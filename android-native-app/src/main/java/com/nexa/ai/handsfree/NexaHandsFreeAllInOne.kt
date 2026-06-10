@file:Suppress("DEPRECATION")

package com.nexa.ai.handsfree

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * 🎙️ NEXA HANDS-FREE ALL-IN-ONE
 * Voz + Memoria + Emociones + Bilingüe en un solo archivo.
 * Inyecta con Hilt y listo.
 *
 * @deprecated This class is superseded by the VoiceUseCase + SpeechManager + VoiceEnhancer
 *             architecture. It is retained only for backward compatibility. New code should
 *             use VoiceUseCase for voice sessions, SpeechManager for TTS/STT,
 *             VoiceEnhancer for wake word/emotion/language detection, and
 *             NaturalConversationEngine for conversation context tracking.
 *             This class will be removed in a future release.
 */
@Deprecated(
    message = "Superseded by VoiceUseCase + SpeechManager + VoiceEnhancer architecture. " +
            "Use VoiceUseCase for voice sessions, SpeechManager for TTS/STT.",
    replaceWith = ReplaceWith(
        "VoiceUseCase(voiceEnhancer, conversationEngine, speechManager)",
        "com.nexa.ai.viewmodel.usecase.VoiceUseCase"
    )
)
// FIX: Removed @Singleton and @Inject to prevent Hilt from creating a duplicate TTS engine.
// This class is deprecated — use SpeechManager + VoiceUseCase instead.
// v5.4 FIX: Do NOT initialize TTS or STT at all — these create duplicate engines
// that compete with SpeechManager for the microphone and audio output.
// All voice functionality is now handled by SpeechManager + VoiceUseCase.
class NexaHandsFreeAllInOne(
    private val context: Context
) : TextToSpeech.OnInitListener {

    override fun onInit(status: Int) {
        // No-op: TTS is now managed by SpeechManager
    }

    // Estado reactivo para UI
    private val _state = MutableStateFlow(HandsFreeState())
    val state: StateFlow<HandsFreeState> = _state.asStateFlow()

    // Componentes
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentLang = "es"

    // Memoria episódica autocontenida (en memoria RAM)
    private val memory = mutableListOf<MemoryEntry>()
    private val maxMemory = 30

    // Callbacks para conectar con tu ViewModel
    var onUserSaid: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTranscriptionChanged: ((String) -> Unit)? = null

    // Inicialización segura
    @androidx.annotation.CallSuper
    fun initialize() {
        // v5.4 FIX: Do NOT init TTS or STT here!
        // These create duplicate engines that compete with SpeechManager.
        // initTTS()  // REMOVED: causes duplicate TTS engine
        // initSTT()  // REMOVED: causes duplicate SpeechRecognizer competing for mic
        android.util.Log.w("NexaHandsFree", "initialize() called — but TTS/STT init SKIPPED to prevent conflicts with SpeechManager")
    }

    /**
     * v5.2: Pause this component when SpeechManager is controlling voice mode.
     * Prevents both TTS engines from speaking at the same time and
     * both SpeechRecognizers from competing for the microphone.
     */
    fun pause() {
        try {
            speechRecognizer?.stopListening()
            tts?.stop()
            isPaused = true
            android.util.Log.d("NexaHandsFree", "Paused — SpeechManager has control")
        } catch (e: Exception) {
            android.util.Log.w("NexaHandsFree", "Pause error: ${e.message}")
        }
    }

    /**
     * v5.2: Resume this component when SpeechManager is no longer active.
     */
    fun resume() {
        isPaused = false
        android.util.Log.d("NexaHandsFree", "Resumed — available again")
    }

    // v6.0: Track paused state to avoid conflicts
    private var isPaused = false

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag(currentLang)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = updateState(isSpeaking = true)
                    override fun onDone(utteranceId: String?) = updateState(isSpeaking = false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        updateState(isSpeaking = false)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        updateState(isSpeaking = false)
                        onError?.invoke("TTS error: $errorCode")
                    }
                })
            } else onError?.invoke("TTS no soportado")
        }
    }

    private fun initSTT() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = updateState(isListening = true)
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() = updateState(isListening = false)
                override fun onError(error: Int) {
                    updateState(isListening = false)
                    onError?.invoke(when(error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No entendí"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Micrófono ocupado"
                        SpeechRecognizer.ERROR_AUDIO -> "Revisa permisos de grabación"
                        else -> "STT error $error"
                    })
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim() ?: return
                    processVoiceInput(text)
                }
                override fun onPartialResults(partial: Bundle?) {
                    partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { onTranscriptionChanged?.invoke(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (_state.value.isListening) return
        if (isPaused) {
            android.util.Log.w("NexaHandsFree", "Cannot start listening — paused by SpeechManager")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        updateState(isListening = false)
    }

    fun speak(text: String) {
        if (isPaused) {
            android.util.Log.w("NexaHandsFree", "Cannot speak — paused by SpeechManager")
            return
        }
        if (tts == null || text.isBlank()) return
        val id = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }, id)
        addToMemory("NEXA", text)
    }

    fun setLanguage(lang: String) {
        if (lang in listOf("es", "en")) {
            currentLang = lang
            tts?.language = Locale.forLanguageTag(lang)
        }
    }

    private fun processVoiceInput(raw: String) {
        val text = raw.lowercase().replace("ok nexa", "").trim()
        if (text.isBlank()) return
        onTranscriptionChanged?.invoke(text)
        val emotion = detectEmotion(text)
        addToMemory("USER", text, emotion)
        val ctx = getContextPrompt()
        onUserSaid?.invoke("[EMOTION:${emotion}] [CTX:${ctx.take(60)}] $text")
    }

    private fun addToMemory(role: String, text: String, emotion: String = "neutral") {
        memory.add(MemoryEntry(System.currentTimeMillis(), role, text, emotion))
        if (memory.size > maxMemory) memory.removeAt(0)
    }

    private fun getContextPrompt(): String =
        memory.takeLast(4).joinToString("\n") { "${it.role}: ${it.text}" }

    private fun detectEmotion(text: String): String {
        val lower = text.lowercase()
        return when {
            Regex("(feliz|contento|genial|increíble|jaja|😄|amor)").containsMatchIn(lower) -> "joy"
            Regex("(triste|malo|mal|decepción|😢|llorar)").containsMatchIn(lower) -> "sadness"
            Regex("(enojado|odio|maldito|😡|estúpido|ira)").containsMatchIn(lower) -> "anger"
            Regex("(miedo|preocup|ayuda|urgente|😨|pánico)").containsMatchIn(lower) -> "fear"
            else -> "neutral"
        }
    }

    private fun updateState(isListening: Boolean? = null, isSpeaking: Boolean? = null) {
        _state.value = _state.value.copy(
            isListening = isListening ?: _state.value.isListening,
            isSpeaking = isSpeaking ?: _state.value.isSpeaking
        )
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        scope.cancel()
    }

    // DATA CLASSES
    data class HandsFreeState(val isListening: Boolean = false, val isSpeaking: Boolean = false)
    data class MemoryEntry(val ts: Long, val role: String, val text: String, val emotion: String)
}
