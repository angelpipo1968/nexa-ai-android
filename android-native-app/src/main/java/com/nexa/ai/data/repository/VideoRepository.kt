package com.nexa.ai.data.repository

import com.nexa.ai.data.remote.ReplicateApi
import com.nexa.ai.data.remote.dto.VideoInput
import com.nexa.ai.data.remote.dto.VideoPredictionRequest
import com.nexa.ai.data.remote.dto.VideoPredictionResponse
import kotlinx.coroutines.delay
import javax.inject.Inject

class VideoRepository @Inject constructor(private val api: ReplicateApi) {
    
    // ID del modelo Stable Video Diffusion en Replicate (puede cambiar, verifica replicate.com)
    private val modelVersion = "db6e627dc1d6401a0ea9a4f1c6b255b0e45f5f8e6f2f5b1e1c2a3b4c5d6e7f8g"

    suspend fun generateVideo(prompt: String): Result<String> = try {
        // 1. Iniciar la generación
        val initResponse = api.createPrediction(VideoPredictionRequest(version = modelVersion, input = VideoInput(prompt = prompt)))
        
        // 2. Hacer Polling hasta que termine o falle
        var result = initResponse
        var attempts = 0
        while (result.status == "starting" || result.status == "processing") {
            delay(5000) // Esperar 5 segundos entre cada chequeo
            result = api.getPrediction(result.id)
            attempts++
            if (attempts > 60) throw Exception("Timeout: El video tardó demasiado en generarse")
        }

        if (result.status == "succeeded") Result.success(result.output ?: "Sin URL")
        else Result.failure(Exception(result.error ?: "Error desconocido generando video"))
    } catch (e: Exception) { Result.failure(e) }
}
