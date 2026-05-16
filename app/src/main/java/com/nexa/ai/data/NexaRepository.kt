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
        .connectTimeout(60, TimeUnit.SECONDS) // Aumentamos el tiempo de espera
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true) // Reintentar automáticamente si falla la red
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "NexaRepository"
    }

    fun sendMessage(
        messages: List<ChatMessage>,
        baseUrl: String,
        provider: String? = null,
        language: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val chatRequest = ChatRequest(messages, provider, language)
        val body = gson.toJsonTree(chatRequest).asJsonObject

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
                // Si es un error de red común, no matamos el chat, intentamos terminarlo con gracia
                try {
                    when {
                        response?.code == 401 -> trySend(StreamEvent.AuthExpired)
                        response?.code == 429 -> trySend(StreamEvent.Error("rate_limit"))
                        else -> {
                            // En lugar de dar error fatal, mandamos "Done" para que lo que haya llegado se lea
                            trySend(StreamEvent.Done)
                        }
                    }
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
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
    data object AuthExpired : StreamEvent()
}
