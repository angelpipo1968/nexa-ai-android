package com.nexa.ai.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Manages Text-to-Speech and Speech-to-Text functionality.
 * Enhanced with audio focus, Bluetooth SCO, adaptive barge-in with VAD,
 * proximity sensor support, and volume feedback.
 *
 * Changelog v3.7:
 * - Fixed: Bluetooth SCO timing (isStartingSco guard now works correctly)
 * - Fixed: SpeechRecognizer error code 5 is ERROR_CLIENT (removed redundant check)
 * - Fixed: AudioRecord not released on barge-in stop (potential mic leak)
 * - Fixed: Audio focus uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK (less aggressive)
 * - Added: Proximity sensor support (auto earpiece/speaker switching)
 * - Added: Voice Activity Detection (VAD) via zero-crossing rate in barge-in
 * - Added: Bluetooth SCO connection state receiver
 * - Added: TTS stream fallback (STREAM_MUSIC if STREAM_VOICE_CALL fails)
 * - Added: SpeechRecognizer recreation on ERROR_SERVER (was missing)
 * - Added: Pre-calibration phase for barge-in (silence before TTS starts)
 * - Improved: cleanForSpeech preserves more natural punctuation
 * - Improved: Barge-in stops AudioRecord only when voice mode ends (not on each barge-in)
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
    var onVolumeLevelChanged: ((Float) -> Unit)? = null  // 0f..1f real-time volume for visual feedback
    var onProximityChanged: ((Boolean) -> Unit)? = null  // true = near (use earpiece), false = far (use speaker)

    // Barge-in: track whether TTS is actively playing
    @Volatile
    var isTtsActive: Boolean = false
        private set

    // Cooldown: don't allow barge-in until TTS has been playing for this long
    // Prevents TTS audio from triggering false barge-in via mic bleed
    private var ttsStartedAt: Long = 0L
    private val bargeInCooldownMs = 2500L // Reduced from 3s to 2.5s — faster response
    private var lastBargeInAt: Long = 0L  // Prevents rapid re-triggers

    // Adaptive barge-in threshold — calibrates to device noise floor
    private var noiseFloorDb: Double = 45.0
    private var adaptiveThresholdDb: Double = 62.0
    private val thresholdAboveNoise = 15.0 // Must be this much above noise floor
    private var calibrationFrames = 0
    private val calibrationTarget = 30  // Frames to calibrate noise floor

    // VAD (Voice Activity Detection) — zero-crossing rate
    // Helps distinguish actual voice from noise/tones
    private val vadZcrThreshold = 15.0  // Zero-crossings per frame — voice typically 10-40
    private var vadEnabled = true

    // Continuous audio session — keeps mic open during entire voice mode
    // to eliminate start/stop clicks and enable seamless barge-in
    private var continuousAudioRecord: AudioRecord? = null
    private var bargeInThread: Thread? = null
    @Volatile private var bargeInActive = false
    private var audioSessionActive = false
    private val audioManager: AudioManager
        get() = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Audio focus — prevents conflicts with other audio apps
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Bluetooth SCO support for hands-free headsets
    private var isBluetoothScoConnected = false
    @Volatile private var isStartingSco = false
    private var scoConnected = false

    // Bluetooth SCO state receiver — detects when SCO actually connects
    private val scoStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        scoConnected = true
                        isStartingSco = false
                        android.util.Log.d("SpeechManager", "Bluetooth SCO connected")
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        scoConnected = false
                        isStartingSco = false
                        android.util.Log.d("SpeechManager", "Bluetooth SCO disconnected")
                    }
                    2 -> {  // SCO_AUDIO_STATE_CONNECTING (not a public SDK constant)
                        isStartingSco = true
                        android.util.Log.d("SpeechManager", "Bluetooth SCO connecting...")
                    }
                }
            }
        }
    }
    private var scoReceiverRegistered = false

    // Proximity sensor — auto-switch earpiece/speaker when phone is near/far
    private val sensorManager: SensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private var proximitySensor: Sensor? = null
    private var isNearEar = false  // true when phone is near user's ear
    private var proximityEnabled = false

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val wasNear = isNearEar
                isNearEar = distance < maxRange

                if (wasNear != isNearEar && audioSessionActive) {
                    updateAudioRoutingForProximity()
                    onProximityChanged?.invoke(isNearEar)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Current settings
    private var currentLanguage: AppLanguage = AppLanguage.SPANISH
    private var currentVoiceType: VoiceType = VoiceType.FEMALE_1

    // TTS stream fallback tracking
    private var useVoiceCallStream = true

    fun initialize() {
        initTTS()
        detectBluetoothSco()
        registerScoStateReceiver()
        initProximitySensor()
    }

    // ═══════════════════════════════════════
    //  PROXIMITY SENSOR — Auto earpiece/speaker
    // ═══════════════════════════════════════

    private fun initProximitySensor() {
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    fun enableProximitySensor() {
        if (proximityEnabled || proximitySensor == null) return
        proximityEnabled = true
        try {
            sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("SpeechManager", "Proximity sensor enabled")
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Proximity sensor error: ${e.message}", e)
            proximityEnabled = false
        }
    }

    fun disableProximitySensor() {
        if (!proximityEnabled) return
        proximityEnabled = false
        try {
            sensorManager.unregisterListener(proximityListener)
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Proximity sensor unregister error: ${e.message}", e)
        }
    }

    /** Set speaker on/off using modern API (API 31+) or fallback to deprecated API. */
    private fun setSpeakerphoneOn(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    // Find and set speaker as communication device
                    val speakerDevice = audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                } else {
                    // Clear communication device to fall back to earpiece
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "setSpeakerphoneOn error: ${e.message}", e)
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
    }

    private fun updateAudioRoutingForProximity() {
        try {
            if (isBluetoothScoConnected && scoConnected) return  // BT takes priority
            if (isNearEar) {
                // Near ear: use earpiece, turn off speaker
                setSpeakerphoneOn(false)
                android.util.Log.d("SpeechManager", "Proximity: near ear → earpiece")
            } else {
                // Far from ear: use speaker for hands-free
                setSpeakerphoneOn(true)
                android.util.Log.d("SpeechManager", "Proximity: far from ear → speaker")
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Audio routing error: ${e.message}", e)
        }
    }

    // ═══════════════════════════════════════
    //  AUDIO FOCUS — Prevent conflicts with other apps
    // ═══════════════════════════════════════

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            // Use MAY_DUCK instead of GAIN_TRANSIENT — allows other apps to duck
            // rather than pause entirely (e.g. music, navigation)
            val focusType = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(focusType)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                hasAudioFocus = false
                                stopSpeaking()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                hasAudioFocus = false
                                stopSpeaking()
                            }
                        }
                    }
                    .build()
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            hasAudioFocus = false
                            stopSpeaking()
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    focusType
                )
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Audio focus request error: ${e.message}", e)
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Audio focus abandon error: ${e.message}", e)
        }
        hasAudioFocus = false
    }

    // ═══════════════════════════════════════
    //  BLUETOOTH SCO — Support for BT headsets
    // ═══════════════════════════════════════

    private fun registerScoStateReceiver() {
        if (scoReceiverRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            application.registerReceiver(scoStateReceiver, filter)
            scoReceiverRegistered = true
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "SCO receiver register error: ${e.message}", e)
        }
    }

    private fun unregisterScoStateReceiver() {
        if (!scoReceiverRegistered) return
        try {
            application.unregisterReceiver(scoStateReceiver)
            scoReceiverRegistered = false
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "SCO receiver unregister error: ${e.message}", e)
        }
    }

    private fun detectBluetoothSco() {
        try {
            isBluetoothScoConnected = audioManager.isBluetoothScoAvailableOffCall
        } catch (e: Exception) {
            isBluetoothScoConnected = false
        }
    }

    private fun startBluetoothSco() {
        if (!isBluetoothScoConnected || isStartingSco || scoConnected) return
        isStartingSco = true
        try {
            // API 31+: Use setCommunicationDevice with Bluetooth SCO device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = audioManager.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (btDevice != null) {
                    val result = audioManager.setCommunicationDevice(btDevice)
                    if (result) {
                        scoConnected = true
                        isStartingSco = false
                        android.util.Log.d("SpeechManager", "Bluetooth SCO connected via setCommunicationDevice")
                    } else {
                        // Fallback to legacy API if setCommunicationDevice fails
                        @Suppress("DEPRECATION")
                        audioManager.startBluetoothSco()
                        startBluetoothScoLegacyFallback()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    startBluetoothScoLegacyFallback()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                startBluetoothScoLegacyFallback()
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Bluetooth SCO start error: ${e.message}", e)
            isStartingSco = false
        }
    }

    /** Legacy fallback for Bluetooth SCO connection with timeout. */
    private fun startBluetoothScoLegacyFallback() {
        // Wait up to 3 seconds for connection
        Handler(Looper.getMainLooper()).postDelayed({
            if (isStartingSco && !scoConnected) {
                try {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                } catch (e: Exception) {
                    android.util.Log.e("SpeechManager", "Bluetooth SCO fallback error: ${e.message}", e)
                }
                isStartingSco = false
            }
        }, 3000)
    }

    private fun stopBluetoothSco() {
        if (!isBluetoothScoConnected && !scoConnected) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Bluetooth SCO stop error: ${e.message}", e)
            // Try legacy as fallback
            try {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            } catch (_: Exception) {}
        }
        scoConnected = false
        isStartingSco = false
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
                            abandonAudioFocus()
                            onSpeakingStateChanged?.invoke(false, null)
                        }
                        @Deprecated("Deprecated")
                        override fun onError(utteranceId: String?) {
                            isTtsActive = false
                            abandonAudioFocus()
                            onSpeakingStateChanged?.invoke(false, null)
                        }
                    })

                    // Apply voice settings after a small delay to let TTS fully initialize
                    Handler(Looper.getMainLooper()).postDelayed({
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
                tts?.setLanguage(Locale.getDefault())
            }

            val allVoices = try { tts?.voices } catch (e: Exception) { null }
            if (allVoices.isNullOrEmpty()) {
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
            // Request audio focus before speaking
            requestAudioFocus()

            isTtsActive = true
            ttsStartedAt = System.currentTimeMillis()
            val utteranceId = messageId ?: "msg_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                // Route audio through voice call stream for better BT headset support
                // Fallback to STREAM_MUSIC if voice call stream doesn't work on device
                val stream = if (useVoiceCallStream) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream)
            }
            val result = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                // If STREAM_VOICE_CALL failed, try STREAM_MUSIC as fallback
                if (useVoiceCallStream) {
                    useVoiceCallStream = false
                    android.util.Log.w("SpeechManager", "TTS with STREAM_VOICE_CALL failed, retrying with STREAM_MUSIC")
                    val fallbackParams = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    }
                    val retryResult = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, fallbackParams, utteranceId)
                    if (retryResult == TextToSpeech.ERROR) {
                        android.util.Log.e("SpeechManager", "TTS speak returned ERROR on both streams")
                        isTtsActive = false
                        abandonAudioFocus()
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                } else {
                    android.util.Log.e("SpeechManager", "TTS speak returned ERROR")
                    isTtsActive = false
                    abandonAudioFocus()
                    onSpeakingStateChanged?.invoke(false, null)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "TTS speak error: ${e.message}", e)
            isTtsActive = false
            abandonAudioFocus()
            onSpeakingStateChanged?.invoke(false, null)
        }
    }

    fun stopSpeaking() {
        isTtsActive = false
        tts?.stop()
        abandonAudioFocus()
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

        // Preserve natural punctuation: keep ¿¡ and common symbols that aid TTS prosody
        cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}\\s.,;:!?¿¡()\\-—]"), "")
        cleaned = cleaned
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s*([.,;:!?])\\s*"), "$1 ")
            .trim()

        return cleaned
    }

    // ═══════════════════════════════════════
    //  SPEECH RECOGNITION
    // ═══════════════════════════════════════

    private fun buildRecognizerIntent(): Intent {
        val langCode = when (currentLanguage) {
            AppLanguage.SPANISH -> "es-ES"
            AppLanguage.ENGLISH -> "en-US"
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (speechRecognizer != null) return speechRecognizer

        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            onError?.invoke("voice_unavailable")
            return null
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningStateChanged?.invoke(true)
                }
                override fun onBeginningOfSpeech() {
                    if (isTtsActive) {
                        onBargeInDetected?.invoke()
                    }
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // Report volume level for visual feedback
                    val normalized = (rmsdB / 12f).coerceIn(0f, 1f)
                    onVolumeLevelChanged?.invoke(normalized)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    onListeningStateChanged?.invoke(false)

                    // Recreate SpeechRecognizer on these unrecoverable errors
                    val shouldRecreate = when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> true
                        else -> false
                    }

                    if (shouldRecreate) {
                        try {
                            speechRecognizer?.cancel()
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                    }

                    // These errors are "normal" — just retry
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_CLIENT) {
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
        return speechRecognizer
    }

    fun startListening() {
        if (isCurrentlyListening) return

        try {
            val recognizer = getOrCreateRecognizer() ?: return

            isCurrentlyListening = true

            if (!isTtsActive) {
                stopSpeaking()
            }

            recognizer.startListening(buildRecognizerIntent())
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
     * Handles Bluetooth SCO, audio focus, and proximity sensor.
     * AudioRecord is created once and reused — never released until voice mode ends.
     */
    fun startVoiceAudioSession() {
        if (audioSessionActive) return
        audioSessionActive = true

        try {
            // Request audio focus for voice communication
            requestAudioFocus()

            // Set communication mode — eliminates clicks between TTS/recording transitions
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Bluetooth SCO: route audio to BT headset if available
            detectBluetoothSco()
            if (isBluetoothScoConnected) {
                startBluetoothSco()
                setSpeakerphoneOn(false)
            } else {
                // Enable proximity sensor for auto earpiece/speaker switching
                enableProximitySensor()
                // Initial state: use speaker for hands-free
                setSpeakerphoneOn(!isNearEar)
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "AudioManager mode error: ${e.message}", e)
        }
    }

    fun stopVoiceAudioSession() {
        audioSessionActive = false
        stopBargeInMonitor()
        releaseContinuousAudioRecord()
        disableProximitySensor()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            setSpeakerphoneOn(false)
            stopBluetoothSco()
            abandonAudioFocus()
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "AudioManager restore error: ${e.message}", e)
        }

        // Reset adaptive threshold calibration for next session
        noiseFloorDb = 45.0
        adaptiveThresholdDb = 62.0
        calibrationFrames = 0
    }

    /**
     * Starts monitoring mic energy using the persistent AudioRecord.
     * Only monitors — does NOT do speech recognition.
     * When sustained voice energy is detected, triggers onBargeInDetected.
     * Uses adaptive threshold that calibrates to device noise floor.
     * Enhanced with Voice Activity Detection (VAD) via zero-crossing rate.
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
                var vadVoiceFrames = 0
                val requiredFrames = 8     // Reduced from 12 — faster barge-in response
                val requiredVadFrames = 3   // Must have this many VAD-positive frames to confirm voice
                var framesSinceCooldown = 0
                // Reset calibration for fresh start
                calibrationFrames = 0

                while (bargeInActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Compute RMS volume
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = Math.sqrt(sum / read)
                        val db = if (rms > 1.0) 20.0 * Math.log10(rms) else 0.0

                        // Compute Zero-Crossing Rate (VAD)
                        val zcr = computeZCR(buffer, read)

                        // Report volume level for visual feedback (normalized 0..1)
                        val normalizedVolume = (db / 90.0).coerceIn(0.0, 1.0)
                        onVolumeLevelChanged?.invoke(normalizedVolume.toFloat())

                        // Calibrate noise floor during first frames
                        if (calibrationFrames < calibrationTarget) {
                            noiseFloorDb = noiseFloorDb * 0.8 + db * 0.2  // Exponential moving average
                            adaptiveThresholdDb = noiseFloorDb + thresholdAboveNoise
                            calibrationFrames++
                            continue
                        }

                        // Re-arm delay: don't allow barge-in immediately after a previous one
                        val sinceLastBargeIn = System.currentTimeMillis() - lastBargeInAt
                        if (sinceLastBargeIn < 1500L) {
                            highEnergyFrames = 0
                            vadVoiceFrames = 0
                            continue
                        }

                        // Enforce cooldown: don't allow barge-in until TTS has played long enough
                        val elapsed = System.currentTimeMillis() - ttsStartedAt
                        if (elapsed < bargeInCooldownMs) {
                            // Update noise floor during cooldown too
                            if (db < adaptiveThresholdDb) {
                                noiseFloorDb = noiseFloorDb * 0.95 + db * 0.05
                                adaptiveThresholdDb = noiseFloorDb + thresholdAboveNoise
                            }
                            if (framesSinceCooldown++ > 20) {
                                highEnergyFrames = 0
                                vadVoiceFrames = 0
                                framesSinceCooldown = 0
                            }
                            continue
                        }

                        // VAD check: is this voice or just noise?
                        val isVoiceLike = !vadEnabled || (zcr > vadZcrThreshold && zcr < 80.0)

                        if (db > adaptiveThresholdDb) {
                            if (isVoiceLike) {
                                highEnergyFrames++
                                vadVoiceFrames++
                            } else {
                                // High energy but not voice-like (probably TTS bleed or tones)
                                highEnergyFrames = maxOf(0, highEnergyFrames - 1)
                                vadVoiceFrames = maxOf(0, vadVoiceFrames - 1)
                            }

                            // Require both energy AND VAD confirmation
                            if (highEnergyFrames >= requiredFrames && vadVoiceFrames >= requiredVadFrames) {
                                android.util.Log.d("SpeechManager", "Barge-in! dB=$db threshold=$adaptiveThresholdDb noise=$noiseFloorDb zcr=$zcr frames=$highEnergyFrames vad=$vadVoiceFrames elapsed=${elapsed}ms")
                                lastBargeInAt = System.currentTimeMillis()
                                bargeInActive = false
                                onBargeInDetected?.invoke()
                                break
                            }
                        } else {
                            highEnergyFrames = maxOf(0, highEnergyFrames - 1)
                            vadVoiceFrames = maxOf(0, vadVoiceFrames - 1)
                            // Slowly adapt noise floor during silence
                            noiseFloorDb = noiseFloorDb * 0.98 + db * 0.02
                            adaptiveThresholdDb = noiseFloorDb + thresholdAboveNoise
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Barge-in monitor error: ${e.message}", e)
            } finally {
                // Don't release AudioRecord here — it's reused across barge-in sessions
                // Only stop recording; release happens in stopVoiceAudioSession()
                try { recorder.stop() } catch (_: Exception) {}
                bargeInActive = false
            }
        }
        bargeInThread?.start()
    }

    /**
     * Compute Zero-Crossing Rate (ZCR) — a simple VAD metric.
     * Voice typically has ZCR between 10-40 crossings per frame at 16kHz.
     * Pure tones (TTS bleed) have very low ZCR.
     * Random noise has very high ZCR.
     */
    private fun computeZCR(buffer: ShortArray, length: Int): Double {
        var crossings = 0
        for (i in 1 until length) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble()
    }

    fun stopBargeInMonitor() {
        bargeInActive = false
        bargeInThread?.interrupt()
        bargeInThread = null
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
        disableProximitySensor()
        unregisterScoStateReceiver()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            setSpeakerphoneOn(false)
            stopBluetoothSco()
            abandonAudioFocus()
        } catch (_: Exception) {}
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Destroy error: ${e.message}", e)
        }
    }
}
