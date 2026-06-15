package com.nexa.ai.data.repository

import com.nexa.ai.data.remote.NexaMediaApi
import com.nexa.ai.data.remote.NexaImageRequest
import javax.inject.Inject

/**
 * Image Repository — uses Nexa server for FREE image generation.
 * Server uses z-ai-web-dev-sdk (no API keys needed).
 * Falls back to Pollinations.ai on server side if SDK unavailable.
 */
class ImageRepository @Inject constructor(
    private val nexaMediaApi: NexaMediaApi
) {
    suspend fun generateImage(prompt: String, style: String = "realistic"): Result<String> = try {
        val response = nexaMediaApi.generateImage(
            NexaImageRequest(
                prompt = prompt,
                size = "1024x1024",
                style = style
            )
        )
        if (response.success && response.images.isNotEmpty()) {
            val image = response.images.first()
            // Return base64 data URL or direct URL
            val imageUrl = if (image.base64.isNotEmpty()) {
                "data:image/png;base64,${image.base64}"
            } else {
                image.url
            }
            if (imageUrl.isNotEmpty()) {
                Result.success(imageUrl)
            } else {
                Result.failure(Exception("No se recibió imagen del servidor"))
            }
        } else {
            Result.failure(Exception(response.error ?: "Error desconocido generando imagen"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
