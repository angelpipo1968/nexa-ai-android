package com.nexa.ai.automotive.ui

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.nexa.ai.automotive.voice.HandsFreeVoiceSession
import com.nexa.ai.offline.LocalLLMManager

/**
 * NexaCarMessageScreen — Premium, 100% Google Play Store compliant screen
 * utilizing approved MessageTemplate to minimize driver distraction.
 */
class NexaCarMessageScreen(
    carContext: CarContext
) : Screen(carContext) {

    private var voiceSession: HandsFreeVoiceSession? = null

    init {
        // Instantiate the local hands-free session Coordinator
        val localLLM = LocalLLMManager(carContext.applicationContext)
        voiceSession = HandsFreeVoiceSession(carContext.applicationContext, localLLM)
    }

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
            "Nexa AI activa en el vehículo. Por favor, interactúa usando comandos de voz para mantener los ojos en el camino."
        )
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Activar voz")
                    .setFlags(Action.FLAG_VOICE_COMMAND)
                    .setOnClickListener {
                        // Start localized hands-free vocal flow simulation when driver taps the button
                        voiceSession?.onVoiceQuery("Nexa, cuál es el estado del coche?")
                    }
                    .build()
            )
            .build()
    }
}
