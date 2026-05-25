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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Microphone status for UI reactivity.
 */
sealed interface MicState {
    object Available : MicState
    object Denied : MicState
    data class Error(val cause: Throwable) : MicState
    object Initializing : MicState
}

/**
 * Manages Text-to-Speech and Speech-to-Text functionality.
 * Enhanced with audio focus, Bluetooth SCO, adaptive barge-in with VAD,
 * proximity sensor support, and volume feedback.
 *
 * Updated v5.2 — VOICE MODE OPTIMIZATION:
 * - Removed "Hands-free" specific branding and logic.
 * - Optimized audio routing for general voice interaction.
 * - Restored missing methods for stable barge-in and audio session management.
 */
class SpeechManager(private val application: Application) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isCurrentlyListening = false

    @Volatile
    private var isPreparingToSpeak = false

    @Volatile
    private var isPausedByFocusLoss = false

    private val speechStateLock = Any()

    private val _micState = MutableStateFlow<MicState>(MicState.Initializing)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private var lastSpokenText: String? = null
    private var lastSpokenMessageId: String? = null
    private var lastSpokenVoiceTag: String? = null

    // Callbacks
    var onListeningStateChanged: ((Boolean) -> Unit)? = null
    var onSpeakingStateChanged: ((Boolean, String?) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onInputTextChanged: ((String) -> Unit)? = null
    var onRecognitionEnded: (() -> Unit)? = null
    var onBargeInDetected: (() -> Unit)? = null
    var onVolumeLevelChanged: ((Float) -> Unit)? = null
    var onProximityChanged: ((Boolean) -> Unit)? = null

    @Volatile
    var isTtsActive: Boolean = false
        private set

    private var ttsStartedAt: Long = 0L
    private val bargeInCooldownMs = 3500L
    private var lastBargeInAt: Long = 0L

    private var noiseFloorDb: Double = 45.0
    private var adaptiveThresholdDb: Double = 62.0
    private val thresholdAboveNoise = 15.0
    private var calibrationFrames = 0
    private val calibrationTarget = 30

    private val vadZcrThreshold = 18.0
    private var vadEnabled = true

    private var continuousAudioRecord: AudioRecord? = null
    private var bargeInThread: Thread? = null
    @Volatile private var bargeInActive = false
    private var audioSessionActive = false
    private val audioManager: AudioManager
        get() = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private var isBluetoothScoConnected = false
    @Volatile private var isStartingSco = false
    private var scoConnected = false

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
                    2 -> {
                        isStartingSco = true
                        android.util.Log.d("SpeechManager", "Bluetooth SCO connecting...")
                    }
                }
            }
        }
    }
    private var scoReceiverRegistered = false

    private val sensorManager: SensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private var proximitySensor: Sensor? = null
    private var isNearEar = false
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

    private var currentLanguage: AppLanguage = AppLanguage.SPANISH
    private var currentVoiceType: VoiceType = VoiceType.FEMALE_1

    private var useVoiceCallStream = false
    private var volumeBoostEnabled = true
    private var speechRate = 1.0f

    private var savedMusicVolume = -1
    private var savedVoiceCallVolume = -1

    fun initialize() {
        initTTS()
        detectBluetoothSco()
        registerScoStateReceiver()
        initProximitySensor()
    }

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

    private fun setSpeakerphoneOn(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
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
            if (isBluetoothScoConnected && scoConnected) return
            if (isNearEar) {
                setSpeakerphoneOn(false)
                android.util.Log.d("SpeechManager", "Proximity: near ear → earpiece")
            } else {
                setSpeakerphoneOn(true)
                android.util.Log.d("SpeechManager", "Proximity: far from ear → speaker")
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Audio routing error: ${e.message}", e)
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val attrs = if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            }

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
                                if (isTtsActive && audioSessionActive) {
                                    isPausedByFocusLoss = true
                                    tts?.stop()
                                    isTtsActive = false
                                    android.util.Log.d("SpeechManager", "TTS paused by transient focus loss")
                                } else {
                                    hasAudioFocus = false
                                    stopSpeaking()
                                }
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (isPausedByFocusLoss && audioSessionActive) {
                                    isPausedByFocusLoss = false
                                    hasAudioFocus = true
                                    reapplyVoiceRouting()
                                    
                                    lastSpokenText?.let { text ->
                                        android.util.Log.d("SpeechManager", "TTS resuming after transient focus loss")
                                        speak(text, lastSpokenMessageId, lastSpokenVoiceTag)
                                    } ?: run {
                                        onSpeakingStateChanged?.invoke(false, null)
                                    }
                                } else {
                                    hasAudioFocus = true
                                }
                            }
                        }
                    }
                    .build()
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val streamType = if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                    AudioManager.STREAM_MUSIC
                } else {
                    AudioManager.STREAM_VOICE_CALL
                }
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                hasAudioFocus = false
                                stopSpeaking()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                if (isTtsActive && audioSessionActive) {
                                    isPausedByFocusLoss = true
                                    tts?.stop()
                                    isTtsActive = false
                                } else {
                                    hasAudioFocus = false
                                    stopSpeaking()
                                }
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (isPausedByFocusLoss && audioSessionActive) {
                                    isPausedByFocusLoss = false
                                    hasAudioFocus = true
                                    reapplyVoiceRouting()
                                    
                                    lastSpokenText?.let { text ->
                                        speak(text, lastSpokenMessageId, lastSpokenVoiceTag)
                                    } ?: run {
                                        onSpeakingStateChanged?.invoke(false, null)
                                    }
                                } else {
                                    hasAudioFocus = true
                                }
                            }
                        }
                    },
                    streamType,
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

    fun refreshBluetoothState() {
        detectBluetoothSco()
        if (isBluetoothScoConnected && audioSessionActive && !scoConnected && !isStartingSco) {
            android.util.Log.d("SpeechManager", "Starting SCO routing")
            startBluetoothSco()
            setSpeakerphoneOn(false)
        }
    }

    private fun startBluetoothSco() {
        if (!isBluetoothScoConnected || isStartingSco || scoConnected) return
        isStartingSco = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = audioManager.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (btDevice != null) {
                    val result = audioManager.setCommunicationDevice(btDevice)
                    if (result) {
                        scoConnected = true
                        isStartingSco = false
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

    private fun startBluetoothScoLegacyFallback() {
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
        }
        scoConnected = false
        isStartingSco = false
    }

    private fun initTTS() {
        try {
            tts = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setSpeechRate(speechRate)

                    try {
                        tts?.setLanguage(Locale.getDefault())
                    } catch (e: Exception) {
                        tts?.setLanguage(Locale.US)
                    }

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isPreparingToSpeak = false
                            onSpeakingStateChanged?.invoke(true, utteranceId)
                            if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (audioSessionActive && !isNearEar) {
                                        reapplyVoiceRouting()
                                    }
                                }, 150)
                            }
                        }
                        override fun onDone(utteranceId: String?) {
                            synchronized(speechStateLock) {
                                isTtsActive = false
                                if (!audioSessionActive) {
                                    abandonAudioFocus()
                                }
                                if (!isPreparingToSpeak) {
                                    onSpeakingStateChanged?.invoke(false, null)
                                }
                            }
                        }
                        @Deprecated("Deprecated")
                        override fun onError(utteranceId: String?) {
                            synchronized(speechStateLock) {
                                isTtsActive = false
                                if (!audioSessionActive) {
                                    abandonAudioFocus()
                                }
                                if (!isPreparingToSpeak) {
                                    onSpeakingStateChanged?.invoke(false, null)
                                }
                            }
                        }
                    })

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
                tts?.setSpeechRate(speechRate)
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
            tts?.setSpeechRate(speechRate)
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

        lastSpokenText = text
        lastSpokenMessageId = messageId
        lastSpokenVoiceTag = currentSpeakingId

        isPreparingToSpeak = true
        stopSpeaking()
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) {
            isPreparingToSpeak = false
            return
        }

        try {
            if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                reapplyVoiceRouting()
            }

            requestAudioFocus()

            if (volumeBoostEnabled && audioSessionActive) {
                boostVolumeForVoiceMode()
            }

            isTtsActive = true
            if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                setSpeakerphoneOn(true)
            }
            ttsStartedAt = System.currentTimeMillis()
            val utteranceId = messageId ?: "msg_${System.currentTimeMillis()}"

            val useStream = if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                AudioManager.STREAM_MUSIC
            } else if (useVoiceCallStream) {
                AudioManager.STREAM_VOICE_CALL
            } else {
                AudioManager.STREAM_MUSIC
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, useStream)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val result = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                if (useVoiceCallStream) {
                    useVoiceCallStream = false
                    val fallbackParams = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    }
                    val retryResult = tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, fallbackParams, utteranceId)
                    if (retryResult == TextToSpeech.ERROR) {
                        isTtsActive = false
                        if (!audioSessionActive) abandonAudioFocus()
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                } else {
                    isTtsActive = false
                    if (!audioSessionActive) abandonAudioFocus()
                    onSpeakingStateChanged?.invoke(false, null)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "TTS speak error: ${e.message}", e)
            isTtsActive = false
            if (!audioSessionActive) abandonAudioFocus()
            onSpeakingStateChanged?.invoke(false, null)
        }
    }

    fun stopSpeaking() {
        synchronized(speechStateLock) {
            isTtsActive = false
            tts?.stop()
            if (!audioSessionActive) {
                abandonAudioFocus()
            }
            if (!isPreparingToSpeak) {
                onSpeakingStateChanged?.invoke(false, null)
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        currentLanguage = lang
        applyVoiceSettings()
    }

    fun setVoiceType(type: VoiceType) {
        currentVoiceType = type
        applyVoiceSettings()
    }

    fun setVolumeBoost(enabled: Boolean) {
        volumeBoostEnabled = enabled
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    fun getVolumeBoost(): Boolean = volumeBoostEnabled

    fun getSpeechRate(): Float = speechRate

    private fun boostVolumeForVoiceMode() {
        try {
            if (!isNearEar && !isBluetoothScoConnected && audioSessionActive) {
                try {
                    audioManager.mode = AudioManager.MODE_NORMAL
                } catch (_: Exception) {}
            }

            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)

            val maxVoiceCall = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceCall, 0)

            try {
                val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
            } catch (_: Exception) {}

            try {
                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            } catch (_: Exception) {}

            try {
                val maxNotification = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotification, 0)
            } catch (_: Exception) {}

            try {
                val maxSystem = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, maxSystem, 0)
            } catch (_: Exception) {}

            try {
                val maxDtmf = audioManager.getStreamMaxVolume(AudioManager.STREAM_DTMF)
                audioManager.setStreamVolume(AudioManager.STREAM_DTMF, maxDtmf, 0)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val maxAccessibility = audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY)
                    audioManager.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, maxAccessibility, 0)
                } catch (_: Exception) {}
            }

            if (!isNearEar && !isBluetoothScoConnected) {
                setSpeakerphoneOn(true)

                val reApplyDelays = listOf(80L, 200L, 400L, 600L, 1000L)
                for ((index, delay) in reApplyDelays.withIndex()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (audioSessionActive && !isNearEar) {
                            try { audioManager.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
                            setSpeakerphoneOn(true)
                            try {
                                val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                if (currentMusic < maxMusic) {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
                                }
                            } catch (_: Exception) {}
                        }
                    }, delay)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SpeechManager", "Volume boost failed: ${e.message}")
        }
    }

    private fun reapplyVoiceRouting() {
        try {
            try { audioManager.mode = AudioManager.MODE_NORMAL } catch (_: Exception) {}
            setSpeakerphoneOn(true)
            try {
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentMusic < maxMusic) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.w("SpeechManager", "Re-apply routing failed: ${e.message}")
        }
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

        cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}\\s.,;:!?¿¡()\\-—]"), "")
        cleaned = cleaned
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s*([.,;:!?])\\s*"), "$1 ")
            .trim()

        return cleaned
    }

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
                    val normalized = (rmsdB / 12f).coerceIn(0f, 1f)
                    onVolumeLevelChanged?.invoke(normalized)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    onListeningStateChanged?.invoke(false)

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

    fun startVoiceAudioSession() {
        if (audioSessionActive) return
        audioSessionActive = true

        try {
            requestAudioFocus()
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            try {
                savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                savedVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            } catch (_: Exception) {}

            if (volumeBoostEnabled) {
                boostVolumeForVoiceMode()
            }

            detectBluetoothSco()
            if (isBluetoothScoConnected) {
                startBluetoothSco()
                setSpeakerphoneOn(false)
            } else {
                enableProximitySensor()
                setSpeakerphoneOn(!isNearEar)
                if (!isNearEar) {
                    try {
                        audioManager.mode = AudioManager.MODE_NORMAL
                        setSpeakerphoneOn(true)
                        if (volumeBoostEnabled) {
                            boostVolumeForVoiceMode()
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (audioSessionActive && !isNearEar) {
                                setSpeakerphoneOn(true)
                                if (volumeBoostEnabled) {
                                    boostVolumeForVoiceMode()
                                }
                            }
                        }, 200)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (audioSessionActive && !isNearEar) {
                                setSpeakerphoneOn(true)
                            }
                        }, 500)
                    } catch (e: Exception) {
                        android.util.Log.w("SpeechManager", "Speaker force error: ${e.message}")
                    }
                } else {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    setSpeakerphoneOn(false)
                }
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
        stopSpeaking()
        stopBluetoothSco()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            setSpeakerphoneOn(true)
            abandonAudioFocus()

            try {
                if (savedMusicVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
                    savedMusicVolume = -1
                }
                if (savedVoiceCallVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceCallVolume, 0)
                    savedVoiceCallVolume = -1
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Error stopping voice session: ${e.message}", e)
        }

        noiseFloorDb = 45.0
        adaptiveThresholdDb = 62.0
        calibrationFrames = 0
    }

    fun startBargeInMonitor() {
        if (bargeInActive) return
        bargeInActive = true

        bargeInThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            if (continuousAudioRecord == null || continuousAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                releaseContinuousAudioRecord()
                val sampleRate = 16000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                try {
                    _micState.value = MicState.Available
                    continuousAudioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 2
                    )
                } catch (e: SecurityException) {
                    android.util.Log.e("SpeechManager", "Mic permission denied", e)
                    _micState.value = MicState.Denied
                    bargeInActive = false
                    return@Thread
                } catch (e: Exception) {
                    android.util.Log.e("SpeechManager", "AudioRecord error: ${e.message}", e)
                    _micState.value = MicState.Error(e)
                    bargeInActive = false
                    return@Thread
                }
            }

            val recorder = continuousAudioRecord ?: run { bargeInActive = false; return@Thread }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
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
                val requiredFrames = 12
                val requiredVadFrames = 5
                var framesSinceCooldown = 0
                calibrationFrames = 0

                while (bargeInActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = Math.sqrt(sum / read)
                        val db = if (rms > 1.0) 20.0 * Math.log10(rms) else 0.0

                        val zcr = computeZCR(buffer, read)

                        val normalizedVolume = (db / 90.0).coerceIn(0.0, 1.0)
                        onVolumeLevelChanged?.invoke(normalizedVolume.toFloat())

                        if (calibrationFrames < calibrationTarget) {
                            noiseFloorDb = noiseFloorDb * 0.8 + db * 0.2
                            adaptiveThresholdDb = noiseFloorDb + thresholdAboveNoise
                            calibrationFrames++
                            continue
                        }

                        val sinceLastBargeIn = System.currentTimeMillis() - lastBargeInAt
                        if (sinceLastBargeIn < 1500L) {
                            highEnergyFrames = 0
                            vadVoiceFrames = 0
                            continue
                        }

                        val elapsed = System.currentTimeMillis() - ttsStartedAt
                        if (elapsed < bargeInCooldownMs) {
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

                        val isVoiceLike = !vadEnabled || (zcr > vadZcrThreshold && zcr < 80.0)

                        if (db > adaptiveThresholdDb) {
                            if (isVoiceLike) {
                                highEnergyFrames++
                                vadVoiceFrames++
                            } else {
                                highEnergyFrames = maxOf(0, highEnergyFrames - 1)
                                vadVoiceFrames = maxOf(0, vadVoiceFrames - 1)
                            }

                            if (highEnergyFrames >= requiredFrames && vadVoiceFrames >= requiredVadFrames) {
                                lastBargeInAt = System.currentTimeMillis()
                                bargeInActive = false
                                onBargeInDetected?.invoke()
                                break
                            }
                        } else {
                            highEnergyFrames = maxOf(0, highEnergyFrames - 1)
                            vadVoiceFrames = maxOf(0, vadVoiceFrames - 1)
                            noiseFloorDb = noiseFloorDb * 0.98 + db * 0.02
                            adaptiveThresholdDb = noiseFloorDb + thresholdAboveNoise
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Barge-in error: ${e.message}", e)
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                bargeInActive = false
            }
        }
        bargeInThread?.start()
    }

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
