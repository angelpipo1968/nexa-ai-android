package com.nexa.ai.data.repository

import com.nexa.ai.data.remote.NexaMediaApi
import com.nexa.ai.data.remote.NexaVideoGenerateRequest
import com.nexa.ai.data.remote.NexaVideoStatusRequest
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Video Repository — uses Nexa server for FREE video generation.
 * Server uses z-ai-web-dev-sdk (no API keys needed).
 * Falls back to Pollinations.ai on server side if SDK unavailable.
 */
class VideoRepository @Inject constructor(
    private val nexaMediaApi: NexaMediaApi
) {
    suspend fun generateVideo(
        prompt: String,
        style: String = "cinematic",
        aspectRatio: String = "16:9",
        duration: Int = 5
    ): Result<String> = try {
        // 1. Start generation
        val initResponse = nexaMediaApi.generateVideo(
            NexaVideoGenerateRequest(
                prompt = prompt,
                duration = duration,
                aspectRatio = aspectRatio,
                style = style
            )
        )

        if (!initResponse.success && initResponse.status != "processing") {
            return Result.failure(Exception(initResponse.error ?: "Error iniciando generación de video"))
        }

        // 2. If completed immediately, return URL
        if (initResponse.status == "completed" && initResponse.videoUrl.isNotEmpty()) {
            return Result.success(initResponse.videoUrl)
        }

        // 3. If base64 returned, return data URL
        if (initResponse.videoBase64.isNotEmpty()) {
            return Result.success("data:video/mp4;base64,${initResponse.videoBase64}")
        }

        // 4. If async task, poll for result
        val taskId = initResponse.taskId
        if (taskId.isNotEmpty()) {
            var attempts = 0
            while (attempts < 60) {  // Max 5 minutes
                delay(5000)
                val statusResponse = nexaMediaApi.checkVideoStatus(
                    NexaVideoStatusRequest(taskId = taskId)
                )

                when {
                    statusResponse.status == "completed" && statusResponse.videoUrl.isNotEmpty() -> {
                        return Result.success(statusResponse.videoUrl)
                    }
                    statusResponse.status == "failed" -> {
                        return Result.failure(Exception(statusResponse.error ?: "La generación del video falló"))
                    }
                    statusResponse.status == "processing" -> {
                        // Continue polling
                    }
                }
                attempts++
            }
            Result.failure(Exception("Timeout: El video tardó demasiado en generarse"))
        } else {
            Result.failure(Exception("No se recibió taskId ni URL del video"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
