package com.nexa.ai.domain.usecase

import com.nexa.ai.viewmodel.SpeechManager
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.VoiceType
import javax.inject.Inject

class VoiceUseCase @Inject constructor(
    private val speechManager: SpeechManager
) {
    fun initialize() = speechManager.initialize()
    fun destroy() = speechManager.destroy()
    fun startListening() = speechManager.startListening()
    fun stopListening() = speechManager.stopListening()
    fun speak(text: String, messageId: String?, speakingMessageId: String?) = speechManager.speak(text, messageId, speakingMessageId)
    fun stopSpeaking() = speechManager.stopSpeaking()
    fun setLanguage(lang: AppLanguage) = speechManager.setLanguage(lang)
    fun setVoiceType(voice: VoiceType) = speechManager.setVoiceType(voice)
    
    // Para simplificar la refactorización inicial, proveemos acceso directo al manager
    // para los callbacks, o en el futuro convertiremos los callbacks a Kotlin Flows.
    val manager: SpeechManager get() = speechManager
}
