package com.nexa.ai.voice

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
<<<<<<< Updated upstream
import android.os.HandlerThread
=======
import android.media.ToneGenerator
import com.nexa.ai.debug.TraeDebug
>>>>>>> Stashed changes
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.VoiceType
import java.util.Locale

/**
 * Manages Text-to-Speech and Speech-to-Text functionality.
 * Enhanced with audio focus, Bluetooth SCO, adaptive barge-in with VAD,
 * proximity sensor support, and volume feedback.
 *
 * Changelog v4.0 — HANDS-FREE VOLUME FIX:
 * - CRITICAL FIX: onDone/onError in TTS no longer abandon audio focus during voice mode
 *   (was the #1 cause of low volume — every TTS completion reset audio routing)
 * - CRITICAL FIX: Audio focus now uses USAGE_MEDIA for hands-free (forces speaker routing)
 *   (USAGE_VOICE_COMMUNICATION routes to earpiece on most OEMs)
 * - CRITICAL FIX: MODE_NORMAL is maintained throughout hands-free session
 *   (MODE_IN_COMMUNICATION forces earpiece routing on Samsung/Xiaomi/OPPO/Huawei)
 * - Added: STREAM_DTMF boost (some devices route TTS through this in comm mode)
 * - Added: Volume re-boost after TTS starts (post-speak verification at 200ms)
 * - Added: isSpeakerphoneActive() verification method
 * - Improved: boostVolumeForHandsFree() now called only during voice mode
 * - Improved: Audio focus request differentiated between earpiece and speaker modes
 * - Improved: Multiple speaker re-apply cycles increased from 3 to 5 with 1s final check
 * - Improved: MODE_NORMAL re-applied after every speak() call in hands-free mode
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
<<<<<<< Updated upstream
    @Volatile private var ttsInitializing = false  // v5.4: Guard against double-init
    private var isCurrentlyListening = false
    private var recognizeFailCount = 0  // v5.2: prevent infinite recognizer recreation leak
    private var ttsInitRetryCount = 0  // v5.4: Track TTS init retries
    private val maxTtsInitRetries = 3   // v5.4: Max TTS init retry attempts
=======
    private var toneGenerator: ToneGenerator? = null
    private var isCurrentlyListening = false
    private var recognizeFailCount = 0  // v5.2: prevent infinite recognizer recreation leak
    @Volatile
    private var preferBluetoothSco = true
    private var btNoMatchCount = 0
    private var lastBtNoMatchAt = 0L
    @Volatile
    private var hasHeardRmsInSession = false
>>>>>>> Stashed changes

    // ═══════════════════════════════════════════════════════════════
    //  v5.0 FIX: Hands-free cut-off prevention flags
    // ═══════════════════════════════════════════════════════════════
    // Bug 1 fix: Prevent speak() -> stopSpeaking() from triggering
    // an unwanted listening restart via onSpeakingStateChanged(false)
    @Volatile
    private var isPreparingToSpeak = false

    // Bug 2 fix: Track audio focus loss type for pause/resume
    @Volatile
    private var isPausedByFocusLoss = false

    // Bug 5 fix: Ensure speaking state callbacks are synchronized
    private val speechStateLock = Any()

    // Callbacks (Multi-cast to support both ViewModel and SpeechService)
    private val _onListeningStateChanged = mutableListOf<(Boolean) -> Unit>()
    var onListeningStateChanged: ((Boolean) -> Unit)?
        get() = _onListeningStateChanged.firstOrNull()
        set(value) { if (value != null) _onListeningStateChanged.add(value) }

    private val _onSpeakingStateChanged = mutableListOf<(Boolean, String?) -> Unit>()
    var onSpeakingStateChanged: ((Boolean, String?) -> Unit)?
        get() = _onSpeakingStateChanged.firstOrNull()
        set(value) { if (value != null) _onSpeakingStateChanged.add(value) }

    private val _onSpeechResult = mutableListOf<(String) -> Unit>()
    var onSpeechResult: ((String) -> Unit)?
        get() = _onSpeechResult.firstOrNull()
        set(value) { if (value != null) _onSpeechResult.add(value) }

    private val _onSpeechPartial = mutableListOf<(String) -> Unit>()
    var onSpeechPartial: ((String) -> Unit)?
        get() = _onSpeechPartial.firstOrNull()
        set(value) { if (value != null) _onSpeechPartial.add(value) }

    private val _onError = mutableListOf<(String) -> Unit>()
    var onError: ((String) -> Unit)?
        get() = _onError.firstOrNull()
        set(value) { if (value != null) _onError.add(value) }

    private val _onInputTextChanged = mutableListOf<(String) -> Unit>()
    var onInputTextChanged: ((String) -> Unit)?
        get() = _onInputTextChanged.firstOrNull()
        set(value) { if (value != null) _onInputTextChanged.add(value) }

    private val _onRecognitionEnded = mutableListOf<() -> Unit>()
    var onRecognitionEnded: (() -> Unit)?
        get() = _onRecognitionEnded.firstOrNull()
        set(value) { if (value != null) _onRecognitionEnded.add(value) }

    private val _onBargeInDetected = mutableListOf<() -> Unit>()
    var onBargeInDetected: (() -> Unit)?
        get() = _onBargeInDetected.firstOrNull()
        set(value) { if (value != null) _onBargeInDetected.add(value) }

    private val _onVolumeLevelChanged = mutableListOf<(Float) -> Unit>()
    var onVolumeLevelChanged: ((Float) -> Unit)?  // 0f..1f real-time volume for visual feedback
        get() = _onVolumeLevelChanged.firstOrNull()
        set(value) { if (value != null) _onVolumeLevelChanged.add(value) }

    private val _onProximityChanged = mutableListOf<(Boolean) -> Unit>()
    var onProximityChanged: ((Boolean) -> Unit)?  // true = near (use earpiece), false = far (use speaker)
        get() = _onProximityChanged.firstOrNull()
        set(value) { if (value != null) _onProximityChanged.add(value) }

    private val _onThinkingStateChanged = mutableListOf<(Boolean) -> Unit>()
    var onThinkingStateChanged: ((Boolean) -> Unit)?
        get() = _onThinkingStateChanged.firstOrNull()
        set(value) { if (value != null) _onThinkingStateChanged.add(value) }

    private fun invokeListeningStateChanged(state: Boolean) = _onListeningStateChanged.forEach { it.invoke(state) }
    private fun invokeSpeakingStateChanged(state: Boolean, id: String?) = _onSpeakingStateChanged.forEach { it.invoke(state, id) }
    private fun invokeSpeechResult(text: String) = _onSpeechResult.forEach { it.invoke(text) }
    private fun invokeSpeechPartial(text: String) = _onSpeechPartial.forEach { it.invoke(text) }
    private fun invokeError(error: String) = _onError.forEach { it.invoke(error) }
    private fun invokeInputTextChanged(text: String) = _onInputTextChanged.forEach { it.invoke(text) }
    private fun invokeRecognitionEnded() = _onRecognitionEnded.forEach { it.invoke() }
    private fun invokeBargeInDetected() = _onBargeInDetected.forEach { it.invoke() }
    private fun invokeVolumeLevelChanged(vol: Float) = _onVolumeLevelChanged.forEach { it.invoke(vol) }
    private fun invokeProximityChanged(state: Boolean) = _onProximityChanged.forEach { it.invoke(state) }
    private fun invokeThinkingStateChanged(state: Boolean) = _onThinkingStateChanged.forEach { it.invoke(state) }

    // Barge-in: track whether TTS is actively playing
    @Volatile
    var isTtsActive: Boolean = false
        private set

    // Cooldown: don't allow barge-in until TTS has been playing for this long
    // Prevents TTS audio from triggering false barge-in via mic bleed
    private var ttsStartedAt: Long = 0L
    // ═══ v5.0 BUG 4 FIX ═══
    // Increased from 2.5s to 3.5s - 2.5s was too short and caused
    // false barge-in triggers from TTS mic bleed on some devices
    private val bargeInCooldownMs = 2800L
    private var lastBargeInAt: Long = 0L  // Prevents rapid re-triggers

    // Adaptive barge-in threshold — calibrates to device noise floor
    private var noiseFloorDb: Double = 45.0
    private var adaptiveThresholdDb: Double = 62.0
    private val thresholdAboveNoise = 15.0 // Must be this much above noise floor
    private var calibrationFrames = 0
    private val calibrationTarget = 30  // Frames to calibrate noise floor

    // VAD (Voice Activity Detection) — zero-crossing rate
    // Helps distinguish actual voice from noise/tones
    // ═══ v5.0 BUG 4 FIX ═══
    // Increased from 15.0 to 18.0 - reduces false barge-in from
    // TTS audio bleed that has some voice-like characteristics
    private val vadZcrThreshold = 18.0
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
    private var isBluetoothScoSupported = false
    @Volatile private var isStartingSco = false
    private var scoConnected = false
    @Volatile private var hasBtMicDevice = false

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
                    invokeProximityChanged(isNearEar)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Current settings
    private var currentLanguage: AppLanguage = AppLanguage.SPANISH
    private var currentVoiceType: VoiceType = VoiceType.FEMALE_1

    // TTS stream fallback tracking
    private var useVoiceCallStream = false  // Changed: default to STREAM_MUSIC for louder hands-free volume

    // Volume boost — persistent preference for louder hands-free
    private var volumeBoostEnabled = true  // Default ON for louder hands-free
    private var speechRate = 0.85f  // v5.2: Slower default for natural speech (was 1.0)

    // Saved volume levels to restore after voice mode
    private var savedMusicVolume = -1
    private var savedVoiceCallVolume = -1

    /**
     * v5.4: Initialize SpeechManager. Idempotent — safe to call multiple times.
     * Prevents double TTS initialization which was causing duplicate engines.
     */
    fun initialize() {
        if (ttsInitializing || ttsReady) {
            android.util.Log.d("SpeechManager", "initialize() called but TTS already ${if (ttsReady) "ready" else "initializing"} — skipping")
            // Still ensure other subsystems are set up
            detectBluetoothSco()
            if (!scoReceiverRegistered) registerScoStateReceiver()
            initProximitySensor()
            return
        }
        initTTS()
        detectBluetoothSco()
        registerScoStateReceiver()
        initProximitySensor()
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Failed to init ToneGenerator: ${e.message}")
        }
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
    private fun playSystemTone(toneType: Int, durationMs: Int = 120) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            android.util.Log.w("SpeechManager", "playSystemTone failed: ${e.message}")
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
            if (scoConnected) return  // BT takes priority
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

    /**
     * Requests audio focus with attributes appropriate for the current mode.
     * v4.0: For hands-free/speaker mode, uses USAGE_MEDIA to force speaker routing.
     * USAGE_VOICE_COMMUNICATION causes Android to route to earpiece on most OEMs.
     */
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            // Use USAGE_VOICE_COMMUNICATION always during active voice sessions.
            // This matches ChatGPT/WhatsApp approach: MODE_IN_COMMUNICATION + STREAM_VOICE_CALL
            // + USAGE_VOICE_COMMUNICATION = consistent VoIP pipeline that Android won't duck.
            val attrs = if (audioSessionActive) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            }

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
                            // ═══ v5.0 BUG 2 FIX ═══
                            // Transient focus loss = notification, alarm, etc.
                            // Don't kill TTS permanently - just pause it.
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                if (isTtsActive && audioSessionActive) {
                                    isPausedByFocusLoss = true
                                    tts?.stop()
                                    isTtsActive = false
                                    // Don't abandon focus, don't trigger callbacks
                                    android.util.Log.d("SpeechManager", "TTS paused by transient focus loss")
                                } else {
                                    hasAudioFocus = false
                                    stopSpeaking()
                                }
                            }
                            // ═══ v5.0 BUG 2 FIX ═══
                            // Focus regained after transient loss - resume
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (isPausedByFocusLoss && audioSessionActive) {
                                    isPausedByFocusLoss = false
                                    hasAudioFocus = true
                                    reapplyHandsFreeRouting()
                                    invokeSpeakingStateChanged(false, null)
                                    android.util.Log.d("SpeechManager", "TTS resuming after transient focus loss")
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
                val streamType = AudioManager.STREAM_MUSIC
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
                                    reapplyHandsFreeRouting()
                                    invokeSpeakingStateChanged(false, null)
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

    // ═══════════════════════════════════════
    //  BLUETOOTH SCO — Support for BT headsets
    // ═══════════════════════════════════════

    private fun registerScoStateReceiver() {
        if (scoReceiverRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            // FIX: RECEIVER_EXPORTED required on API 33+ for system broadcasts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(scoStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(scoStateReceiver, filter)
            }
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
            isBluetoothScoSupported = audioManager.isBluetoothScoAvailableOffCall
        } catch (e: Exception) {
            isBluetoothScoSupported = false
        }
    }

    private fun detectBluetoothMicDevice(): Boolean {
        return try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val hasSco = inputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            val hasBle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                inputs.any { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
            } else {
                false
            }
            hasSco || hasBle
        } catch (_: Exception) {
            false
        }
    }

    private fun startBluetoothSco() {
        if (!isBluetoothScoSupported || isStartingSco || scoConnected) return
        if (!hasBtMicDevice) return
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
        if (!scoConnected) return
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
<<<<<<< Updated upstream
        if (ttsInitializing || ttsReady) {
            android.util.Log.d("SpeechManager", "initTTS() skipped — already ${if (ttsReady) "ready" else "initializing"}")
            return
        }
        ttsInitializing = true
        try {
            // CRITICAL FIX v5.4: Shut down any existing TTS before creating a new one.
            // This prevents duplicate TTS engines that fight for audio output.
            tts?.stop()
            tts?.shutdown()
            tts = null

            tts = TextToSpeech(application) { status ->
                ttsInitializing = false
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    ttsInitRetryCount = 0  // Reset retry count on success
                    android.util.Log.i("SpeechManager", "TTS initialized successfully")
                    tts?.setSpeechRate(speechRate)

                    // Set default language first, then apply voice settings
                    try {
                        tts?.setLanguage(Locale.getDefault())
                    } catch (e: Exception) {
                        tts?.setLanguage(Locale.US)
                    }

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            // ═══ v5.1 BUG 1 FIX ═══
                            // Reset the flag now that TTS has actually started.
                            // This ensures onDone/onError will fire the callback
                            // when this utterance completes.
                            isPreparingToSpeak = false
                            onSpeakingStateChanged?.invoke(true, utteranceId)
                            // v4.0: Re-boost volume and re-apply speaker after TTS starts
                            // Some OEMs reset routing when TTS engine takes over audio
                            if (audioSessionActive && !isNearEar && !isBluetoothScoConnected) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (audioSessionActive && !isNearEar) {
                                        reapplyHandsFreeRouting()
                                    }
                                }, 150)
                            }
                        }
                        override fun onDone(utteranceId: String?) {
                            synchronized(speechStateLock) {
                                isTtsActive = false
                                // v4.0 CRITICAL FIX: Don't abandon audio focus during voice mode!
                                if (!audioSessionActive) {
                                    abandonAudioFocus()
                                }
                                // ═══ v5.0 BUG 1 FIX ═══
                                // Skip callback if speak() is about to start new TTS
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
                                // ═══ v5.0 BUG 1 FIX ═══
                                if (!isPreparingToSpeak) {
                                    onSpeakingStateChanged?.invoke(false, null)
                                }
                            }
                        }
                    })

                    // Apply voice settings after a small delay to let TTS fully initialize
                    Handler(Looper.getMainLooper()).postDelayed({
                        applyVoiceSettings()
                    }, 500)
                } else {
                    // v5.4 FIX: TTS init failed — retry with exponential backoff
                    ttsInitializing = false
                    ttsReady = false
                    ttsInitRetryCount++
                    android.util.Log.e("SpeechManager", "TTS init FAILED (status=$status), retry $ttsInitRetryCount/$maxTtsInitRetries")

                    if (ttsInitRetryCount < maxTtsInitRetries) {
                        // Clean up failed TTS instance
                        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
                        tts = null

                        // Retry with exponential backoff: 1s, 2s, 4s
                        val delayMs = (1000L * (1L shl (ttsInitRetryCount - 1))).coerceAtMost(4000L)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!ttsReady && !ttsInitializing) {
                                android.util.Log.d("SpeechManager", "Retrying TTS init after ${delayMs}ms...")
                                initTTS()
                            }
                        }, delayMs)
                    } else {
                        android.util.Log.e("SpeechManager", "TTS init failed $maxTtsInitRetries times — giving up")
                        onError?.invoke("tts_init_failed")
                    }
