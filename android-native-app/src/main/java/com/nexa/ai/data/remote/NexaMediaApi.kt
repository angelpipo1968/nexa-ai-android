package com.nexa.ai.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Nexa Media API — connects to the Nexa server for image and video generation.
 * Uses z-ai-web-dev-sdk on the server (FREE, no API keys needed).
 *
 * Base URL should point to the Nexa server, e.g. https://nexa-ai.dev/
 */
interface NexaMediaApi {

    // ═══════════════════════════════════════
    //  IMAGE GENERATION
    // ═══════════════════════════════════════

    @POST("api/image")
    suspend fun generateImage(@Body request: NexaImageRequest): NexaImageResponse

    // ═══════════════════════════════════════
    //  VIDEO GENERATION
    // ═══════════════════════════════════════

    @POST("api/video")
    suspend fun generateVideo(@Body request: NexaVideoGenerateRequest): NexaVideoResponse

    @POST("api/video")
    suspend fun checkVideoStatus(@Body request: NexaVideoStatusRequest): NexaVideoResponse
}

// ═══════════════════════════════════════
//  IMAGE DTOs
// ═══════════════════════════════════════

data class NexaImageRequest(
    val prompt: String,
    val size: String = "1024x1024",
    val style: String = "realistic",
    val n: Int = 1
)

data class NexaImageResponse(
    val success: Boolean = false,
    val provider: String = "",
    val prompt: String = "",
    val images: List<NexaGeneratedImage> = emptyList(),
    val count: Int = 0,
    val error: String? = null
)

data class NexaGeneratedImage(
    val base64: String = "",
    val url: String = ""
)

// ═══════════════════════════════════════
//  VIDEO DTOs
// ═══════════════════════════════════════

data class NexaVideoGenerateRequest(
    val action: String = "generate",
    val prompt: String,
    val duration: Int = 5,
    val aspectRatio: String = "16:9",
    val style: String = "cinematic"
)

data class NexaVideoStatusRequest(
    val action: String = "status",
    val taskId: String
)

data class NexaVideoResponse(
    val success: Boolean = false,
    val provider: String = "",
    val prompt: String = "",
    val videoUrl: String = "",
    @SerializedName("videoBase64")
    val videoBase64: String = "",
    val taskId: String = "",
    val status: String = "",
    val progress: Int = 0,
    val estimatedTimeSeconds: Int = 0,
    val message: String = "",
    val error: String? = null
)
