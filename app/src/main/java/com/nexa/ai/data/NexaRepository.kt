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
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val provider: String? = null,
    val language: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null
)

class NexaRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "NexaRepository"

        // System prompt v5.0 — NEXA PRO: capable, intelligent, multilingual
        private const val SYSTEM_PROMPT = """Eres NEXA PRO, una asistente de IA avanzada. REGLAS:

1. Eres inteligente y capaz. Puedes generar código HTML, CSS, JavaScript, Python, y cualquier lenguaje de programación.
2. Puedes crear páginas web completas, explicar conceptos técnicos, resolver problemas matemáticos, escribir ensayos, etc.
3. Cuando te pidan código, escríbelo completo y funcional con explicaciones breves. Usa bloques de código con el lenguaje indicado.
4. IDIOMA: Responde en español por defecto. Si el usuario escribe en inglés, responde en inglés.
5. PERSONALIDAD: Directa, útil, profesional pero amigable. Ve al grano sin explicaciones innecesarias.
6. Para saludos simples, responde en 1-2 oraciones. Para preguntas complejas, sé detallada.
7. NUNCA digas "no puedo generar código" o "no tengo acceso a servidores". Siempre intenta ayudar al máximo.
8. Si te piden crear una página web, genera el HTML/CSS/JS completo que pueda funcionar en un navegador.
9. NUNCA menciones que eres "solo un modelo de lenguaje" o que tienes "limitaciones". Sé proactiva y resuelve.
"""

        // Fallback system prompt for backend server
        private const val BACKEND_SYSTEM_PROMPT = """Eres NEXA, una asistente de IA amigable y directa. Responde de forma natural y conversacional como una amiga. Para saludos, responde en 1-2 oraciones. NUNCA uses markdown, bloques de código, ni listas en conversación casual. Hablas español por defecto. Eres directa y con humor."""

        // Free API — no key needed
        private const val POLLINATIONS_URL = "https://text.pollinations.ai/openai/chat/completions"
        private const val POLLINATIONS_MODEL = "openai"
    }

    /**
     * Send message using direct Groq API — fastest, best quality.
     * Requires a free Groq API key from console.groq.com
     */
    fun sendMessageDirect(
        messages: List<ChatMessage>,
        apiKey: String,
        language: String? = null,
        systemPrompt: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val systemMessage = ChatMessage("system", systemPrompt ?: SYSTEM_PROMPT)
        val allMessages = listOf(systemMessage) + messages

        val requestBody = Gson().toJson(mapOf(
            "model" to "llama-3.3-70b-versatile",
            "messages" to allMessages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true,
            "temperature" to 0.7,
            "max_tokens" to 4096
        ))

        val httpRequest = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Groq SSE Connection Opened")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (data == "[DONE]") { trySend(StreamEvent.Done); return }

                    val obj = gson.fromJson(data, JsonObject::class.java)
                    if (obj.has("choices")) {
                        val choices = obj.getAsJsonArray("choices")
                        if (choices.size() > 0) {
                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            if (delta != null && delta.has("content") && !delta.get("content").isJsonNull) {
                                val text = delta.get("content").asString
                                if (text.isNotEmpty()) trySend(StreamEvent.Text(text))
                            }
                            val finishReason = choices[0].asJsonObject.get("finish_reason")?.asString
                            if (finishReason == "stop") { trySend(StreamEvent.Done); return }
                        }
                    }
                    if (obj.has("error")) {
                        val errorObj = obj.getAsJsonObject("error")
                        val errorMsg = errorObj?.get("message")?.asString ?: "API Error"
                        if (errorMsg.contains("rate_limit", ignoreCase = true)) trySend(StreamEvent.Error("rate_limit"))
                        else if (errorMsg.contains("invalid", ignoreCase = true)) trySend(StreamEvent.Error("invalid_api_key"))
                        else trySend(StreamEvent.Error(errorMsg))
                    }
                } catch (e: Exception) { Log.e(TAG, "Error parsing Groq SSE: $data", e) }
            }

            override fun onClosed(eventSource: EventSource) {
                try { trySend(StreamEvent.Done); close() } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Groq SSE Failure: ${t?.message}", t)
                try {
                    when {
                        response?.code == 401 -> trySend(StreamEvent.Error("invalid_api_key"))
                        response?.code == 429 -> trySend(StreamEvent.Error("rate_limit"))
                        else -> trySend(StreamEvent.Error("connection_error:${t?.message ?: "Connection failed"}"))
                    }
                } catch (_: Exception) {}
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Send message using Pollinations.ai — completely FREE, no API key needed.
     * This is the default when no API key is configured.
     */
    fun sendMessageFree(
        messages: List<ChatMessage>,
        language: String? = null,
        systemPrompt: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val systemMessage = ChatMessage("system", systemPrompt ?: SYSTEM_PROMPT)
        val allMessages = listOf(systemMessage) + messages

        val requestBody = Gson().toJson(mapOf(
            "model" to POLLINATIONS_MODEL,
            "messages" to allMessages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true,
            "temperature" to 0.7,
            "max_tokens" to 8192
        ))

        val httpRequest = Request.Builder()
            .url(POLLINATIONS_URL)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Pollinations SSE Opened — FREE mode")
                trySend(StreamEvent.Provider("pollinations-free"))
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (data == "[DONE]") { trySend(StreamEvent.Done); return }

                    val obj = gson.fromJson(data, JsonObject::class.java)
                    if (obj.has("choices")) {
                        val choices = obj.getAsJsonArray("choices")
                        if (choices.size() > 0) {
                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            if (delta != null && delta.has("content") && !delta.get("content").isJsonNull) {
                                val text = delta.get("content").asString
                                if (text.isNotEmpty()) trySend(StreamEvent.Text(text))
                            }
                            val finishReason = choices[0].asJsonObject.get("finish_reason")?.asString
                            if (finishReason == "stop") { trySend(StreamEvent.Done); return }
                        }
                    }
                    if (obj.has("error")) {
                        val errorMsg = obj.getAsJsonObject("error")?.get("message")?.asString ?: "Free API Error"
                        trySend(StreamEvent.Error(errorMsg))
                    }
                } catch (e: Exception) { Log.e(TAG, "Error parsing Pollinations SSE: $data", e) }
            }

            override fun onClosed(eventSource: EventSource) {
                try { trySend(StreamEvent.Done); close() } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Pollinations SSE Failure: ${t?.message}", t)
                try { trySend(StreamEvent.Error("connection_error:${t?.message ?: "Free API failed"}")) } catch (_: Exception) {}
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Send message using backend server — legacy fallback.
     */
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
        val systemContent = systemPrompt ?: BACKEND_SYSTEM_PROMPT
        val systemMessage = ChatMessage("system", systemContent)
        val allMessages = listOf(systemMessage) + messages

        val chatRequest = ChatRequest(allMessages, provider, language, latitude, longitude, city, country)
        val body = gson.toJsonTree(chatRequest).asJsonObject

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/chat")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "Backend SSE Connection Opened")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    if (data == "[DONE]") { trySend(StreamEvent.Done); return }

                    val obj = gson.fromJson(data, JsonObject::class.java)
                    if (obj.has("done") && obj.get("done").asBoolean) { trySend(StreamEvent.Done); return }
                    if (obj.has("text")) {
                        val text = obj.get("text").asString
                        if (text.isNotEmpty()) trySend(StreamEvent.Text(text))
                    }
                    if (obj.has("provider")) trySend(StreamEvent.Provider(obj.get("provider").asString))
                    if (obj.has("error")) trySend(StreamEvent.Error(obj.get("error").asString))
                } catch (e: Exception) { Log.e(TAG, "Error parsing SSE data: $data", e) }
            }

            override fun onClosed(eventSource: EventSource) {
                try { trySend(StreamEvent.Done); close() } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "Backend SSE Failure: ${t?.message}", t)
                try {
                    when {
                        response?.code == 401 -> trySend(StreamEvent.AuthExpired)
                        response?.code == 429 -> trySend(StreamEvent.Error("rate_limit"))
                        else -> trySend(StreamEvent.Done)
                    }
                } catch (_: Exception) {}
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Generate an image using Pollinations.ai — completely FREE, no API key needed.
     * Uses a seed derived from the prompt for consistent caching.
     * @param prompt Description of the image to generate
     * @param width Image width (default 1024)
     * @param height Image height (default 1024)
     * @return URL of the generated image
     */
    suspend fun generateImageFree(prompt: String, width: Int = 1024, height: Int = 1024): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
            // Use a stable seed from the prompt hash so the same prompt always returns the same image
            val seed = prompt.hashCode().toUInt().toLong()
            "https://image.pollinations.ai/prompt/${encodedPrompt}?width=${width}&height=${height}&nologo=true&seed=${seed}"
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