=======
        android.util.Log.d("SpeechManager", "Initializing TTS...")
        val onInit = TextToSpeech.OnInitListener { status ->
            // #region debug-point H3:tts-init
            TraeDebug.event(
                hypothesisId = "H3",
                location = "SpeechManager:initTTS",
                msg = if (status == TextToSpeech.SUCCESS) "tts_init_success" else "tts_init_failure",
                dataJson = """{"status":$status,"audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus}""",
            )
            // #endregion
            if (status == TextToSpeech.SUCCESS) {
                android.util.Log.i("SpeechManager", "TTS Initialized successfully")
                ttsReady = true
                tts?.setSpeechRate(speechRate)
                
                // Set default language first
                try {
                    tts?.setLanguage(Locale.getDefault())
                } catch (e: Exception) {
                    tts?.setLanguage(Locale.US)
>>>>>>> Stashed changes
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // #region debug-point H3:tts-start
                        TraeDebug.event(
                            hypothesisId = "H3",
                            location = "SpeechManager:onTtsStart",
                            msg = "tts_start",
                            dataJson = """{"utteranceId":"${(utteranceId ?: "null").replace("\"", "\\\"")}","audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"scoConnected":$scoConnected,"isNearEar":$isNearEar}""",
                        )
                        // #endregion
                        android.util.Log.d("SpeechManager", "TTS started: $utteranceId")
                        isPreparingToSpeak = false
                        isTtsActive = true
                        invokeSpeakingStateChanged(true, utteranceId)
                    }
                    override fun onDone(utteranceId: String?) {
                        // #region debug-point H3:tts-done
                        TraeDebug.event(
                            hypothesisId = "H3",
                            location = "SpeechManager:onTtsDone",
                            msg = "tts_done",
                            dataJson = """{"utteranceId":"${(utteranceId ?: "null").replace("\"", "\\\"")}","audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"isPreparingToSpeak":$isPreparingToSpeak}""",
                        )
                        // #endregion
                        android.util.Log.d("SpeechManager", "TTS done: $utteranceId")
                        synchronized(speechStateLock) {
                            isTtsActive = false
                            if (!audioSessionActive) abandonAudioFocus()
                            if (!isPreparingToSpeak) invokeSpeakingStateChanged(false, null)
                        }
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        // #region debug-point H3:tts-error
                        TraeDebug.event(
                            hypothesisId = "H3",
                            location = "SpeechManager:onTtsError",
                            msg = "tts_error",
                            dataJson = """{"utteranceId":"${(utteranceId ?: "null").replace("\"", "\\\"")}","audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"isPreparingToSpeak":$isPreparingToSpeak}""",
                        )
                        // #endregion
                        android.util.Log.e("SpeechManager", "TTS error: $utteranceId")
                        synchronized(speechStateLock) {
                            isTtsActive = false
                            if (!audioSessionActive) abandonAudioFocus()
                            if (!isPreparingToSpeak) invokeSpeakingStateChanged(false, null)
                        }
                    }
                })

                Handler(Looper.getMainLooper()).postDelayed({ applyVoiceSettings() }, 500)
            } else {
                android.util.Log.e("SpeechManager", "TTS Initialization failed with status: $status")
            }
        }

        try {
            // Re-create TTS to ensure fresh binding
            tts?.shutdown()
            // Try Google engine first
            tts = TextToSpeech(application, onInit, "com.google.android.tts")
        } catch (e: Exception) {
<<<<<<< Updated upstream
            ttsInitializing = false
            android.util.Log.e("SpeechManager", "TTS init error: ${e.message}", e)
=======
            android.util.Log.e("SpeechManager", "Google TTS engine not available: ${e.message}")
            tts = TextToSpeech(application, onInit)
>>>>>>> Stashed changes
        }
    }

    /**
     * v5.4: Check if TTS engine is alive and responsive.
     * If TTS has died (common on some OEMs), reinitialize it.
     * Call this before any speak() operation.
     */
    private fun ensureTtsAlive(): Boolean {
        if (ttsReady && tts != null) {
            // Quick health check: try to get voices — if this throws, TTS is dead
            try {
                val engines = tts?.engines
                if (!engines.isNullOrEmpty()) return true
            } catch (e: Exception) {
                android.util.Log.w("SpeechManager", "TTS health check failed — engine appears dead: ${e.message}")
            }
        }

        // TTS is dead or not ready — try to reinitialize
        android.util.Log.w("SpeechManager", "TTS not alive — attempting reconnection...")
        ttsReady = false
        ttsInitializing = false
        ttsInitRetryCount = 0  // Reset for fresh attempt
        initTTS()

        // Give it a moment to initialize
        return ttsReady
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
<<<<<<< Updated upstream
        // v5.4 FIX: Use ensureTtsAlive() to auto-reconnect dead TTS engines
        if (!ttsReady || tts == null) {
            if (!ensureTtsAlive()) {
                android.util.Log.w("SpeechManager", "speak() called but TTS not ready — attempting reconnect")
                // Don't just return silently — try again after TTS reconnects
                Handler(Looper.getMainLooper()).postDelayed({
                    if (ttsReady && tts != null) {
                        speak(text, messageId, currentSpeakingId)
                    } else {
                        android.util.Log.e("SpeechManager", "TTS still not ready after reconnect attempt")
                        onSpeakingStateChanged?.invoke(false, null)
                    }
                }, 1500)
                return
            }
=======
        invokeThinkingStateChanged(false)
        android.util.Log.d("SpeechManager", "speak() called with text: ${text.take(20)}..., ttsReady=$ttsReady, tts=${tts != null}")
        // #region debug-point H3:speak-called
        TraeDebug.event(
            hypothesisId = "H3",
            location = "SpeechManager:speak",
            msg = "tts_speak_called",
            dataJson = """{"ttsReady":$ttsReady,"hasTts":${tts != null},"textLength":${text.length},"messageIdProvided":${messageId != null},"sameMessage":${messageId != null && currentSpeakingId == messageId},"audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"isSpeaking":$isTtsActive}""",
        )
        // #endregion
        
        if (!ttsReady || tts == null) {
            android.util.Log.w("SpeechManager", "TTS not ready, attempting re-init")
            // #region debug-point H3:speak-not-ready
            TraeDebug.event(
                hypothesisId = "H3",
                location = "SpeechManager:speak",
                msg = "tts_not_ready_reinit",
                dataJson = """{"ttsReady":$ttsReady,"hasTts":${tts != null}}""",
            )
            // #endregion
            initTTS()
            // Wait a bit and try again once if it was just initializing
            Handler(Looper.getMainLooper()).postDelayed({
                if (ttsReady && tts != null) speak(text, messageId, currentSpeakingId)
            }, 1000)
            return
>>>>>>> Stashed changes
        }

        if (messageId != null && currentSpeakingId == messageId) {
            android.util.Log.d("SpeechManager", "speak() aborted: already speaking this message")
            stopSpeaking()
            return
        }

        isPreparingToSpeak = true
        android.util.Log.d("SpeechManager", "Stopping current speech before starting new one")
        stopSpeaking()
        
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) {
            android.util.Log.w("SpeechManager", "speak() aborted: cleaned text is blank")
            isPreparingToSpeak = false
            return
        }

        try {
            requestAudioFocus()

<<<<<<< Updated upstream
            // FIX: Re-enabled volume boost for hands-free with safe thread handling
            if (volumeBoostEnabled && audioSessionActive) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        boostVolumeForHandsFree()
                    } catch (e: Exception) {
                        android.util.Log.w("SpeechManager", "Volume boost error: ${e.message}")
                    }
                }
            }

