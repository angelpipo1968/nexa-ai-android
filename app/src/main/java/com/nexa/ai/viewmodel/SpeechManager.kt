package com.nexa.ai.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
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
    private var isCurrentlyListening = false

    // Callbacks
    var onListeningStateChanged: ((Boolean) -> Unit)? = null
    var onSpeakingStateChanged: ((Boolean, String?) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onInputTextChanged: ((String) -> Unit)? = null
    var onRecognitionEnded: (() -> Unit)? = null
    var onBargeInDetected: (() -> Unit)? = null

    // Barge-in: track whether TTS is actively playing
    @Volatile
    var isTtsActive: Boolean = false
        private set

    // Cooldown: don't allow barge-in until TTS has been playing for this long
    // Prevents TTS audio from triggering false barge-in via mic bleed
    private var ttsStartedAt: Long = 0L
    private val bargeInCooldownMs = 1500L // 1.5 seconds

    // Continuous audio session — keeps mic open during entire voice mode
    // to eliminate start/stop clicks and enable seamless barge-in
    private var continuousAudioRecord: AudioRecord? = null
    private var bargeInThread: Thread? = null
    @Volatile private var bargeInActive = false
    private var audioSessionActive = false
    private val audioManager: AudioManager
        get() = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
                            isTtsActive = false
                            onSpeakingStateChanged?.invoke(false, null)
                        }
                        @Deprecated("Deprecated")
                        override fun onError(utteranceId: String?) {
                            isTtsActive = false
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
            // Use simple speak without params to avoid device-specific crashes
            isTtsActive = true
            ttsStartedAt = System.currentTimeMillis()
            val result = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, messageId ?: "msg")
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
        isTtsActive = false
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
        if (isCurrentlyListening) return // Prevents ERROR_CLIENT (Error 5)
        
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(application)) {
                onError?.invoke("voice_unavailable")
                return
            }

            isCurrentlyListening = true
            // Cleanup previous recognizer if any
            speechRecognizer?.destroy()
            
            // Don't stop TTS during barge-in listening
            if (!isTtsActive) {
                stopSpeaking()
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onListeningStateChanged?.invoke(true)
                    }
                    override fun onBeginningOfSpeech() {
                        // Barge-in: user started speaking while AI was talking
                        if (isTtsActive) {
                            onBargeInDetected?.invoke()
                        }
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        // Monitor energy levels to prevent premature cut-offs
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        // DON'T change state here. Wait for results or error to avoid UI flicker.
                    }
                    override fun onError(error: Int) {
                        isCurrentlyListening = false
                        onListeningStateChanged?.invoke(false)
                        
                        // Error 5 (ERROR_CLIENT) recovery: destroy and nullify to force fresh start next time
                        if (error == 5) {
                            try {
                                speechRecognizer?.cancel()
                                speechRecognizer?.destroy()
                            } catch (_: Exception) {}
                            speechRecognizer = null
                        }

                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || 
                            error == SpeechRecognizer.ERROR_NO_MATCH || error == 5) {
                            onRecognitionEnded?.invoke()
                        } else if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            onError?.invoke("voice_error: $error")
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isCurrentlyListening = false
                        onListeningStateChanged?.invoke(false)
                        
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        
                        if (!text.isNullOrBlank()) {
                            onInputTextChanged?.invoke(text)
                            onSpeechResult?.invoke(text)
                        } else {
                            onRecognitionEnded?.invoke()
                        }
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
                
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.packageName)
                
                // Optimized silence detection (as suggested)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                
                // Ensure dictation mode for better long speech handling
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isCurrentlyListening = false
            android.util.Log.e("SpeechManager", "Speech recognition error: ${e.message}", e)
            onListeningStateChanged?.invoke(false)
            onError?.invoke("voice_error")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Stop listening error: ${e.message}", e)
        }
        isCurrentlyListening = false
        onListeningStateChanged?.invoke(false)
    }

    // ═══════════════════════════════════════
    //  CONTINUOUS VOICE AUDIO SESSION
    //  Keeps mic open during entire voice mode
    //  to eliminate clicks and enable instant barge-in
    // ═══════════════════════════════════════

    /**
     * Opens a continuous audio session for the entire voice mode.
     * Sets MODE_IN_COMMUNICATION to prevent audio clicks on transitions.
     * AudioRecord is created once and reused — never released until voice mode ends.
     */
    fun startVoiceAudioSession() {
        if (audioSessionActive) return
        audioSessionActive = true

        try {
            // Set communication mode — eliminates clicks between TTS/recording transitions
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false // Route to earpiece/headset, reduce feedback
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "AudioManager mode error: ${e.message}", e)
        }
    }

    fun stopVoiceAudioSession() {
        audioSessionActive = false
        stopBargeInMonitor()
        releaseContinuousAudioRecord()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "AudioManager restore error: ${e.message}", e)
        }
    }

    /**
     * Starts monitoring mic energy using the persistent AudioRecord.
     * Only monitors — does NOT do speech recognition.
     * When sustained voice energy is detected, triggers onBargeInDetected.
     */
    fun startBargeInMonitor() {
        if (bargeInActive) return
        bargeInActive = true

        bargeInThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            // Create persistent AudioRecord once
            if (continuousAudioRecord == null || continuousAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                releaseContinuousAudioRecord()
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                try {
                    continuousAudioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Hardware echo cancellation
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 2
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SpeechManager", "AudioRecord create error: ${e.message}", e)
                    bargeInActive = false
                    return@Thread
                }
            }

            val recorder = continuousAudioRecord ?: run { bargeInActive = false; return@Thread }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e("SpeechManager", "AudioRecord not initialized")
                bargeInActive = false
                return@Thread
            }

            try {
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.startRecording()
                }

                val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val buffer = ShortArray(bufferSize.coerceAtLeast(1024))
                var highEnergyFrames = 0
                val requiredFrames = 10    // Need sustained voice, not a quick spike
                val thresholdDb = 55.0     // Higher threshold to reject TTS bleed-through
                var framesSinceCooldown = 0

                while (bargeInActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Enforce cooldown: don't allow barge-in until TTS has played long enough
                        val elapsed = System.currentTimeMillis() - ttsStartedAt
                        if (elapsed < bargeInCooldownMs) {
                            // Skip detection during cooldown, but keep monitoring
                            framesSinceCooldown++
                            if (framesSinceCooldown % 10 == 0) {
                                highEnergyFrames = 0 // Reset counter periodically during cooldown
                            }
                            continue
                        }

                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = Math.sqrt(sum / read)
                        val db = if (rms > 1.0) 20.0 * Math.log10(rms) else 0.0

                        if (db > thresholdDb) {
                            highEnergyFrames++
                            if (highEnergyFrames >= requiredFrames) {
                                android.util.Log.d("SpeechManager", "Barge-in! dB=$db frames=$highEnergyFrames elapsed=${elapsed}ms")
                                bargeInActive = false
                                onBargeInDetected?.invoke()
                                break
                            }
                        } else {
                            highEnergyFrames = maxOf(0, highEnergyFrames - 1)
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Barge-in monitor error: ${e.message}", e)
            } finally {
                // Pause recording but KEEP the AudioRecord instance (don't release)
                // This prevents the click sound from mic hardware open/close
                try { recorder.stop() } catch (_: Exception) {}
                bargeInActive = false
            }
        }
        bargeInThread?.start()
    }

    fun stopBargeInMonitor() {
        bargeInActive = false
        bargeInThread?.interrupt()
        bargeInThread = null
        // Note: don't stop recorder here, let the thread's finally block handle it
        // and don't release — keep it for next use
    }

    private fun releaseContinuousAudioRecord() {
        try {
            continuousAudioRecord?.stop()
        } catch (_: Exception) {}
        try {
            continuousAudioRecord?.release()
        } catch (_: Exception) {}
        continuousAudioRecord = null
    }

    fun destroy() {
        stopBargeInMonitor()
        releaseContinuousAudioRecord()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Destroy error: ${e.message}", e)
        }
    }
}
