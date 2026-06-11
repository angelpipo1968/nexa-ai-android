package com.nexa.ai.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val provider: String? = null,
    val language: String? = null,
    val systemPrompt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null
)

@Singleton
class NexaRepository @Inject constructor() {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE needs no read timeout
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "NexaRepository"
    }

    fun sendMessage(
        messages: List<ChatMessage>,
        baseUrl: String,
        provider: String? = null,
        language: String? = null,
        systemPrompt: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        city: String? = null,
        country: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val chatRequest = ChatRequest(messages, provider, language, systemPrompt, latitude, longitude, city, country)
        val body = gson.toJsonTree(chatRequest).asJsonObject

        // Remove systemPrompt from body and inject as first message instead
        if (body.has("systemPrompt")) body.remove("systemPrompt")
        if (!systemPrompt.isNullOrBlank()) {
            val msgArray = body.getAsJsonArray("messages")
            val systemMsg = com.google.gson.JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            }
            msgArray?.let { it ->
                val newArray = com.google.gson.JsonArray()
                newArray.add(systemMsg)
                it.forEach { elem -> newArray.add(elem) }
                body.add("messages", newArray)
            }
        }

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/chat")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE Connection Opened")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (data == "[DONE]") {
                        trySend(StreamEvent.Done)
                        return
                    }

                    val obj = gson.fromJson(data, JsonObject::class.java)
                    
                    if (obj.has("done") && obj.get("done").asBoolean) {
                        trySend(StreamEvent.Done)
                        return
                    }

                    if (obj.has("text")) {
                        val text = obj.get("text").asString
                        if (text.isNotEmpty()) trySend(StreamEvent.Text(text))
                    }

                    if (obj.has("provider")) {
                        trySend(StreamEvent.Provider(obj.get("provider").asString))
                    }

                    if (obj.has("error")) {
                        trySend(StreamEvent.Error(obj.get("error").asString))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE data: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE Connection Closed")
                try {
                    trySend(StreamEvent.Done)
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing SSE flow", e)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE Failure: ${t?.message}", t)
                
                val errorEvent = when {
                    t is SocketTimeoutException -> StreamEvent.Error("timeout")
                    t is IOException -> StreamEvent.Error("network_error")
                    response?.code == 401 -> StreamEvent.AuthExpired
                    response?.code == 429 -> StreamEvent.Error("rate_limit")
                    (response?.code ?: 0) >= 500 -> StreamEvent.Error("server_error")
                    else -> StreamEvent.Error(t?.localizedMessage ?: "unknown_error")
                }

                try {
                    trySend(errorEvent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending failure event", e)
                }
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendVisionRequest(
        baseUrl: String,
        base64Image: String,
        mimeType: String = "image/jpeg",
        question: String? = null
    ): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("image", base64Image)
                addProperty("mimeType", mimeType)
                if (question != null) addProperty("question", question)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/vision")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Vision API error: ${response.code} - ${response.body?.string()}")
                    return@withContext null
                }
                val responseBody = response.body?.string() ?: return@withContext null
                val obj = gson.fromJson(responseBody, JsonObject::class.java)
                
                // Try multiple possible response fields
                return@withContext when {
                    obj.has("text") -> obj.get("text").asString
                    obj.has("response") -> obj.get("response").asString
                    obj.has("description") -> obj.get("description").asString
                    obj.has("content") -> obj.get("content").asString
                    obj.has("error") -> {
                        val err = obj.get("error")
                        if (err.isJsonObject) {
                            err.asJsonObject.get("message")?.asString ?: err.toString()
                        } else {
                            err.asString
                        }
                    }
                    else -> {
                        // If it's a valid string but not in a known field, return raw if it's not JSON
                        if (!responseBody.trim().startsWith("{")) responseBody else null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision request error", e)
            null
        }
    }

    /**
     * Send a vision request to LiteLLM using the OpenAI-compatible chat completions format.
     * This sends an image (as base64 data URI) along with a text prompt to a VLM model
     * (e.g., llava:7b) through LiteLLM's proxy on port 4000.
     *
     * Flow: Android Camera → Base64 → LiteLLM :4000/v1/chat/completions → VLM → Response
     *
     * @param baseUrl LiteLLM base URL, e.g. "http://192.168.1.50:4000"
     * @param base64Image Base64-encoded image data (raw, without data URI prefix)
     * @param mimeType Image MIME type, e.g. "image/jpeg"
     * @param question Text prompt/question about the image
     * @param model Model name configured in LiteLLM, e.g. "vision"
     * @return The assistant's text response, or null on error
     */
    suspend fun sendLiteLLMVisionRequest(
        baseUrl: String,
        base64Image: String,
        mimeType: String = "image/jpeg",
        question: String = "Describe esta imagen en detalle",
        model: String = "vision"
    ): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val dataUri = "data:$mimeType;base64,$base64Image"

            // Build OpenAI-compatible vision request
            val body = JsonObject().apply {
                addProperty("model", model)
                addProperty("max_tokens", 1024)

                val messagesArray = com.google.gson.JsonArray()
                val userMessage = JsonObject().apply {
                    addProperty("role", "user")
                    val contentArray = com.google.gson.JsonArray()

                    // Text part
                    val textPart = JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", question)
                    }
                    contentArray.add(textPart)

                    // Image part
                    val imagePart = JsonObject().apply {
                        addProperty("type", "image_url")
                        val imageUrlObj = JsonObject().apply {
                            addProperty("url", dataUri)
                        }
                        add("image_url", imageUrlObj)
                    }
                    contentArray.add(imagePart)

                    add("content", contentArray)
                }
                messagesArray.add(userMessage)
                add("messages", messagesArray)
            }

            Log.d(TAG, "Sending LiteLLM vision request to $baseUrl/v1/chat/completions model=$model")

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newBuilder()
                .readTimeout(120, TimeUnit.SECONDS) // VLM inference can be slow
                .build()
                .newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "LiteLLM Vision API error: ${response.code} - $errorBody")
                        return@withContext null
                    }

                    val responseBody = response.body?.string() ?: return@withContext null
                    val obj = gson.fromJson(responseBody, JsonObject::class.java)

                    // Parse OpenAI chat completions response format
                    return@withContext when {
                        obj.has("choices") && obj.getAsJsonArray("choices").size() > 0 -> {
                            val choice = obj.getAsJsonArray("choices")[0].asJsonObject
                            if (choice.has("message")) {
                                choice.getAsJsonObject("message").get("content")?.asString
                            } else if (choice.has("text")) {
                                choice.get("text")?.asString
                            } else null
                        }
                        obj.has("error") -> {
                            val err = obj.get("error")
                            Log.e(TAG, "LiteLLM Vision error: $err")
                            if (err.isJsonObject) err.asJsonObject.get("message")?.asString else err.asString
                        }
                        else -> {
                            Log.e(TAG, "Unexpected LiteLLM vision response: $responseBody")
                            null
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "LiteLLM Vision request error", e)
            null
        }
    }

    /**
     * Send a STREAMING vision request to LiteLLM using SSE.
     * This sends an image (as base64 data URI) along with a text prompt to a VLM model
     * (e.g., llava:7b, qwen2.5-vl) through LiteLLM's proxy on port 4000.
     *
     * The response arrives as Server-Sent Events with OpenAI streaming format:
     *   data: {"choices":[{"delta":{"content":"La "}}]}
     *   data: {"choices":[{"delta":{"content":"imagen "}}]}
     *   data: [DONE]
     *
     * Flow: Android Camera → Base64 → LiteLLM :4000/v1/chat/completions (stream=true) → SSE → VLM
     *
     * @param baseUrl LiteLLM base URL, e.g. "http://192.168.1.50:4000"
     * @param base64Image Base64-encoded image data (raw, without data URI prefix)
     * @param mimeType Image MIME type, e.g. "image/jpeg"
     * @param question Text prompt/question about the image
     * @param model Model name configured in LiteLLM, e.g. "vision", "qwen-vision"
     * @return Flow of StreamEvent with streamed text chunks, provider info, errors, and Done
     */
    fun sendLiteLLMVisionStream(
        baseUrl: String,
        base64Image: String,
        mimeType: String = "image/jpeg",
        question: String = "Describe esta imagen en detalle",
        model: String = "vision"
    ): Flow<StreamEvent> = callbackFlow {
        val dataUri = "data:$mimeType;base64,$base64Image"

        // Build OpenAI-compatible vision request with stream=true
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 1024)
            addProperty("stream", true)  // Enable SSE streaming

            val messagesArray = com.google.gson.JsonArray()
            val userMessage = JsonObject().apply {
                addProperty("role", "user")
                val contentArray = com.google.gson.JsonArray()

                // Text part
                val textPart = JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", question)
                }
                contentArray.add(textPart)

                // Image part
                val imagePart = JsonObject().apply {
                    addProperty("type", "image_url")
                    val imageUrlObj = JsonObject().apply {
                        addProperty("url", dataUri)
                    }
                    add("image_url", imageUrlObj)
                }
                contentArray.add(imagePart)

                add("content", contentArray)
            }
            messagesArray.add(userMessage)
            add("messages", messagesArray)
        }

        Log.d(TAG, "Sending STREAMING LiteLLM vision request to $baseUrl/v1/chat/completions model=$model")

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Vision SSE Connection Opened")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (data == "[DONE]") {
                        trySend(StreamEvent.Done)
                        return
                    }

                    val obj = gson.fromJson(data, JsonObject::class.java)

                    // OpenAI streaming format: {"choices":[{"delta":{"content":"text"}}]}
                    if (obj.has("choices")) {
                        val choices = obj.getAsJsonArray("choices")
                        if (choices.size() > 0) {
                            val choice = choices[0].asJsonObject
                            if (choice.has("delta")) {
                                val delta = choice.getAsJsonObject("delta")
                                if (delta.has("content")) {
                                    val content = delta.get("content").asString
                                    if (content.isNotEmpty()) {
                                        trySend(StreamEvent.Text(content))
                                    }
                                }
                            }
                            // Check finish_reason
                            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull) {
                                val reason = choice.get("finish_reason").asString
                                if (reason == "stop") {
                                    trySend(StreamEvent.Done)
                                }
                            }
                        }
                    }

                    // Error handling in stream
                    if (obj.has("error")) {
                        val err = obj.get("error")
                        val errMsg = if (err.isJsonObject) {
                            err.asJsonObject.get("message")?.asString ?: err.toString()
                        } else {
                            err.asString
                        }
                        trySend(StreamEvent.Error(errMsg))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing vision SSE data: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "Vision SSE Connection Closed")
                try {
                    trySend(StreamEvent.Done)
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing vision SSE flow", e)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Vision SSE Failure: ${t?.message}", t)

                val errorEvent = when {
                    t is SocketTimeoutException -> StreamEvent.Error("timeout")
                    t is IOException -> StreamEvent.Error("network_error")
                    response?.code == 401 -> StreamEvent.AuthExpired
                    response?.code == 429 -> StreamEvent.Error("rate_limit")
                    (response?.code ?: 0) >= 500 -> StreamEvent.Error("server_error")
                    else -> StreamEvent.Error(t?.localizedMessage ?: "unknown_error")
                }

                try {
                    trySend(errorEvent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending vision failure event", e)
                }
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
    data object AuthExpired : StreamEvent()
}
