package com.nexa.ai.data.remote.dto

data class ImageGenRequest(
    val model: String = "dall-e-3",
    val prompt: String,
    val size: String = "1024x1024",
    val n: Int = 1,
    val quality: String = "standard",
    val style: String = "vivid"
)

data class ImageGenResponse(
    val data: List<ImageData>
)

data class ImageData(
    val url: String? = null,
    val b64_json: String? = null,
    val revised_prompt: String? = null
)
