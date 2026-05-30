package com.nexa.ai.data.remote.dto

data class VisionRequest(
    val model: String = "gpt-4o",
    val messages: List<VisionMessage>,
    val max_tokens: Int = 1024
)

data class VisionMessage(
    val role: String = "user",
    val content: List<ContentPart>
)

data class ContentPart(
    val type: String, // "text" o "image_url"
    val text: String? = null,
    val image_url: ImageUrlDto? = null
)

data class ImageUrlDto(val url: String)

data class VisionResponse(
    val choices: List<VisionChoice>
)

data class VisionChoice(
    val message: VisionResponseMessage
)

data class VisionResponseMessage(
    val content: String
)