=======
>>>>>>> Stashed changes
            isTtsActive = true
            if (audioSessionActive && !isNearEar && !scoConnected) {
                setSpeakerphoneOn(true)
            }
            ttsStartedAt = System.currentTimeMillis()
            val utteranceId = messageId ?: "msg_${System.currentTimeMillis()}"

            val useStream = AudioManager.STREAM_MUSIC

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, useStream)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            
            val queueMode = TextToSpeech.QUEUE_FLUSH
            android.util.Log.d("SpeechManager", "Executing tts.speak for utterance: $utteranceId on stream $useStream")
            
            val result = tts?.speak(cleaned, queueMode, params, utteranceId)
            // #region debug-point H3:speak-result
            TraeDebug.event(
                hypothesisId = "H3",
                location = "SpeechManager:speak",
                msg = "tts_speak_result",
                dataJson = """{"utteranceId":"${utteranceId.replace("\"", "\\\"")}","result":${result ?: -999},"queueMode":$queueMode,"stream":$useStream,"cleanedLength":${cleaned.length},"audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"scoConnected":$scoConnected}""",
            )
            // #endregion
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("SpeechManager", "TTS.speak returned ERROR")
                // ... (retry logic remains)
            } else {
                android.util.Log.i("SpeechManager", "TTS.speak called successfully (result=$result)")
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "TTS speak exception: ${e.message}", e)
            isTtsActive = false
            isPreparingToSpeak = false
            if (!audioSessionActive) abandonAudioFocus()
            invokeSpeakingStateChanged(false, null)
        }
    }

    fun stopSpeaking() {
        synchronized(speechStateLock) {
            isTtsActive = false
            tts?.stop()
            // Don't abandon audio focus in voice mode — keep the session active
            if (!audioSessionActive) {
                abandonAudioFocus()
            }
            // ═══ v5.0 BUG 1 FIX ═══
            // If we're preparing to speak again (speak() called stopSpeaking),
            // don't fire the callback that would trigger listening restart.
            if (!isPreparingToSpeak) {
                invokeSpeakingStateChanged(false, null)
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

    /** v5.4: Public accessor for TTS readiness state */
    fun isTtsReady(): Boolean = ttsReady

    /**
     * Aggressively boosts all relevant audio streams to maximum volume
     * for hands-free/speaker mode. This is the key fix for "too low" volume.
     */
    private fun boostVolumeForHandsFree() {
        try {
            // Force audio mode to NORMAL for speaker output to maintain loud volume
            if (!isNearEar && !scoConnected && audioSessionActive) {
                audioManager.mode = AudioManager.MODE_NORMAL
                setSpeakerphoneOn(true)
                
                // Actual volume boost for STREAM_MUSIC to fix low volume in hands-free mode
                try {
                    val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                } catch (e: Exception) {
                    android.util.Log.w("SpeechManager", "Failed to max music volume: ${e.message}")
                }
            }

            android.util.Log.d("SpeechManager", "Volume Boost Applied for hands-free.")
        } catch (e: Exception) {
            android.util.Log.w("SpeechManager", "Volume boost failed: ${e.message}")
        }
    }

    private fun reapplyHandsFreeRouting() {
<<<<<<< Updated upstream
        // FIX: Re-enabled with safe main-thread execution to prevent TTS engine death.
        // The original crash was caused by calling audio routing methods from a background thread.
        // Now all audio routing calls run on the main looper.
        try {
            if (!audioSessionActive) return
            if (isBluetoothScoConnected && scoConnected) return  // BT takes priority

            // Must run on main thread — audio routing APIs require it
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Handler(Looper.getMainLooper()).post { reapplyHandsFreeRouting() }
                return
            }

            // Re-apply MODE_NORMAL for speaker output
            audioManager.mode = AudioManager.MODE_NORMAL

            // Re-apply speaker if not near ear and no BT
            if (!isNearEar && !isBluetoothScoConnected) {
                setSpeakerphoneOn(true)
                // Verify speaker is actually active
                if (!isSpeakerphoneActive()) {
                    android.util.Log.w("SpeechManager", "Speaker not active after reapply, retrying...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (audioSessionActive && !isNearEar) {
                            setSpeakerphoneOn(true)
                        }
                    }, 100)
                }
            }
            android.util.Log.d("SpeechManager", "Hands-free routing re-applied successfully")
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "reapplyHandsFreeRouting error: ${e.message}", e)
=======
        if (!audioSessionActive) return
        try {
            if (!isNearEar && !scoConnected) {
                setSpeakerphoneOn(true)
            }
        } catch (e: Exception) {
            android.util.Log.w("SpeechManager", "reapplyHandsFreeRouting error: ${e.message}")
>>>>>>> Stashed changes
        }
    }

    /**
     * v4.0: Checks if speakerphone is currently active.
     * Uses modern API on API 31+ and deprecated API as fallback.
     */
    private fun isSpeakerphoneActive(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.communicationDevice
                device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    private fun cleanForSpeech(text: String): String {
        // 1. Remove URLs
        var cleaned = text.replace(Regex("https?://\\S+"), "")
        
        // 2. Remove Markdown formatting
        cleaned = cleaned.replace(Regex("#{1,6}\\s*"), "")
        cleaned = cleaned.replace(Regex("[*_~`]+"), "")
        cleaned = cleaned.replace(Regex("```[\\s\\S]*?```"), "")
        
        // 3. Remove list markers
        cleaned = cleaned.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        
        // 4. Replace newlines with period+space for natural TTS pausing
        cleaned = cleaned.replace(Regex("\\n+"), ". ")
        
        // 5. Keep ALL Unicode letters (ñ, á, é, í, ó, ú, ¿, ¡, etc.)
        // \p{L}=letters, \p{N}=numbers, \p{M}=marks/accents
        cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s.,?!;:¿¡\u0027\"()-]"), " ")
        
        // 6. Collapse whitespace
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()
        
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
            
            // v5.3: Ultra-patient mode. Stay open as long as possible.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            
            // This is key: tell Google not to cut off so quickly
            putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 10000L)
            putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 6000L)

            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (speechRecognizer != null) return speechRecognizer

        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            invokeError("voice_unavailable")
            return null
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    playSystemTone(ToneGenerator.TONE_PROP_ACK)
                    invokeThinkingStateChanged(false)
                    TraeDebug.event(
                        hypothesisId = "SM",
                        location = "SpeechManager:onReadyForSpeech",
                        msg = "ready_for_speech",
                        dataJson = """{"audioSessionActive":$audioSessionActive,"isNearEar":$isNearEar,"btSupported":$isBluetoothScoSupported,"btScoActive":$scoConnected,"hasBtMic":$hasBtMicDevice,"hasAudioFocus":$hasAudioFocus}""",
                    )
                    if (!hasAudioFocus) {
                        requestAudioFocus()
                        TraeDebug.event(
                            hypothesisId = "SM",
                            location = "SpeechManager:onReadyForSpeech",
                            msg = "reacquire_audio_focus",
                            dataJson = """{"hasAudioFocus":$hasAudioFocus}""",
                        )
                    }
                    invokeListeningStateChanged(true)
                }
                override fun onBeginningOfSpeech() {
                    TraeDebug.event(
                        hypothesisId = "SM",
                        location = "SpeechManager:onBeginningOfSpeech",
                        msg = "beginning_of_speech",
                        dataJson = """{"isTtsActive":$isTtsActive,"audioSessionActive":$audioSessionActive}""",
                    )
                    if (isTtsActive) {
                        invokeBargeInDetected()
                    }
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // Report volume level for visual feedback
                    val normalized = (rmsdB / 12f).coerceIn(0f, 1f)
                    invokeVolumeLevelChanged(normalized)
                    if (!hasHeardRmsInSession && rmsdB > 3f) {
                        hasHeardRmsInSession = true
                        TraeDebug.event(
                            hypothesisId = "SM",
                            location = "SpeechManager:onRmsChanged",
                            msg = "rms_detected",
                            dataJson = """{"rmsdB":$rmsdB,"audioSessionActive":$audioSessionActive,"preferBtSco":$preferBluetoothSco,"btSupported":$isBluetoothScoSupported,"btScoActive":$scoConnected,"hasBtMic":$hasBtMicDevice}""",
                        )
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    playSystemTone(ToneGenerator.TONE_PROP_NACK, 200)
                    isCurrentlyListening = false
                    invokeListeningStateChanged(false)
                    invokeThinkingStateChanged(false)

                    // Recreate SpeechRecognizer on these unrecoverable errors
                    val shouldRecreate = when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_AUDIO,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> true
                        else -> false
                    }

                    if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                        audioSessionActive && scoConnected && preferBluetoothSco && !hasBtMicDevice
                    ) {
                        val now = System.currentTimeMillis()
                        btNoMatchCount = if (now - lastBtNoMatchAt <= 15000L) btNoMatchCount + 1 else 1
                        lastBtNoMatchAt = now
                        if (btNoMatchCount >= 2) {
                            preferBluetoothSco = false
                            stopBluetoothSco()
                            try {
                                setSpeakerphoneOn(true)
                            } catch (_: Exception) {}
                            TraeDebug.event(
                                hypothesisId = "SM",
                                location = "SpeechManager:onError",
                                msg = "bt_sco_fallback_disabled",
                                dataJson = """{"error":$error,"btNoMatchCount":$btNoMatchCount}""",
                            )
                        }
                    }

                    TraeDebug.event(
                        hypothesisId = "SM",
                        location = "SpeechManager:onError",
                        msg = "speech_error",
                        dataJson = """{"error":$error,"shouldRecreate":$shouldRecreate,"audioSessionActive":$audioSessionActive,"isNearEar":$isNearEar,"btSupported":$isBluetoothScoSupported,"btScoActive":$scoConnected,"hasBtMic":$hasBtMicDevice,"preferBtSco":$preferBluetoothSco,"hasAudioFocus":$hasAudioFocus}""",
                    )

                    if (shouldRecreate) {
                        try {
                            speechRecognizer?.cancel()
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        // FIX v5.2: Limit recreation to prevent memory leak
                        recognizeFailCount++
                        if (recognizeFailCount >= 3) {
                            android.util.Log.w("SpeechManager", "SpeechRecognizer failed 3 times, waiting 5s before retry")
                            Handler(Looper.getMainLooper()).postDelayed({ recognizeFailCount = 0 }, 5000)
                        }
                    } else {
                        recognizeFailCount = 0
                    }

                    // These errors are "normal" — just retry
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_CLIENT) {
                        invokeRecognitionEnded()
                    } else if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        invokeError("voice_error: $error")
                    }
                }
                override fun onResults(results: Bundle?) {
                    playSystemTone(ToneGenerator.TONE_PROP_BEEP2)
                    isCurrentlyListening = false
                    invokeListeningStateChanged(false)
                    invokeThinkingStateChanged(true)

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()

                    val textLen = text?.length ?: 0
                    val matchCount = matches?.size ?: 0
                    TraeDebug.event(
                        hypothesisId = "SM",
                        location = "SpeechManager:onResults",
                        msg = "speech_results",
                        dataJson = """{"matchCount":$matchCount,"textLength":$textLen,"audioSessionActive":$audioSessionActive}""",
                    )

                    if (!text.isNullOrBlank()) {
                        invokeInputTextChanged(text)
                        invokeSpeechResult(text)
                    } else {
                        invokeRecognitionEnded()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    TraeDebug.event(
                        hypothesisId = "SM",
                        location = "SpeechManager:onPartialResults",
                        msg = "partial_result",
                        dataJson = """{"textLength":${text.length}}""",
                    )
                    invokeSpeechPartial(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        return speechRecognizer
    }

    fun startListening() {
        Handler(Looper.getMainLooper()).post {
            if (isCurrentlyListening) return@post

            try {
                val recognizer = getOrCreateRecognizer() ?: return@post

                isCurrentlyListening = true
                hasHeardRmsInSession = false
                hasBtMicDevice = detectBluetoothMicDevice()

                if (!isTtsActive) {
                    stopSpeaking()
                }

                if (audioSessionActive && !hasAudioFocus) requestAudioFocus()
                if (audioSessionActive && scoConnected && !preferBluetoothSco) {
                    stopBluetoothSco()
                    try {
                        setSpeakerphoneOn(true)
                    } catch (_: Exception) {}
                }

                // #region debug-point H1:start-listening
                TraeDebug.event(
                    hypothesisId = "H1",
                    location = "SpeechManager:startListening",
                    msg = "start_listening_called",
                    dataJson = """{"audioSessionActive":$audioSessionActive,"hasAudioFocus":$hasAudioFocus,"scoConnected":$scoConnected,"preferBtSco":$preferBluetoothSco,"hasBtMic":$hasBtMicDevice,"isTtsActive":$isTtsActive}""",
                )
                // #endregion
                android.util.Log.d("SpeechManager", "SpeechRecognizer.startListening() called")
                recognizer.startListening(buildRecognizerIntent())

            } catch (e: Exception) {
                isCurrentlyListening = false
                android.util.Log.e("SpeechManager", "Speech recognition error: ${e.message}", e)
                invokeListeningStateChanged(false)
                invokeError("voice_error")
            }
        }
    }

    fun stopListening() {
        Handler(Looper.getMainLooper()).post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Stop listening error: ${e.message}", e)
            }
            isCurrentlyListening = false
            invokeListeningStateChanged(false)
        }
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

            // Set normal mode for media playback
            audioManager.mode = AudioManager.MODE_NORMAL

            // No longer saving volumes as we no longer override them

            // ── INITIAL VOLUME FOR HANDS-FREE ──
            // The user requested not to force the volume to high initially.
            // It will rely entirely on the system's current media volume.

            // Bluetooth SCO: route audio to BT headset if available
            detectBluetoothSco()
            hasBtMicDevice = detectBluetoothMicDevice()
            if (isBluetoothScoSupported && hasBtMicDevice && preferBluetoothSco) {
                startBluetoothSco()
                setSpeakerphoneOn(false)
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } catch (_: Exception) {}
            } else {
                // Enable proximity sensor for auto earpiece/speaker switching
                enableProximitySensor()
                // Initial state: use speaker for hands-free (louder)
                setSpeakerphoneOn(!isNearEar)
                
                // Use MODE_NORMAL at all times during the session!
                // This ensures loud media playback via speakerphone.
                audioManager.mode = AudioManager.MODE_NORMAL
                
                try {
                    // Delayed re-apply to ensure routing sticks
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (audioSessionActive) {
                            setSpeakerphoneOn(!isNearEar)
                        }
                    }, 200)
                    // Second delayed re-apply for stubborn devices
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (audioSessionActive) {
                            setSpeakerphoneOn(!isNearEar)
                        }
                    }, 500)
                } catch (e: Exception) {
                    android.util.Log.w("SpeechManager", "Speaker force error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "AudioManager mode error: ${e.message}", e)
        }
    }

    fun stopVoiceAudioSession() {
        audioSessionActive = false

        // PRIMERO: Detener monitoreo y grabación (liberar recursos de audio)
        stopBargeInMonitor()
        releaseContinuousAudioRecord()
        disableProximitySensor()

        // SEGUNDO: Detener TTS y Bluetooth SCO
        stopSpeaking()
        stopBluetoothSco()

        // TERCERO: Restaurar configuración de audio (DESPUÉS de liberar recursos)
        try {
            // Restaurar modo de audio ANTES de abandonar foco
            audioManager.mode = AudioManager.MODE_NORMAL
            setSpeakerphoneOn(true)

            // Finalmente abandonar foco de audio
            abandonAudioFocus()

            // No longer restoring volumes as we no longer override them
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Error stopping voice session: ${e.message}", e)
        }

        // Resetear calibración para próxima sesión
        noiseFloorDb = 45.0
        adaptiveThresholdDb = 62.0
        calibrationFrames = 0
    }

    fun startBargeInMonitor() {
<<<<<<< Updated upstream
        // FIX: Re-enabled with safe thread handling and proper error recovery.
        // The original freezes were caused by AudioRecord blocking without interruption handling.
=======
>>>>>>> Stashed changes
        if (bargeInActive) return
        bargeInActive = true

        android.util.Log.d("SpeechManager", "Starting barge-in monitor (VAD)")
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
                // ═══ v5.0 BUG 4 FIX ═══
                // Increased from 8/3 to 12/5 - reduces false barge-in
                // triggers while still being responsive enough
                val requiredFrames = 12     // Energy frames required
                val requiredVadFrames = 5   // VAD-positive frames required
                var framesSinceCooldown = 0
                // Reset calibration for fresh start
                calibrationFrames = 0

                while (bargeInActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    // FIX: Check for thread interruption to prevent freezes
                    if (Thread.currentThread().isInterrupted) {
                        android.util.Log.d("SpeechManager", "Barge-in thread interrupted, exiting")
                        break
                    }
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

                        // Report volume level for visual feedback (normalized 0..1 relative to noise floor)
                        val relativeDb = db - noiseFloorDb
                        val normalizedVolume = (relativeDb / 30.0).coerceIn(0.0, 1.0)
                        invokeVolumeLevelChanged(normalizedVolume.toFloat())

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
                                invokeBargeInDetected()
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
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
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
        // v5.4: Reset all state so initialize() can be called again if needed
        tts = null
        ttsReady = false
        ttsInitializing = false
        isCurrentlyListening = false
        audioSessionActive = false
        hasAudioFocus = false
    }
}
