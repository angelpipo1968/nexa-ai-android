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
    val provider: String? = null
)

class NexaRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite timeout for streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val TAG = "NexaRepository"

    fun sendMessage(
        messages: List<ChatMessage>,
        baseUrl: String,
        provider: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val request = ChatRequest(messages, provider)
        val body = gson.toJsonTree(request).asJsonObject

        val request = Request.Builder()
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
                if (data == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }

                try {
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
                trySend(StreamEvent.Done)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE Failure: ${t?.message}", t)
                when {
                    response?.code == 401 -> {
                        trySend(StreamEvent.AuthExpired)
                    }
                    response?.code == 429 -> {
                        trySend(StreamEvent.Error("Límite de mensajes alcanzado. Intenta más tarde."))
                    }
                    t != null -> {
                        trySend(StreamEvent.Error("Error de conexión: ${t.localizedMessage}"))
                    }
                    else -> {
                        trySend(StreamEvent.Error("Error desconocido en el servidor (${response?.code})"))
                    }
                }
                close(t)
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)

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
