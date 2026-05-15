package com.nexa.ai.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    var onRecognitionEnded: (() -> Unit)? = null

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
        try {
            tts = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setSpeechRate(1.0f)

                    // Set default language first, then apply voice settings
                    try {
                        tts?.setLanguage(Locale.getDefault())
                    } catch (e: Exception) {
                        tts?.setLanguage(Locale.US)
                    }

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

                    // Apply voice settings after a small delay to let TTS fully initialize
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        applyVoiceSettings()
                    }, 500)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "TTS init error: ${e.message}", e)
        }
    }

    fun applyVoiceSettings() {
        if (!ttsReady || tts == null) return
        try {
            val locale = when (currentLanguage) {
                AppLanguage.SPANISH -> Locale("es", "ES")
                AppLanguage.ENGLISH -> Locale.US
            }

            val localeResult = tts?.setLanguage(locale)
            if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to device default
                tts?.setLanguage(Locale.getDefault())
            }

            // Voice selection - skip if no voices available (some devices don't expose voices)
            val allVoices = try { tts?.voices } catch (e: Exception) { null }
            if (allVoices.isNullOrEmpty()) {
                // Just set pitch, skip voice selection
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
                return
            }

            val localeVoices = allVoices.filter { it.locale.language == locale.language }
            if (localeVoices.isEmpty()) return

            val voiceName = getVoiceName(currentLanguage, currentVoiceType)

            val isMale = currentVoiceType == VoiceType.MALE_1 ||
                    currentVoiceType == VoiceType.MALE_2 ||
                    currentVoiceType == VoiceType.MALE_3

            val selectedVoice = localeVoices.find { it.name == voiceName }
                ?: run {
                    val genderKeywords = if (isMale) listOf("male", "man", "hom") else listOf("female", "woman", "fem")
                    localeVoices.find { v -> genderKeywords.any { v.name.lowercase().contains(it) } }
                }
                ?: localeVoices.firstOrNull()
                ?: return

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
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Voice settings error: ${e.message}", e)
        }
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
        if (!ttsReady || tts == null) return

        if (messageId != null && currentSpeakingId == messageId) {
            stopSpeaking()
            return
        }

        stopSpeaking()
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return

        try {
            val utteranceId = messageId ?: "msg_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("SpeechManager", "TTS speak returned ERROR")
                onSpeakingStateChanged?.invoke(false, null)
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "TTS speak error: ${e.message}", e)
            onSpeakingStateChanged?.invoke(false, null)
        }
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
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(application)) {
                onError?.invoke("voice_unavailable")
                return
            }

            stopSpeaking()

            // Destroy previous recognizer to avoid conflicts
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null

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
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                onRecognitionEnded?.invoke()
                            }
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
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Speech recognition error: ${e.message}", e)
            onListeningStateChanged?.invoke(false)
            onError?.invoke("voice_error")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Stop listening error: ${e.message}", e)
        }
        onListeningStateChanged?.invoke(false)
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Destroy error: ${e.message}", e)
        }
    }
}
