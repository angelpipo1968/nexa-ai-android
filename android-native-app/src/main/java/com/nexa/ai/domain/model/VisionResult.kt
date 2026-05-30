package com.nexa.ai.domain.model

data class VisionResult(
    val content: String,
    val modelUsed: String,
    val promptTokens: Int,
    val completionTokens: Int
)
