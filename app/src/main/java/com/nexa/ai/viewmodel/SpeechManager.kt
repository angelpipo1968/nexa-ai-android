package com.nexa.ai.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Manages Text-to-Speech and Speech-to-Text functionality.
 */
class SpeechManager(private val application: Application) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Callbacks
    var onListeningStateChanged: ((Boolean) -> Unit)? = null
    var onSpeakingStateChanged: ((Boolean, String?) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onInputTextChanged: ((String) -> Unit)? = null

    // Current settings
    private var currentLanguage: AppLanguage = AppLanguage.SPANISH
    private var currentVoiceType: VoiceType = VoiceType.FEMALE_1

    fun initialize() {
        initTTS()
    }

    // ═══════════════════════════════════════
    //  TTS — Text to Speech
    // ═══════════════════════════════════════

    private fun initTTS() {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyVoiceSettings()
                tts?.setSpeechRate(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeakingStateChanged?.invoke(true, utteranceId)
                    }
                    override fun onDone(utteranceId: String?) {
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                })
            }
        }
    }

    fun applyVoiceSettings() {
        if (!ttsReady) return
        val locale = when (currentLanguage) {
            AppLanguage.SPANISH -> Locale("es", "ES")
            AppLanguage.ENGLISH -> Locale.US
        }
        tts?.language = locale

        val allVoices = tts?.voices?.filter { it.locale.language == locale.language } ?: return
        if (allVoices.isEmpty()) return

        val voiceName = getVoiceName(currentLanguage, currentVoiceType)

        val isMale = currentVoiceType == VoiceType.MALE_1 ||
                currentVoiceType == VoiceType.MALE_2 ||
                currentVoiceType == VoiceType.MALE_3

        val selectedVoice = allVoices.find { it.name == voiceName }
            ?: run {
                val genderKeywords = if (isMale) listOf("male", "man", "hom") else listOf("female", "woman", "fem")
                allVoices.find { v -> genderKeywords.any { v.name.lowercase().contains(it) } }
            }
            ?: allVoices.first()

        tts?.voice = selectedVoice

        val pitch = when (currentVoiceType) {
            VoiceType.FEMALE_1 -> 1.1f
            VoiceType.FEMALE_2 -> 1.0f
            VoiceType.FEMALE_3 -> 0.9f
            VoiceType.MALE_1   -> 0.8f
            VoiceType.MALE_2   -> 1.0f
            VoiceType.MALE_3   -> 1.2f
        }
        tts?.setPitch(pitch)
        tts?.setSpeechRate(1.0f)
    }

    private fun getVoiceName(lang: AppLanguage, type: VoiceType): String {
        return when (lang) {
            AppLanguage.SPANISH -> when (type) {
                VoiceType.FEMALE_1 -> "es-es-x-eea-local"
                VoiceType.FEMALE_2 -> "es-es-x-eec-local"
                VoiceType.FEMALE_3 -> "es-us-x-esc-local"
                VoiceType.MALE_1   -> "es-es-x-eed-local"
                VoiceType.MALE_2   -> "es-es-x-eee-local"
                VoiceType.MALE_3   -> "es-us-x-esd-local"
            }
            AppLanguage.ENGLISH -> when (type) {
                VoiceType.FEMALE_1 -> "en-us-x-tpf-local"
                VoiceType.FEMALE_2 -> "en-us-x-tpd-local"
                VoiceType.FEMALE_3 -> "en-gb-x-gba-local"
                VoiceType.MALE_1   -> "en-us-x-tpc-local"
                VoiceType.MALE_2   -> "en-us-x-tpa-local"
                VoiceType.MALE_3   -> "en-gb-x-gbb-local"
            }
        }
    }

    fun speak(text: String, messageId: String? = null, currentSpeakingId: String?) {
        if (!ttsReady) return

        if (messageId != null && currentSpeakingId == messageId) {
            stopSpeaking()
            return
        }

        stopSpeaking()
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, messageId ?: "msg")
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingStateChanged?.invoke(false, null)
    }

    fun setLanguage(lang: AppLanguage) {
        currentLanguage = lang
        applyVoiceSettings()
    }

    fun setVoiceType(type: VoiceType) {
        currentVoiceType = type
        applyVoiceSettings()
    }

    private fun cleanForSpeech(text: String): String {
        var cleaned = text
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*{1,3}(.+?)\\*{1,3}"), "$1")
            .replace(Regex("_{1,3}(.+?)_{1,3}"), "$1")
            .replace(Regex("\\*+"), "")
            .replace(Regex("```[\\s\\S]*?```"), "código")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("!\\[[^]]*]\\([^)]+\\)"), "")
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), ". ")

        cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}\\s.,;:!?¿¡]"), "")
        cleaned = cleaned
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s*([.,;:!?])\\s*"), "$1 ")
            .trim()

        return cleaned
    }

    // ═══════════════════════════════════════
    //  SPEECH RECOGNITION
    // ═══════════════════════════════════════

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            onError?.invoke("voice_unavailable")
            return
        }

        stopSpeaking()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningStateChanged?.invoke(true)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onListeningStateChanged?.invoke(false)
                }
                override fun onError(error: Int) {
                    onListeningStateChanged?.invoke(false)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> {}
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {}
                        else -> onError?.invoke("voice_error: $error")
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    onInputTextChanged?.invoke(text)
                    onSpeechResult?.invoke(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    onSpeechPartial?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val langCode = when (currentLanguage) {
            AppLanguage.SPANISH -> "es-ES"
            AppLanguage.ENGLISH -> "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onListeningStateChanged?.invoke(false)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
