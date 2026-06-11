package com.nexa.ai.data.repository

import com.nexa.ai.data.remote.OpenAiApi
import com.nexa.ai.data.remote.dto.ImageGenRequest
import javax.inject.Inject

class ImageRepository @Inject constructor(private val api: OpenAiApi) {
    suspend fun generateImage(prompt: String): Result<String> = try {
        val response = api.generateImage(ImageGenRequest(prompt = prompt))
        val url = response.data.first().url ?: throw Exception("URL de la imagen nula")
        Result.success(url)
    } catch (e: Exception) { Result.failure(e) }
}
