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
                        } else err.asString
                    }
                    else -> {
                        Log.w(TAG, "Unknown Vision response format, returning raw body: $responseBody")
                        if (responseBody.trim().startsWith("{")) null else responseBody
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision request error", e)
            null
        }
    }
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
    data object AuthExpired : StreamEvent()
}
