package com.nexa.ai.data.remote.dto

data class VideoPredictionRequest(val version: String, val input: VideoInput)
data class VideoInput(val prompt: String)
data class VideoPredictionResponse(val id: String, val status: String, val output: String? = null, val error: String? = null)
