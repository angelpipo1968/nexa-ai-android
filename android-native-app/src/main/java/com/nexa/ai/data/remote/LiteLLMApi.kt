package com.nexa.ai.data.remote

import com.nexa.ai.data.remote.dto.VisionRequest
import com.nexa.ai.data.remote.dto.VisionResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for local LiteLLM proxy server.
 *
 * LiteLLM provides an OpenAI-compatible API that proxies requests to local models
 * (e.g., llava:7b for vision, qwen2.5-7b for text) running on Ollama/vLLM.
 *
 * Base URL should point to the LiteLLM server, e.g. http://192.168.1.50:4000/
 *
 * IMPORTANT: Android must always connect to port 4000 (LiteLLM router), NOT directly to:
 * - Port 8002 (vLLM internal - no fallback/router/balance)
 * - Port 3000 (Next.js web UI)
 */
interface LiteLLMApi {

    /**
     * Vision analysis via chat completions with multimodal content.
     * Uses a VLM model (e.g., llava:7b) configured as "vision" in LiteLLM.
     *
     * Request format follows OpenAI Vision API:
     * {
     *   "model": "vision",
     *   "messages": [{
     *     "role": "user",
     *     "content": [
     *       {"type": "text", "text": "Describe esta imagen"},
     *       {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
     *     ]
     *   }],
     *   "max_tokens": 1024
     * }
     */
    @POST("v1/chat/completions")
    suspend fun analyzeImage(
        @Body request: VisionRequest
    ): VisionResponse

    /**
     * Text-only chat completions via local LiteLLM.
     * Uses a text model (e.g., qwen2.5-7b) configured as "qwen" in LiteLLM.
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: VisionRequest  // Reuse VisionRequest as it's OpenAI-compatible
    ): VisionResponse
}
