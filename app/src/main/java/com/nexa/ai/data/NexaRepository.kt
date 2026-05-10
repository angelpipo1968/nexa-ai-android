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
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            emit(StreamEvent.Error("Error ${response.code}: ${response.message}"))
            return@flow
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (!l.startsWith("data: ")) continue

            val jsonStr = l.removePrefix("data: ")
            if (jsonStr == "[DONE]") {
                emit(StreamEvent.Done)
                continue
            }

            try {
                val obj = gson.fromJson(jsonStr, JsonObject::class.java)
                when {
                    obj.has("error") -> emit(StreamEvent.Error(obj.get("error").asString))
                    obj.has("text") -> emit(StreamEvent.Text(obj.get("text").asString))
                    obj.has("provider") -> emit(StreamEvent.Provider(obj.get("provider").asString))
                    obj.has("done") -> emit(StreamEvent.Done)
                }
            } catch (_: Exception) {}
        }

        reader.close()
    }.flowOn(Dispatchers.IO)
}

sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Provider(val name: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
}
