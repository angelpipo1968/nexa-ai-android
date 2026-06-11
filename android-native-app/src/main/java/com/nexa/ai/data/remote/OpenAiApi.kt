package com.nexa.ai.data.remote

import com.nexa.ai.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface OpenAiApi {

    // Vision - Describir imágenes
    @POST("v1/chat/completions")
    suspend fun analyzeImage(
        @Body request: VisionRequest
    ): VisionResponse

    // Generación de imágenes con DALL-E 3
    @POST("v1/images/generations")
    suspend fun generateImage(
        @Body request: ImageGenRequest
    ): ImageGenResponse

    // Generación de imágenes editando
    @Multipart
    @POST("v1/images/edits")
    suspend fun editImage(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
        @Part("model") model: RequestBody,
        @Part("n") n: RequestBody,
        @Part("size") size: RequestBody
    ): ImageGenResponse
}
