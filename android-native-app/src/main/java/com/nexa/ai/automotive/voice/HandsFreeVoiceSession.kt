package com.nexa.ai.automotive.voice

import android.content.Context
import android.util.Log
import com.nexa.ai.automotive.safety.DrivingMonitor
import com.nexa.ai.automotive.sensors.VehicleState
import com.nexa.ai.offline.LocalLLMManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HandsFreeVoiceSession — Orchestrates continuous driving voice sessions.
 * Intercepts speech inputs, parses command intents, query GGUF locally when needed,
 * and reads responses back to the driver using TTSManager.
 */
class HandsFreeVoiceSession(
    private val context: Context,
    private val localLLMManager: LocalLLMManager
) {
    companion object {
        private const val TAG = "NexaAutoSession"
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // Initialize Speech and Telemetry dependencies
        TTSManager.init(context)
    }

    /**
     * Entrypoint for processing verbal speech inputs detected inside the car.
     */
    fun onVoiceQuery(query: String) {
        Log.i(TAG, "Driver vocal query input: $query")

        // 1. Check driving constraints first
        DrivingMonitor.evaluateSafety()

        // 2. Parse vehicle-specific command intents
        val parsed = VoiceCommandParser.parse(query)
        
        when (parsed.intent) {
            "NAVIGATE" -> {
                val destination = parsed.target
                val response = if (destination.isNotBlank()) {
                    "Navegando a $destination. Mantén los ojos en el camino."
                } else {
                    "No entendí el destino de navegación. Por favor, indícalo de nuevo."
                }
                TTSManager.speak(response)
            }
            "CLIMATE" -> {
                // Trigger simulated in-car climate command
                val response = "Ajustando climatización inteligente del vehículo a veintidós grados."
                TTSManager.speak(response)
            }
            "STATUS" -> {
                val report = vehicleStatusReport()
                TTSManager.speak(report)
            }
            else -> {
                // If it's not a direct dashboard control command, route it to local Llama-3 / Phi-3 GGUF LLM!
                scope.launch {
                    val prompt = "Conductor pregunta: $query. Da una respuesta extremadamente breve (máximo 2 frases) apta para conducción."
                    val llmResponse = localLLMManager.generateText(prompt)
                    
                    // Enforce response constraints (< 3 sentences) for safety
                    val cleanResponse = limitResponseLength(llmResponse)
                    TTSManager.speak(cleanResponse)
                }
            }
        }
    }

    private fun vehicleStatusReport(): String {
        val speed = VehicleState.speed
        val rpm = VehicleState.rpm
        val fuel = VehicleState.fuelLevel
        val temp = VehicleState.engineTemp

        return if (DrivingMonitor.isDriving()) {
            "Velocidad $speed kilómetros por hora. Motor a $rpm revoluciones. Combustible al $fuel por ciento."
        } else {
            "Vehículo detenido de forma segura. Combustible al $fuel por ciento. Temperatura del motor $temp grados celsius."
        }
    }

    private fun limitResponseLength(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        return if (sentences.size > 2) {
            sentences.take(2).joinToString(" ")
        } else {
            text
        }
    }

    /**
     * Releases session TTS/STT resources.
     */
    fun destroy() {
        TTSManager.shutdown()
        Log.i(TAG, "Automotive Hands-Free Session destroyed.")
    }
}