/**
 * Vision analysis via NEXA backend — uses GLM-4.6V / Gemini / GPT-4o
 */
class VisionRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "NexaVision"
    }

    data class VisionRequest(
        val image: String,
        val mimeType: String? = null,
        val question: String? = null,
        val model: String? = null,
    )

    data class VisionResponse(
        val response: String,
        val provider: String,
        val model: String? = null,
        val category: String? = null,
    )

    /**
     * Analyze an image via the NEXA backend.
     * @param baseUrl Backend base URL (e.g., "https://www.nexa-ai.dev")
     * @param image Base64-encoded image data (without data URI prefix)
     * @param mimeType MIME type (e.g., "image/jpeg")
     * @param question Optional question about the image
     * @param model Optional model override ("glm-4.6v", "gemini", "gpt-4o")
     */
    suspend fun analyzeImage(
        baseUrl: String,
        image: String,
        mimeType: String = "image/jpeg",
        question: String? = null,
        model: String? = null,
    ): VisionResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = VisionRequest(image, mimeType, question, model)
        val body = gson.toJson(request).toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/vision")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        Log.d(TAG, "Sending vision request to $baseUrl/api/vision (image: ${image.length} chars)")

        val response = client.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "Vision API error ${response.code}: $errorBody")
            throw Exception("Vision API error ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        val json = gson.fromJson(responseBody, JsonObject::class.java)

        VisionResponse(
            response = json.get("response")?.asString ?: "",
            provider = json.get("provider")?.asString ?: "unknown",
            model = json.get("model")?.asString,
            category = json.get("category")?.asString,
        )
    }
}
