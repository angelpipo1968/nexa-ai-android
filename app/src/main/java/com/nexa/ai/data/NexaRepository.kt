package com.nexa.ai.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

class NexaRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun sendMessage(
        messages: List<ChatMessage>,
        baseUrl: String
    ): Flow<StreamEvent> = flow {
        val body = JsonObject().apply {
            add("messages", gson.toJsonTree(messages.map {
                mapOf("role" to it.role, "content" to it.content)
            }))
        }

        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(StreamEvent.Error("Error ${response.code}: ${response.message}"))
                return@flow
            }

            val source = response.body?.source() ?: throw Exception("Response body is null")
            
            while (!source.exhausted()) {
                val line = source.readUtf8Line()?.trim() ?: break
                if (line.isEmpty() || !line.startsWith("data: ")) continue

                val jsonStr = line.removePrefix("data: ")
                if (jsonStr == "[DONE]") {
                    emit(StreamEvent.Done)
                    break
                }

                try {
                    val obj = gson.fromJson(jsonStr, JsonObject::class.java)
                    
                    // Prioritize 'done' state
                    if (obj.has("done") && obj.get("done").asBoolean) {
                        emit(StreamEvent.Done)
                        break
                    }

                    // Handle text chunk
                    if (obj.has("text")) {
                        val text = obj.get("text").asString
                        if (text.isNotEmpty()) {
                            emit(StreamEvent.Text(text))
                        }
                    }

                    // Handle provider info
                    if (obj.has("provider")) {
                        emit(StreamEvent.Provider(obj.get("provider").asString))
                    }

                    // Handle potential errors
                    if (obj.has("error")) {
                        emit(StreamEvent.Error(obj.get("error").asString))
                        break
                    }
                } catch (e: Exception) {
                    // Skip malformed JSON in the stream
                }
            }
            response.close()
        } catch (e: Exception) {
            emit(StreamEvent.Error("Connection error: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
}
