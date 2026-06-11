package com.nexa.ai.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nexa.ai.BuildConfig
import com.nexa.ai.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VisionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VisionRepository"
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun describeImageStream(
        uri: Uri,
        userPrompt: String = "¿Qué hay en esta imagen? Describe detalladamente.",
        baseUrl: String = BuildConfig.API_BASE_URL
    ): Flow<String> = callbackFlow {

        val base64 = ImageUtils.uriToBase64(context, uri)
        if (base64.isEmpty()) {
            close(Exception("No se pudo leer la imagen"))
            return@callbackFlow
        }

        val apiUrl = baseUrl.trimEnd('/') + "/chat/completions"
        val dataUri = "data:image/jpeg;base64,$base64"

        // Construir JSON OpenAI Vision compatible
        val contentArray = com.google.gson.JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", userPrompt)
            })
            add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", dataUri)
                })
            })
        }

        val messagesArray = com.google.gson.JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                add("content", contentArray)
            })
        }

        val body = JsonObject().apply {
            addProperty("model", "vision")
            addProperty("stream", true)
            addProperty("max_tokens", 1024)
            add("messages", messagesArray)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer ${BuildConfig.LITELLM_API_KEY}")
            .header("User-Agent", "NexaAI-Android/5.2")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val factory = EventSources.createFactory(client)
        val eventSource = factory.newEventSource(request, object : EventSourceListener() {

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                try {
                    val obj = gson.fromJson(data, JsonObject::class.java)
                    val choices = obj.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                        if (delta != null && delta.has("content")) {
                            val chunk = delta.get("content").asString
                            if (chunk.isNotEmpty()) trySend(chunk)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SSE chunk: $data", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: okhttp3.Response?
            ) {
                val msg = t?.message ?: response?.message ?: "Stream failed"
                Log.e(TAG, "SSE failure: $msg")
                close(t ?: Exception(msg))
            }
        })

        // Cancelar el stream si la corrutina se cancela (rotación, navegación, etc.)
        awaitClose {
            eventSource.cancel()
        }
    }
}
