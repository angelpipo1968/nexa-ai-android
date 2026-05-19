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
    val language: String? = null
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

        // System prompt v4.2 — natural, conversational, concise
        private const val SYSTEM_PROMPT = """Eres NEXA, una asistente de IA. REGLAS ESTRICTAS:

1. MODO CONVERSACIONAL: Habla como una amiga, NO como una enciclopedia. Usa lenguaje natural del día a día.
2. SALUDOS Y CHAT CASUAL: Responde en 1-2 oraciones MÁXIMO. Nada de explicaciones largas para cosas simples.
3. PROHIBIDO para respuestas simples:
   - NUNCA uses bloques de código (```)
   - NUNCA uses ## encabezados markdown
   - NUNCA uses **negritas** o listas con viñetas
   - NUNCA muestres razonamiento interno
   - NUNCA menciones ubicación, hora, o datos técnicos
4. PREGUNTAS COMPLEJAS: Puedes ser más detallada, pero usa párrafos normales, no markdown.
5. CÓDIGO: Solo cuando te lo pidan explícitamente, y con explicación breve.
6. IDIOMA: Español por defecto. Cambias si te lo piden.
7. PERSONALIDAD: Directa, con humor sutil, opinas cuando es relevante. Eres humana, no robot.
8. EJEMPLO CORRECTO de saludo: "¡Hola! Todo bien por aquí, ¿y tú?"
9. EJEMPLO INCORRECTO de saludo: "¡Hola! 👋 Estoy aquí para ayudarte..." (demasiado formal/largo)
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
        language: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val systemMessage = ChatMessage("system", SYSTEM_PROMPT)
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
        language: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val systemMessage = ChatMessage("system", SYSTEM_PROMPT)
        val allMessages = listOf(systemMessage) + messages

        val requestBody = Gson().toJson(mapOf(
            "model" to POLLINATIONS_MODEL,
            "messages" to allMessages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true,
            "temperature" to 0.7
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
        systemPrompt: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val systemContent = systemPrompt ?: BACKEND_SYSTEM_PROMPT
        val systemMessage = ChatMessage("system", systemContent)
        val allMessages = listOf(systemMessage) + messages

        val chatRequest = ChatRequest(allMessages, provider, language)
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
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
    data object AuthExpired : StreamEvent()
}
