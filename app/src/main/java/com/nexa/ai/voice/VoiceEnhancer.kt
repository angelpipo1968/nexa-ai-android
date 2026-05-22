package com.nexa.ai.voice

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.nexa.ai.data.local.EmotionEntity
import com.nexa.ai.data.local.NexaDatabase
import com.nexa.ai.data.local.VoiceProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * NEXA Voice Enhancer — Reconocimiento de voz avanzado con capacidades emocionales
 *
 * Core Features:
 * - Wake Word Detection: "Hey NEXA" / "Oye NEXA" (local, sin servidor)
 * - Voice Emotion Analysis: Detecta emociones desde el tono de voz
 * - Speaker Identification: Reconoce al usuario por su voz
 * - Voice Activity Detection mejorado
 * - Audio Quality Analysis
 * - SSML Support: Respuestas con entonación natural
 *
 * Enhanced Features:
 * - Conversational turn-taking with customizable silence thresholds
 * - Real-time speech-to-text confidence scoring
 * - Multi-language voice detection (auto-detect Spanish / English)
 * - Voice profile storage (save/load speaker characteristics to Room DB)
 * - Continuous listening mode with smart pause detection
 * - Prosody analysis for more accurate emotion detection
 * - Noise cancellation feedback (alert when environment is too noisy)
 * - Integration callback for IoT voice commands
 */
class VoiceEnhancer(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { NexaDatabase.getInstance(application) }

    // ═══════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Core voice state tracking all real-time audio features.
     */
    data class VoiceState(
        val isWakeWordListening: Boolean = false,
        val wakeWordDetected: Boolean = false,
        val voiceEmotion: String = "neutral",
        val voiceEmotionConfidence: Float = 0f,
        val voiceEnergy: Float = 0f,
        val voicePitch: Float = 0f,
        val voiceQuality: String = "normal",
        val speakerId: String = "unknown",
        val lastWakeWordTime: Long = 0L,
        // --- Enhanced fields ---
        val detectedLanguage: String = "unknown",         // "en", "es", "mixed", "unknown"
        val languageConfidence: Float = 0f,
        val sttConfidence: Float = 0f,                    // Speech-to-text confidence 0-1
        val noiseLevel: String = "quiet",                 // "quiet", "moderate", "loud", "very_loud"
        val noiseLevelDb: Float = 0f,                     // Approximate dB level
        val isContinuousListening: Boolean = false,       // Continuous listening mode active
        val turnState: TurnState = TurnState.IDLE,        // Current conversational turn state
        val silenceDurationMs: Long = 0L,                 // Current silence duration
        val prosodyFeatures: ProsodyFeatures = ProsodyFeatures(),
        val isTooNoisy: Boolean = false                   // Environment too noisy for recognition
    )

    /**
     * Conversational turn-taking states.
     */
    enum class TurnState {
        IDLE,               // No speech detected
        USER_SPEAKING,      // User is currently speaking
        USER_PAUSING,       // User paused briefly (might continue)
        TURN_COMPLETE,      // User finished speaking (silence exceeded threshold)
        PROCESSING          // System is processing the user's turn
    }

    /**
     * Prosody features for advanced emotion and speech analysis.
     */
    data class ProsodyFeatures(
        val pitchContour: Float = 0f,         // Pitch direction: positive=rising, negative=falling
        val pitchRange: Float = 0f,           // Variability of pitch (Hz)
        val speechRate: Float = 0f,           // Estimated syllables per second
        val rhythmRegularity: Float = 0f,     // How regular the speech rhythm is (0-1)
        val intensityContour: Float = 0f,     // Energy direction: positive=increasing, negative=decreasing
        val pauseFrequency: Float = 0f,       // Pauses per second of speech
        val avgPauseDuration: Float = 0f,     // Average pause duration (ms)
        val stressPattern: String = "neutral"  // "stressed", "relaxed", "emphatic", "neutral"
    )

    /**
     * Silence threshold configuration for customizable turn-taking.
     */
    data class SilenceThresholds(
        val shortPauseMs: Long = 500L,         // Brief pause (filler territory)
        val mediumPauseMs: Long = 1200L,       // Likely end of thought
        val longPauseMs: Long = 2000L,         // Definitive turn end
        val veryLongPauseMs: Long = 3500L,     // Extended silence (user disengaged)
        val energyFloor: Float = 500f,         // Below this = silence
        val turnEndConfidenceThreshold: Float = 0.7f  // Min confidence to declare turn complete
    )

    /**
     * STT confidence result with detailed scoring.
     */
    data class STTConfidenceResult(
        val overallConfidence: Float,          // 0-1 overall confidence
        val acousticConfidence: Float,         // Audio quality confidence
        val vocabularyConfidence: Float,       // Word recognition confidence
        val isLowConfidence: Boolean,          // Below usable threshold
        val suggestion: String? = null         // Suggestion if confidence is low
    )

    /**
     * Noise cancellation feedback for the user.
     */
    data class NoiseFeedback(
        val noiseLevel: String,                // "quiet", "moderate", "loud", "very_loud"
        val approxDb: Float,                   // Approximate dB
        val message: String,                   // User-facing message
        val suggestion: String,                // Actionable suggestion
        val isTooNoisyForSTT: Boolean          // Whether STT will struggle
    )

    // ═══════════════════════════════════════════════════════════════════
    //  STATE FLOWS & PROPERTIES
    // ═══════════════════════════════════════════════════════════════════

    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Customizable silence thresholds
    var silenceThresholds = SilenceThresholds()

    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onVoiceEmotionChanged: ((String, Float) -> Unit)? = null
    var onTurnComplete: ((String, Long) -> Unit)? = null              // (transcript, silenceMs)
    var onNoiseFeedback: ((NoiseFeedback) -> Unit)? = null            // Noise alerts
    var onIoTVoiceCommand: ((String) -> Unit)? = null                 // IoT command detected
    var onLanguageDetected: ((String, Float) -> Unit)? = null         // (language, confidence)
    var onSTTConfidenceUpdate: ((STTConfidenceResult) -> Unit)? = null
    var onContinuousListeningResult: ((String, Float) -> Unit)? = null // (transcript, confidence)

    // Audio constants
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    // Wake word detection internals
    private var wakeWordAudioRecord: AudioRecord? = null
    private var wakeWordThread: Thread? = null
    @Volatile private var wakeWordListening = false

    // Continuous listening internals
    private var continuousAudioRecord: AudioRecord? = null
    private var continuousThread: Thread? = null
    @Volatile private var continuousListening = false

    // Prosody tracking buffers
    private val pitchHistory = FloatArray(20) { 0f }
    private val energyHistory = FloatArray(30) { 0f }
    private var pitchHistoryIdx = 0
    private var energyHistoryIdx = 0
    private var lastSpeechEndTime = 0L
    private var pauseCount = 0
    private var totalPauseDuration = 0L
    private var speechSegmentStart = 0L
    private var syllableCount = 0

    // Language detection internals
    private val spanishSpectralSignature = floatArrayOf(
        0.12f, 0.18f, 0.22f, 0.19f, 0.15f, 0.11f, 0.08f, 0.06f, 0.04f, 0.03f, 0.02f, 0.01f, 0.01f
    )
    private val englishSpectralSignature = floatArrayOf(
        0.10f, 0.15f, 0.20f, 0.21f, 0.17f, 0.13f, 0.10f, 0.07f, 0.05f, 0.04f, 0.03f, 0.02f, 0.01f
    )

    // IoT voice command keywords
    private val iotCommandKeywords = setOf(
        "enciende", "apaga", "prende", "desactiva", "activa", "luz", "luces", "aire",
        "termostato", "calefacción", "temperatura", "grado", "volumen", "bloquea",
        "desbloquea", "cierra", "abre", "puerta", "cerradura", "lámpara",
        "turn on", "turn off", "light", "lock", "unlock", "thermostat",
        "speaker", "brightness", "volume", "dim", "brighten"
    )

    // ═══════════════════════════════════════════════════════════════════
    //  1. CONVERSATIONAL TURN-TAKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Determine if the user's turn is complete based on silence duration,
     * speech patterns, and customizable thresholds.
     *
     * @param currentSilenceMs Current silence duration in milliseconds
     * @param hasFillerWords Whether filler words were detected in recent speech
     * @param lastText The most recent speech transcript
     * @param conversationEngine Optional conversation engine for context
     * @return true if the user's turn is considered complete
     */
    fun isTurnComplete(
        currentSilenceMs: Long,
        hasFillerWords: Boolean,
        lastText: String,
        conversationEngine: NaturalConversationEngine? = null
    ): Boolean {
        val trimmed = lastText.trim()

        // Definitive end signals — punctuation
        if (trimmed.endsWith("?") || trimmed.endsWith("!")) return true
        if (trimmed.endsWith(".")) return true

        // Very long silence always ends the turn
        if (currentSilenceMs > silenceThresholds.veryLongPauseMs) return true

        // Long silence ends turn unless user is known to pause a lot
        if (currentSilenceMs > silenceThresholds.longPauseMs) return true

        // Medium pause: check for filler words (user is thinking)
        if (currentSilenceMs in (silenceThresholds.shortPauseMs + 1)..silenceThresholds.mediumPauseMs) {
            if (hasFillerWords) return false // User is thinking, don't interrupt
        }

        // Medium-to-long pause without fillers: likely done
        if (currentSilenceMs > silenceThresholds.mediumPauseMs && !hasFillerWords) return true

        // Short pause: user is likely still speaking
        if (currentSilenceMs <= silenceThresholds.shortPauseMs) return false

        // Consult conversation engine if available for additional context
        conversationEngine?.let { engine ->
            return engine.isTurnComplete(lastText, currentSilenceMs, hasFillerWords)
        }

        // Default: wait a bit more
        return false
    }

    /**
     * Update the turn state based on current audio analysis.
     */
    fun updateTurnState(energy: Float, isVoice: Boolean) {
        val now = System.currentTimeMillis()
        val currentState = _voiceState.value.turnState

        val newState = when {
            !isVoice && energy < silenceThresholds.energyFloor -> {
                // Silence detected
                val silence = now - lastSpeechEndTime
                _voiceState.value = _voiceState.value.copy(silenceDurationMs = silence)
                when (currentState) {
                    TurnState.USER_SPEAKING -> {
                        lastSpeechEndTime = now
                        TurnState.USER_PAUSING
                    }
                    TurnState.USER_PAUSING -> {
                        if (silence > silenceThresholds.longPauseMs) {
                            TurnState.TURN_COMPLETE
                        } else {
                            TurnState.USER_PAUSING
                        }
                    }
                    else -> currentState
                }
            }
            isVoice -> {
                // Voice detected
                if (currentState == TurnState.IDLE || currentState == TurnState.TURN_COMPLETE) {
                    speechSegmentStart = now
                    syllableCount = 0
                }
                TurnState.USER_SPEAKING
            }
            else -> currentState
        }

        if (newState != currentState) {
            _voiceState.value = _voiceState.value.copy(turnState = newState)

            if (newState == TurnState.TURN_COMPLETE) {
                Handler(Looper.getMainLooper()).post {
                    onTurnComplete?.invoke("", _voiceState.value.silenceDurationMs)
                }
            }
        }
    }

    /**
     * Set the turn state to PROCESSING after turn completion.
     */
    fun markTurnProcessing() {
        _voiceState.value = _voiceState.value.copy(turnState = TurnState.PROCESSING)
    }

    /**
     * Reset turn state to IDLE (ready for next user input).
     */
    fun resetTurnState() {
        _voiceState.value = _voiceState.value.copy(
            turnState = TurnState.IDLE,
            silenceDurationMs = 0L
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. SPEECH-TO-TEXT CONFIDENCE SCORING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculate STT confidence from audio features and recognized text quality.
     *
     * @param audioBuffer Raw audio data
     * @param length Number of valid samples
     * @param recognizedText The STT output to evaluate
     * @return Detailed confidence result
     */
    fun calculateSTTConfidence(
        audioBuffer: ShortArray,
        length: Int,
        recognizedText: String
    ): STTConfidenceResult {
        // Acoustic confidence: based on signal quality
        val energy = calculateEnergy(audioBuffer, length)
        val zcr = calculateZCR(audioBuffer, length)
        val snrEstimate = estimateSNR(audioBuffer, length)

        val acousticConfidence = when {
            snrEstimate > 20f -> 0.95f
            snrEstimate > 15f -> 0.85f
            snrEstimate > 10f -> 0.70f
            snrEstimate > 5f -> 0.50f
            else -> 0.30f
        }.let { conf ->
            // Reduce if clipping detected
            val maxSample = audioBuffer.take(length).maxByOrNull { abs(it.toInt()) }?.toInt() ?: 0
            if (maxSample > 32000) conf * 0.7f else conf
        }

        // Vocabulary confidence: based on text characteristics
        val words = recognizedText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val vocabularyConfidence = when {
            words.isEmpty() -> 0.1f
            words.any { it.contains(Regex("[^\\wáéíóúñÁÉÍÓÚÑüÜ]")) && it.length <= 2 } -> 0.5f
            recognizedText.contains(Regex("\\[.*?\\]")) -> 0.3f  // Unrecognized markers
            words.size == 1 && words[0].length <= 2 -> 0.4f
            words.all { it.length > 2 } -> 0.9f
            else -> 0.7f
        }

        // Overall confidence is weighted average
        val overallConfidence = (acousticConfidence * 0.6f + vocabularyConfidence * 0.4f)
            .coerceIn(0f, 1f)

        val isLowConfidence = overallConfidence < 0.5f

        val suggestion = when {
            overallConfidence < 0.3f -> "Audio too low or noisy. Try speaking closer to the microphone."
            overallConfidence < 0.5f -> "Reconocimiento incierto. ¿Podrías repetirlo?"
            overallConfidence < 0.7f -> "No estoy seguro de haber entendido bien."
            else -> null
        }

        val result = STTConfidenceResult(
            overallConfidence = overallConfidence,
            acousticConfidence = acousticConfidence,
            vocabularyConfidence = vocabularyConfidence,
            isLowConfidence = isLowConfidence,
            suggestion = suggestion
        )

        _voiceState.value = _voiceState.value.copy(sttConfidence = overallConfidence)
        Handler(Looper.getMainLooper()).post { onSTTConfidenceUpdate?.invoke(result) }

        return result
    }

    /**
     * Estimate signal-to-noise ratio from audio buffer.
     */
    private fun estimateSNR(buffer: ShortArray, length: Int): Float {
        val len = minOf(length, buffer.size)
        if (len == 0) return 0f

        var sumSquares = 0L
        var minEnergy = Long.MAX_VALUE
        var maxEnergy = 0L
        val frameSize = 160 // 10ms at 16kHz

        for (i in 0 until len - frameSize step frameSize) {
            var frameEnergy = 0L
            for (j in i until minOf(i + frameSize, len)) {
                frameEnergy += buffer[j].toLong() * buffer[j].toLong()
            }
            sumSquares += frameEnergy
            if (frameEnergy < minEnergy) minEnergy = frameEnergy
            if (frameEnergy > maxEnergy) maxEnergy = frameEnergy
        }

        if (minEnergy == 0L) minEnergy = 1L
        val signalPower = maxEnergy.toFloat()
        val noisePower = minEnergy.toFloat()

        return if (noisePower > 0f) {
            10f * kotlin.math.log10((signalPower / noisePower).coerceAtLeast(1f))
        } else 30f
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. MULTI-LANGUAGE VOICE DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect the spoken language from audio spectral features.
     * Uses spectral signature matching between Spanish and English phoneme patterns.
     *
     * @param audioBuffer Raw audio data
     * @param length Number of valid samples
     * @param transcript Optional recognized text for lexical hints
     * @return Pair of (languageCode, confidence) e.g. ("es", 0.85f)
     */
    fun detectLanguage(
        audioBuffer: ShortArray,
        length: Int,
        transcript: String? = null
    ): Pair<String, Float> {
        val spectralFeatures = extractSpectralFeatures(audioBuffer, length)

        // Calculate spectral distance to each language signature
        val spanishDist = spectralDistance(spectralFeatures, spanishSpectralSignature)
        val englishDist = spectralDistance(spectralFeatures, englishSpectralSignature)

        // Convert distances to similarities
        val spanishSim = 1f / (1f + spanishDist)
        val englishSim = 1f / (1f + englishDist)
        val totalSim = spanishSim + englishSim

        var languageScore = if (totalSim > 0f) {
            spanishSim / totalSim  // 0 = English, 1 = Spanish
        } else 0.5f

        // Lexical hints from transcript
        transcript?.let { text ->
            val lower = text.lowercase()
            val spanishMarkers = countLanguageMarkers(lower, spanishLexicalMarkers)
            val englishMarkers = countLanguageMarkers(lower, englishLexicalMarkers)
            val totalMarkers = spanishMarkers + englishMarkers

            if (totalMarkers > 0) {
                // Blend spectral (60%) and lexical (40%) evidence
                val lexicalScore = spanishMarkers.toFloat() / totalMarkers.toFloat()
                languageScore = languageScore * 0.6f + lexicalScore * 0.4f
            }
        }

        val (lang, conf) = when {
            languageScore > 0.65f -> "es" to languageScore
            languageScore < 0.35f -> "en" to (1f - languageScore)
            else -> "mixed" to (1f - abs(languageScore - 0.5f) * 2f)
        }

        _voiceState.value = _voiceState.value.copy(
            detectedLanguage = lang,
            languageConfidence = conf
        )
        Handler(Looper.getMainLooper()).post { onLanguageDetected?.invoke(lang, conf) }

        return lang to conf
    }

    /**
     * Detect language purely from recognized text.
     */
    fun detectLanguageFromText(text: String): Pair<String, Float> {
        val lower = text.lowercase()
        val spanishCount = countLanguageMarkers(lower, spanishLexicalMarkers)
        val englishCount = countLanguageMarkers(lower, englishLexicalMarkers)
        val total = spanishCount + englishCount

        return when {
            total == 0 -> "unknown" to 0f
            spanishCount > englishCount * 2 -> "es" to (spanishCount.toFloat() / total.toFloat())
            englishCount > spanishCount * 2 -> "en" to (englishCount.toFloat() / total.toFloat())
            else -> "mixed" to 0.5f
        }
    }

    private val spanishLexicalMarkers = setOf(
        "el", "la", "los", "las", "un", "una", "de", "del", "en", "es", "por",
        "con", "para", "que", "no", "sí", "pero", "como", "más", "este", "esta",
        "estoy", "tengo", "puedes", "quiero", "necesito", "hola", "gracias",
        "por favor", "bueno", "bien", "también", "muy", "aquí", "ahora",
        "casa", "trabajo", "hoy", "mañana", "ayuda", "enciende", "apaga"
    )

    private val englishLexicalMarkers = setOf(
        "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to",
        "for", "with", "and", "but", "or", "not", "this", "that", "it", "i",
        "you", "he", "she", "we", "they", "have", "has", "do", "does", "can",
        "will", "would", "could", "should", "hello", "thanks", "please",
        "good", "well", "also", "very", "here", "now", "home", "work",
        "today", "tomorrow", "help", "turn", "light"
    )

    private fun spectralDistance(features: FloatArray, signature: FloatArray): Float {
        var dist = 0f
        val len = minOf(features.size, signature.size)
        for (i in 0 until len) {
            val diff = features[i] - signature[i]
            dist += diff * diff
        }
        return sqrt(dist.toDouble()).toFloat()
    }

    private fun countLanguageMarkers(text: String, markers: Set<String>): Int {
        val words = text.split(Regex("\\s+"))
        return words.count { it.trim() in markers }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. VOICE PROFILE STORAGE (Room DB)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Save the current speaker's voice profile to the database.
     * Accumulates features over multiple samples for robustness.
     *
     * @param speakerId Unique identifier for the speaker (defaults to current)
     * @param speakerName Optional display name
     */
    fun saveVoiceProfile(speakerId: String? = null, speakerName: String? = null) {
        scope.launch {
            try {
                val id = speakerId ?: _voiceState.value.speakerId
                val existing = db.voiceProfileDao().getBySpeakerId(id)
                val state = _voiceState.value
                val prosody = state.prosodyFeatures

                val spectralJson = JSONObject().apply {
                    val features = extractSpectralFeatures(ShortArray(0), 0)
                    // Use current energy/pitch as approximate spectral summary
                    put("avgPitch", state.voicePitch)
                    put("avgEnergy", state.voiceEnergy)
                    put("avgZCR", calculateZCR(ShortArray(0), 0))
                }.toString()

                if (existing != null) {
                    // Update existing profile with exponential moving average
                    val alpha = 0.3f // Weight for new sample
                    val updated = existing.copy(
                        avgPitch = existing.avgPitch * (1 - alpha) + state.voicePitch * alpha,
                        pitchVariance = existing.pitchVariance * (1 - alpha) +
                                abs(state.voicePitch - existing.avgPitch) * alpha,
                        avgEnergy = existing.avgEnergy * (1 - alpha) + state.voiceEnergy * alpha,
                        energyVariance = existing.energyVariance * (1 - alpha) +
                                abs(state.voiceEnergy - existing.avgEnergy) * alpha,
                        avgZCR = existing.avgZCR * (1 - alpha),
                        avgSpectralCentroid = existing.avgSpectralCentroid * (1 - alpha) +
                                state.voicePitch * alpha,  // Proxy for spectral centroid
                        spectralFeatures = spectralJson,
                        detectedLanguage = state.detectedLanguage,
                        languageConfidence = state.languageConfidence,
                        speakingRate = existing.speakingRate * (1 - alpha) +
                                prosody.speechRate * alpha,
                        sampleCount = existing.sampleCount + 1,
                        lastUpdated = System.currentTimeMillis(),
                        speakerName = speakerName ?: existing.speakerName
                    )
                    db.voiceProfileDao().upsert(updated)
                } else {
                    // Create new profile
                    val newProfile = VoiceProfileEntity(
                        speakerId = id,
                        speakerName = speakerName,
                        avgPitch = state.voicePitch,
                        pitchVariance = 0f,
                        avgEnergy = state.voiceEnergy,
                        energyVariance = 0f,
                        avgZCR = 0f,
                        avgSpectralCentroid = state.voicePitch,
                        spectralFeatures = spectralJson,
                        detectedLanguage = state.detectedLanguage,
                        languageConfidence = state.languageConfidence,
                        speakingRate = prosody.speechRate,
                        sampleCount = 1,
                        lastUpdated = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                    db.voiceProfileDao().upsert(newProfile)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Load a voice profile from the database and apply it.
     *
     * @param speakerId The speaker ID to load
     * @return The loaded profile, or null if not found
     */
    suspend fun loadVoiceProfile(speakerId: String): VoiceProfileEntity? {
        return try {
            val profile = db.voiceProfileDao().getBySpeakerId(speakerId)
            if (profile != null) {
                _voiceState.value = _voiceState.value.copy(
                    speakerId = profile.speakerId,
                    detectedLanguage = profile.detectedLanguage,
                    languageConfidence = profile.languageConfidence,
                    voicePitch = profile.avgPitch,
                    voiceEnergy = profile.avgEnergy
                )
            }
            profile
        } catch (_: Exception) { null }
    }

    /**
     * Get all saved voice profiles.
     */
    suspend fun getAllVoiceProfiles(): List<VoiceProfileEntity> {
        return try { db.voiceProfileDao().getAll() } catch (_: Exception) { emptyList() }
    }

    /**
     * Identify the current speaker by comparing against stored profiles.
     *
     * @param audioBuffer Current audio sample
     * @param length Valid sample count
     * @return Pair of (matchedSpeakerId, matchConfidence)
     */
    suspend fun identifySpeaker(audioBuffer: ShortArray, length: Int): Pair<String, Float> {
        val currentPitch = estimatePitch(audioBuffer, length)
        val currentEnergy = calculateEnergy(audioBuffer, length)

        return try {
            val profiles = db.voiceProfileDao().getAll()
            if (profiles.isEmpty()) return "unknown" to 0f

            var bestMatch = "unknown"
            var bestScore = 0f

            for (profile in profiles) {
                // Pitch similarity (weighted most)
                val pitchDiff = abs(currentPitch - profile.avgPitch)
                val pitchScore = if (profile.avgPitch > 0f) {
                    1f - (pitchDiff / profile.avgPitch).coerceIn(0f, 1f)
                } else 0f

                // Energy similarity
                val energyDiff = abs(currentEnergy - profile.avgEnergy)
                val energyScore = if (profile.avgEnergy > 0f) {
                    1f - (energyDiff / profile.avgEnergy).coerceIn(0f, 1f)
                } else 0f

                // Combined score (pitch is 70%, energy is 30%)
                val score = pitchScore * 0.7f + energyScore * 0.3f

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = profile.speakerId
                }
            }

            // Threshold: must be at least 50% confident
            if (bestScore < 0.5f) {
                "unknown" to bestScore
            } else {
                _voiceState.value = _voiceState.value.copy(speakerId = bestMatch)
                bestMatch to bestScore
            }
        } catch (_: Exception) {
            "unknown" to 0f
        }
    }

    /**
     * Create a new speaker profile from current audio.
     *
     * @param speakerName Optional display name
     * @return The new speaker ID
     */
    fun createNewSpeakerProfile(speakerName: String? = null): String {
        val newId = UUID.randomUUID().toString()
        _voiceState.value = _voiceState.value.copy(speakerId = newId)
        saveVoiceProfile(speakerId = newId, speakerName = speakerName)
        return newId
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. CONTINUOUS LISTENING MODE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Start continuous listening mode with smart pause detection.
     * Unlike wake-word mode, this continuously monitors for speech
     * and uses turn-taking logic to detect when the user is done.
     */
    fun startContinuousListening() {
        if (continuousListening) return
        continuousListening = true
        _voiceState.value = _voiceState.value.copy(
            isContinuousListening = true,
            turnState = TurnState.IDLE
        )

        continuousThread = Thread {
            try {
                continuousAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize.coerceAtLeast(8192)
                )
                if (continuousAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    continuousListening = false; return@Thread
                }

                continuousAudioRecord?.startRecording()
                val audioBuffer = ShortArray(bufferSize / 2)
                val rollingEnergy = FloatArray(50) { 0f }
                var rollingIdx = 0
                var silenceStart = 0L
                var wasSpeaking = false
                var speechBuffer = StringBuilder()
                var consecutiveVoiceFrames = 0
                var consecutiveSilenceFrames = 0

                while (continuousListening) {
                    val read = continuousAudioRecord?.read(
                        audioBuffer, 0, audioBuffer.size, AudioRecord.READ_NON_BLOCKING
                    ) ?: break

                    if (read > 0) {
                        val energy = calculateEnergy(audioBuffer, read)
                        val zcr = calculateZCR(audioBuffer, read)

                        rollingEnergy[rollingIdx] = energy
                        rollingIdx = (rollingIdx + 1) % rollingEnergy.size
                        val avgEnergy = rollingEnergy.average().toFloat()

                        val isVoice = energy > avgEnergy * 1.4f && zcr > 0.04f && zcr < 0.5f

                        // Update turn state
                        updateTurnState(energy, isVoice)

                        // Analyze noise and prosody in continuous mode
                        if (consecutiveVoiceFrames % 5 == 0) {
                            analyzeNoiseLevel(audioBuffer, read)
                        }
                        if (isVoice && consecutiveVoiceFrames % 3 == 0) {
                            updateProsodyFeatures(audioBuffer, read)
                        }

                        if (isVoice) {
                            consecutiveVoiceFrames++
                            consecutiveSilenceFrames = 0

                            if (!wasSpeaking) {
                                wasSpeaking = true
                                silenceStart = 0L
                            }
                        } else {
                            consecutiveSilenceFrames++
                            consecutiveVoiceFrames = 0

                            if (wasSpeaking && silenceStart == 0L) {
                                silenceStart = System.currentTimeMillis()
                            }

                            // Smart pause detection
                            if (wasSpeaking && silenceStart > 0L) {
                                val pauseMs = System.currentTimeMillis() - silenceStart
                                _voiceState.value = _voiceState.value.copy(
                                    silenceDurationMs = pauseMs
                                )

                                val hasFiller = speechBuffer.toString().let {
                                    NaturalConversationEngine(application)
                                        .detectFillerWords(it)
                                }

                                if (isTurnComplete(pauseMs, hasFiller, speechBuffer.toString())) {
                                    // Turn complete — notify
                                    Handler(Looper.getMainLooper()).post {
                                        onContinuousListeningResult?.invoke(
                                            speechBuffer.toString(),
                                            _voiceState.value.sttConfidence
                                        )
                                        onTurnComplete?.invoke(
                                            speechBuffer.toString(),
                                            pauseMs
                                        )
                                    }
                                    speechBuffer = StringBuilder()
                                    wasSpeaking = false
                                    silenceStart = 0L
                                    _voiceState.value = _voiceState.value.copy(
                                        turnState = TurnState.TURN_COMPLETE
                                    )
                                }
                            }

                            // Extended silence: reset completely
                            if (consecutiveSilenceFrames > 200) { // ~4 seconds
                                wasSpeaking = false
                                speechBuffer = StringBuilder()
                                _voiceState.value = _voiceState.value.copy(
                                    turnState = TurnState.IDLE,
                                    silenceDurationMs = 0L
                                )
                            }
                        }
                    }
                    try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                }
            } catch (_: Exception) {}
            finally {
                try { continuousAudioRecord?.stop(); continuousAudioRecord?.release() } catch (_: Exception) {}
                continuousAudioRecord = null; continuousListening = false
                _voiceState.value = _voiceState.value.copy(
                    isContinuousListening = false,
                    turnState = TurnState.IDLE
                )
            }
        }
        continuousThread?.name = "nexa-continuous-listening"
        continuousThread?.start()
    }

    /**
     * Stop continuous listening mode.
     */
    fun stopContinuousListening() {
        continuousListening = false
        _voiceState.value = _voiceState.value.copy(
            isContinuousListening = false,
            turnState = TurnState.IDLE
        )
        try { continuousThread?.interrupt() } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. PROSODY ANALYSIS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Update prosody features from audio analysis.
     * Tracks pitch contour, speech rate, rhythm, and stress patterns.
     */
    fun updateProsodyFeatures(audioBuffer: ShortArray, length: Int) {
        val pitch = estimatePitch(audioBuffer, length)
        val energy = calculateEnergy(audioBuffer, length)

        // Update pitch history for contour tracking
        pitchHistory[pitchHistoryIdx] = pitch
        pitchHistoryIdx = (pitchHistoryIdx + 1) % pitchHistory.size

        // Update energy history
        energyHistory[energyHistoryIdx] = energy
        energyHistoryIdx = (energyHistoryIdx + 1) % energyHistory.size

        // Pitch contour: linear regression slope of recent pitch values
        val pitchContour = calculateContourSlope(pitchHistory)

        // Pitch range: standard deviation of recent pitch values
        val pitchMean = pitchHistory.average().toFloat()
        val pitchRange = sqrt(
            pitchHistory.map { (it.toDouble() - pitchMean.toDouble()) * (it.toDouble() - pitchMean.toDouble()) }.average()
        ).toFloat()

        // Intensity contour
        val intensityContour = calculateContourSlope(energyHistory)

        // Estimate speech rate from energy peaks
        val energyMean = energyHistory.average().toFloat()
        val peaks = energyHistory.count { it > energyMean * 1.5f }
        val elapsedTime = (pitchHistory.size * 20f) / 1000f // Approximate time in seconds
        val speechRate = if (elapsedTime > 0f) peaks / elapsedTime else 0f

        // Rhythm regularity: coefficient of variation of energy peaks
        val peakEnergies = energyHistory.filter { it > energyMean * 1.5f }
        val rhythmRegularity = if (peakEnergies.size > 2) {
            val peakMean = peakEnergies.average().toFloat()
            val peakStd = sqrt(peakEnergies.map { (it.toDouble() - peakMean.toDouble()) * (it.toDouble() - peakMean.toDouble()) }.average()).toFloat()
            if (peakMean > 0f) 1f - (peakStd / peakMean).coerceIn(0f, 1f) else 0f
        } else 0.5f

        // Pause frequency and duration
        val pauseFrequency = if (elapsedTime > 0f) pauseCount / elapsedTime else 0f
        val avgPauseDuration = if (pauseCount > 0) totalPauseDuration.toFloat() / pauseCount else 0f

        // Stress pattern from combined pitch and energy
        val stressPattern = when {
            pitchContour > 50f && intensityContour > 500f -> "emphatic"
            pitchRange > 80f && speechRate > 5f -> "stressed"
            pitchRange < 30f && speechRate < 3f -> "relaxed"
            else -> "neutral"
        }

        val prosody = ProsodyFeatures(
            pitchContour = pitchContour,
            pitchRange = pitchRange,
            speechRate = speechRate,
            rhythmRegularity = rhythmRegularity,
            intensityContour = intensityContour,
            pauseFrequency = pauseFrequency,
            avgPauseDuration = avgPauseDuration,
            stressPattern = stressPattern
        )

        _voiceState.value = _voiceState.value.copy(prosodyFeatures = prosody)
    }

    /**
     * Enhanced emotion detection using prosody analysis for higher accuracy.
     * Combines traditional energy/pitch analysis with prosodic features.
     */
    fun analyzeVoiceEmotionWithProsody(audioBuffer: ShortArray, length: Int): Pair<String, Float> {
        // Get base emotion from traditional analysis
        val (baseEmotion, baseConfidence) = analyzeVoiceEmotion(audioBuffer, length)

        // Get current prosody
        updateProsodyFeatures(audioBuffer, length)
        val prosody = _voiceState.value.prosodyFeatures

        // Refine emotion using prosody
        val refinedEmotion = when {
            // Joy: rising pitch + fast rate + emphatic stress
            baseEmotion == "joy" && prosody.pitchContour > 20f && prosody.speechRate > 4f -> "joy"
            baseEmotion == "joy" && prosody.stressPattern == "relaxed" -> "contentment"

            // Anger: falling pitch + stressed + high intensity
            baseEmotion == "anger" && prosody.pitchContour < -20f && prosody.stressPattern == "stressed" -> "anger"
            baseEmotion == "anger" && prosody.stressPattern == "emphatic" -> "frustration"

            // Sadness: falling pitch + slow rate + relaxed
            baseEmotion == "sadness" && prosody.speechRate < 2f && prosody.pitchContour < -10f -> "sadness"
            baseEmotion == "sadness" && prosody.stressPattern == "relaxed" -> "melancholy"

            // Fear: rising pitch + irregular rhythm + fast rate
            baseEmotion == "fear" && prosody.rhythmRegularity < 0.4f -> "anxiety"
            baseEmotion == "fear" && prosody.pitchContour > 30f -> "panic"

            // Surprise: high pitch range + emphatic
            baseEmotion == "surprise" && prosody.pitchRange > 100f -> "surprise"
            baseEmotion == "surprise" && prosody.stressPattern == "emphatic" -> "amazement"

            // Excitement: fast + rising + emphatic
            baseEmotion == "excitement" && prosody.speechRate > 5f -> "enthusiasm"
            baseEmotion == "excitement" && prosody.pitchContour > 30f -> "thrill"

            else -> baseEmotion
        }

        // Refine confidence using prosody consistency
        val prosodyConfidenceBoost = when {
            prosody.stressPattern != "neutral" -> 0.1f
            prosody.rhythmRegularity > 0.6f -> 0.05f
            prosody.rhythmRegularity < 0.3f -> -0.1f
            else -> 0f
        }

        val refinedConfidence = (baseConfidence + prosodyConfidenceBoost).coerceIn(0f, 1f)

        _voiceState.value = _voiceState.value.copy(
            voiceEmotion = refinedEmotion,
            voiceEmotionConfidence = refinedConfidence
        )

        onVoiceEmotionChanged?.invoke(refinedEmotion, refinedConfidence)

        // Save emotion with prosody context
        scope.launch {
            try {
                db.emotionDao().insert(EmotionEntity(
                    primaryEmotion = refinedEmotion,
                    secondaryEmotion = if (refinedEmotion != baseEmotion) baseEmotion else null,
                    intensity = (_voiceState.value.voiceEnergy / 5000f).coerceIn(0f, 1f),
                    confidence = refinedConfidence,
                    source = "voice_prosody",
                    context = "prosody_stress=${prosody.stressPattern}_rate=${prosody.speechRate}"
                ))
            } catch (_: Exception) {}
        }

        return refinedEmotion to refinedConfidence
    }

    /**
     * Calculate linear regression slope for contour tracking.
     */
    private fun calculateContourSlope(history: FloatArray): Float {
        val n = history.size
        if (n < 2) return 0f

        var sumX = 0f; var sumY = 0f; var sumXY = 0f; var sumX2 = 0f
        for (i in 0 until n) {
            val x = i.toFloat()
            val y = history[i]
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x
        }

        val denominator = n * sumX2 - sumX * sumX
        return if (abs(denominator) > 0.001f) (n * sumXY - sumX * sumY) / denominator else 0f
    }

    // ═══════════════════════════════════════════════════════════════════
    //  7. NOISE CANCELLATION FEEDBACK
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze ambient noise level and provide feedback to the user.
     *
     * @param audioBuffer Raw audio data
     * @param length Number of valid samples
     * @return NoiseFeedback with level, approximate dB, and suggestions
     */
    fun analyzeNoiseLevel(audioBuffer: ShortArray, length: Int): NoiseFeedback {
        val energy = calculateEnergy(audioBuffer, length)
        val zcr = calculateZCR(audioBuffer, length)

        // Approximate dB SPL (very rough estimate from RMS)
        val approxDb = if (energy > 0f) {
            20f * kotlin.math.log10((energy / 32768f).coerceAtLeast(0.0001f)) + 94f
        } else 0f

        // Noise classification
        val noiseLevel = when {
            approxDb < 35f -> "quiet"
            approxDb < 50f -> "moderate"
            approxDb < 65f -> "loud"
            else -> "very_loud"
        }

        // Check for specific noise patterns
        val isWindNoise = zcr > 0.45f && energy > 1000f
        val isStaticNoise = zcr > 0.4f && energy in 500f..2000f
        val isBackgroundTalk = energy > 2000f && zcr in 0.1f..0.25f

        val isTooNoisyForSTT = approxDb > 60f || isWindNoise || isBackgroundTalk

        val message = when {
            isWindNoise -> "Viento detectado en el micrófono"
            isStaticNoise -> "Interferencia de audio detectada"
            isBackgroundTalk -> "Conversación de fondo detectada"
            noiseLevel == "very_loud" -> "Ambiente muy ruidoso"
            noiseLevel == "loud" -> "Ambiente ruidoso"
            else -> ""
        }

        val suggestion = when {
            isWindNoise -> "Cubre el micrófono o muévete a un lugar protegido"
            isBackgroundTalk -> "Busca un lugar más silencioso para mejor reconocimiento"
            noiseLevel == "very_loud" -> "El reconocimiento de voz puede fallar. Busca un lugar más silencioso"
            noiseLevel == "loud" -> "Speak closer to the microphone for better results"
            else -> ""
        }

        val feedback = NoiseFeedback(
            noiseLevel = noiseLevel,
            approxDb = approxDb,
            message = message,
            suggestion = suggestion,
            isTooNoisyForSTT = isTooNoisyForSTT
        )

        _voiceState.value = _voiceState.value.copy(
            noiseLevel = noiseLevel,
            noiseLevelDb = approxDb,
            isTooNoisy = isTooNoisyForSTT
        )

        // Only trigger callback when noise is problematic
        if (isTooNoisyForSTT || noiseLevel == "loud") {
            Handler(Looper.getMainLooper()).post { onNoiseFeedback?.invoke(feedback) }
        }

        return feedback
    }

    /**
     * Check if the current environment is suitable for voice recognition.
     */
    fun isEnvironmentSuitable(): Boolean {
        return !_voiceState.value.isTooNoisy
    }

    /**
     * Get the current noise level as a human-readable string.
     */
    fun getNoiseDescription(): String {
        val state = _voiceState.value
        return when (state.noiseLevel) {
            "quiet" -> "Silencio (${state.noiseLevelDb.toInt()} dB)"
            "moderate" -> "Ruido moderado (${state.noiseLevelDb.toInt()} dB)"
            "loud" -> "Ruidoso (${state.noiseLevelDb.toInt()} dB)"
            "very_loud" -> "Muy ruidoso (${state.noiseLevelDb.toInt()} dB)"
            else -> "Desconocido"
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  8. IoT VOICE COMMAND INTEGRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect if the recognized text contains an IoT voice command.
     * Triggers the onIoTVoiceCommand callback if a command is found.
     *
     * @param text The recognized speech text
     * @return true if an IoT command was detected
     */
    fun detectIoTCommand(text: String): Boolean {
        val lower = text.lowercase()
        val detected = iotCommandKeywords.any { lower.contains(it) }

        if (detected) {
            Handler(Looper.getMainLooper()).post { onIoTVoiceCommand?.invoke(text) }
        }
        return detected
    }

    /**
     * Extract structured IoT command data from recognized text.
     *
     * @param text The recognized speech text
     * @return IoTCommandData or null if not a valid IoT command
     */
    fun extractIoTCommandData(text: String): IoTCommandData? {
        val lower = text.lowercase()

        // Determine action
        val action = when {
            lower.contains("enciende") || lower.contains("prende") || lower.contains("activa") ||
                    lower.contains("turn on") || lower.contains("brighten") -> "on"
            lower.contains("apaga") || lower.contains("desactiva") ||
                    lower.contains("turn off") || lower.contains("dim") -> "off"
            lower.contains("sube") || lower.contains("aumenta") || lower.contains("volume up") -> "increase"
            lower.contains("baja") || lower.contains("disminuye") || lower.contains("volume down") -> "decrease"
            lower.contains("bloquea") || lower.contains("cierra") || lower.contains("lock") -> "lock"
            lower.contains("desbloquea") || lower.contains("abre") || lower.contains("unlock") -> "unlock"
            else -> null
        } ?: return null

        // Determine device type
        val deviceType = when {
            lower.contains("luz") || lower.contains("luces") || lower.contains("lámpara") ||
                    lower.contains("light") -> "light"
            lower.contains("aire") || lower.contains("termostato") || lower.contains("calefacción") ||
                    lower.contains("thermostat") -> "thermostat"
            lower.contains("puerta") || lower.contains("cerradura") || lower.contains("lock") -> "lock"
            lower.contains("altavoz") || lower.contains("bocina") || lower.contains("speaker") -> "speaker"
            lower.contains("volumen") || lower.contains("volume") -> "speaker"
            else -> null
        }

        // Extract temperature value
        val tempValue = Regex("(\\d+)\\s*grado").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("temperature.*?(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()

        // Extract device name
        val deviceName = Regex("(?:del|de la|de el)\\s+(\\w+)").find(lower)?.groupValues?.get(1)

        return IoTCommandData(
            rawText = text,
            action = action,
            deviceType = deviceType,
            deviceName = deviceName,
            temperatureValue = tempValue,
            confidence = if (deviceType != null) 0.9f else 0.6f
        )
    }

    /**
     * Structured IoT command data.
     */
    data class IoTCommandData(
        val rawText: String,
        val action: String,            // "on", "off", "increase", "decrease", "lock", "unlock"
        val deviceType: String?,       // "light", "thermostat", "lock", "speaker"
        val deviceName: String?,       // Specific device name
        val temperatureValue: Int? = null,
        val confidence: Float = 0f
    )

    // ═══════════════════════════════════════════════════════════════════
    //  ORIGINAL FEATURES (preserved and improved)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Start listening for wake word "Hey NEXA" / "Oye NEXA"
     */
    fun startWakeWordDetection() {
        if (wakeWordListening) return
        wakeWordListening = true
        _voiceState.value = _voiceState.value.copy(isWakeWordListening = true)

        wakeWordThread = Thread {
            try {
                wakeWordAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize.coerceAtLeast(4096)
                )
                if (wakeWordAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    wakeWordListening = false; return@Thread
                }

                wakeWordAudioRecord?.startRecording()
                val audioBuffer = ShortArray(bufferSize / 2)
                val energyHistory = FloatArray(30) { 0f }
                var energyIdx = 0
                var silenceFrames = 0
                var speechFrames = 0
                var lastDetectionTime = 0L

                while (wakeWordListening) {
                    val read = wakeWordAudioRecord?.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_NON_BLOCKING) ?: break
                    if (read > 0) {
                        var sumSquares = 0L
                        for (i in 0 until read) { sumSquares += audioBuffer[i].toLong() * audioBuffer[i].toLong() }
                        val rms = sqrt(sumSquares.toDouble() / read).toFloat()

                        var crossings = 0
                        for (i in 1 until read) {
                            if ((audioBuffer[i] >= 0 && audioBuffer[i - 1] < 0) || (audioBuffer[i] < 0 && audioBuffer[i - 1] >= 0)) crossings++
                        }
                        val zcr = crossings.toFloat() / read

                        energyHistory[energyIdx] = rms; energyIdx = (energyIdx + 1) % energyHistory.size
                        val avgEnergy = energyHistory.average().toFloat()
                        val isVoice = rms > avgEnergy * 1.5f && zcr > 0.05f && zcr < 0.5f

                        if (isVoice) { speechFrames++; silenceFrames = 0 } else {
                            silenceFrames++; if (silenceFrames > 10) speechFrames = 0
                        }

                        // Analyze noise during wake word listening
                        if (silenceFrames % 10 == 0) {
                            analyzeNoiseLevel(audioBuffer, read)
                        }

                        val now = System.currentTimeMillis()
                        if (speechFrames in 5..20 && isVoice && now - lastDetectionTime > 5000L && rms > 2000f) {
                            val features = extractSpectralFeatures(audioBuffer, read)
                            val midEnergy = features.slice(2..5).sum()
                            val totalEnergy = features.sum() + 0.001f
                            if (midEnergy / totalEnergy > 0.3f) {
                                lastDetectionTime = now
                                _voiceState.value = _voiceState.value.copy(wakeWordDetected = true, lastWakeWordTime = now)
                                Handler(Looper.getMainLooper()).post { onWakeWordDetected?.invoke() }
                                speechFrames = 0

                                // Also attempt language detection on wake word audio
                                detectLanguage(audioBuffer, read)

                                // Save voice profile sample
                                saveVoiceProfile()
                            }
                        }
                    }
                    try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                }
            } catch (_: Exception) {}
            finally {
                try { wakeWordAudioRecord?.stop(); wakeWordAudioRecord?.release() } catch (_: Exception) {}
                wakeWordAudioRecord = null; wakeWordListening = false
                _voiceState.value = _voiceState.value.copy(isWakeWordListening = false)
            }
        }
        wakeWordThread?.name = "nexa-wake-word"
        wakeWordThread?.start()
    }

    fun stopWakeWordDetection() {
        wakeWordListening = false
        _voiceState.value = _voiceState.value.copy(isWakeWordListening = false)
        try { wakeWordThread?.interrupt() } catch (_: Exception) {}
    }

    /**
     * Analyze emotion from voice audio characteristics.
     * (Original method preserved for compatibility)
     */
    fun analyzeVoiceEmotion(audioBuffer: ShortArray, length: Int): Pair<String, Float> {
        val energy = calculateEnergy(audioBuffer, length)
        val pitch = estimatePitch(audioBuffer, length)
        val zcr = calculateZCR(audioBuffer, length)

        _voiceState.value = _voiceState.value.copy(voiceEnergy = energy, voicePitch = pitch)

        val emotion = when {
            energy > 3000f && pitch > 200f -> "joy"
            energy > 4000f && pitch < 150f -> "anger"
            energy < 1000f && pitch < 120f -> "sadness"
            energy < 800f && pitch > 180f -> "fear"
            zcr > 0.3f && energy > 2000f -> "surprise"
            energy > 2000f -> "excitement"
            energy in 800f..2000f && pitch in 120f..180f -> "neutral"
            else -> "neutral"
        }

        val confidence = when (emotion) { "neutral" -> 0.5f; "joy", "anger" -> 0.7f; "sadness" -> 0.6f; else -> 0.5f }
        _voiceState.value = _voiceState.value.copy(voiceEmotion = emotion, voiceEmotionConfidence = confidence)

        scope.launch {
            try {
                db.emotionDao().insert(EmotionEntity(
                    primaryEmotion = emotion, intensity = (energy / 5000f).coerceIn(0f, 1f),
                    confidence = confidence, source = "voice", context = "realtime_analysis"
                ))
            } catch (_: Exception) {}
        }

        onVoiceEmotionChanged?.invoke(emotion, confidence)
        return Pair(emotion, confidence)
    }

    fun getEmotionalSSML(emotion: String, text: String): String = when (emotion) {
        "joy", "excitement", "enthusiasm", "thrill" -> "<speak><prosody rate=\"fast\" pitch=\"+10%\" volume=\"loud\">$text</prosody></speak>"
        "sadness", "melancholy" -> "<speak><prosody rate=\"slow\" pitch=\"-10%\" volume=\"soft\">$text</prosody></speak>"
        "anger", "frustration" -> "<speak><prosody rate=\"medium\" pitch=\"-5%\" volume=\"loud\">$text</prosody></speak>"
        "fear", "anxiety", "panic" -> "<speak><prosody rate=\"slow\" pitch=\"+5%\" volume=\"medium\">$text</prosody></speak>"
        "contentment" -> "<speak><prosody rate=\"medium\" pitch=\"+3%\" volume=\"medium\">$text</prosody></speak>"
        "amazement", "surprise" -> "<speak><prosody rate=\"fast\" pitch=\"+15%\" volume=\"loud\">$text</prosody></speak>"
        else -> text
    }

    fun analyzeAudioQuality(audioBuffer: ShortArray, length: Int): String {
        val maxSample = audioBuffer.take(length).maxByOrNull { abs(it.toInt()) }?.toInt() ?: 0
        if (maxSample > 32000) { _voiceState.value = _voiceState.value.copy(voiceQuality = "clipping"); return "clipping" }
        val energy = calculateEnergy(audioBuffer, length)
        val zcr = calculateZCR(audioBuffer, length)
        if (energy > 1000f && zcr > 0.4f) { _voiceState.value = _voiceState.value.copy(voiceQuality = "noisy"); return "noisy" }
        if (energy < 500f) { _voiceState.value = _voiceState.value.copy(voiceQuality = "muffled"); return "muffled" }
        _voiceState.value = _voiceState.value.copy(voiceQuality = "clear")
        return "clear"
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AUDIO ANALYSIS HELPERS (original + new)
    // ═══════════════════════════════════════════════════════════════════

    private fun calculateEnergy(buffer: ShortArray, length: Int): Float {
        var sumSquares = 0L
        for (i in 0 until minOf(length, buffer.size)) { sumSquares += buffer[i].toLong() * buffer[i].toLong() }
        return sqrt(sumSquares.toDouble() / length).toFloat()
    }

    private fun calculateZCR(buffer: ShortArray, length: Int): Float {
        var crossings = 0; val len = minOf(length, buffer.size)
        for (i in 1 until len) { if ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0)) crossings++ }
        return crossings.toFloat() / len
    }

    private fun estimatePitch(buffer: ShortArray, length: Int): Float {
        val len = minOf(length, buffer.size, 1024)
        var maxCorrelation = 0f; var bestLag = 0
        val minLag = sampleRate / 400; val maxLag = sampleRate / 80
        for (lag in minLag..minOf(maxLag, len / 2)) {
            var correlation = 0f
            for (i in 0 until len - lag) { correlation += buffer[i] * buffer[i + lag] }
            if (correlation > maxCorrelation) { maxCorrelation = correlation; bestLag = lag }
        }
        return if (bestLag > 0) sampleRate.toFloat() / bestLag else 0f
    }

    private fun extractSpectralFeatures(buffer: ShortArray, length: Int): FloatArray {
        val features = FloatArray(13)
        val len = minOf(length, buffer.size, 512)
        for (i in 0 until 13) {
            var real = 0f; var imag = 0f; val bin = i * 2
            for (n in 0 until len) {
                val angle = 2f * Math.PI * bin * n / len
                real += buffer[n] * Math.cos(angle).toFloat()
                imag -= buffer[n] * Math.sin(angle).toFloat()
            }
            features[i] = sqrt(real * real + imag * imag) / len
        }
        return features
    }

    /**
     * Generate comprehensive voice context string for AI system prompts.
     * Now includes language, noise, prosody, and turn-taking info.
     */
    fun getVoiceContextForAI(): String {
        val state = _voiceState.value
        val parts = mutableListOf<String>()

        // Emotion
        if (state.voiceEmotion != "neutral") {
            val emotionMap = mapOf(
                "joy" to "alegría", "sadness" to "tristeza", "anger" to "enojo",
                "fear" to "miedo", "surprise" to "sorpresa", "excitement" to "emoción",
                "frustration" to "frustración", "anxiety" to "ansiedad", "panic" to "pánico",
                "contentment" to "contentamiento", "melancholy" to "melancolía",
                "enthusiasm" to "entusiasmo", "amazement" to "asombro", "thrill" to "emoción intensa"
            )
            parts.add("Voz: emoción = ${emotionMap[state.voiceEmotion] ?: state.voiceEmotion} (${(state.voiceEmotionConfidence * 100).toInt()}%)")
        }

        // Language
        if (state.detectedLanguage != "unknown") {
            val langName = when (state.detectedLanguage) {
                "es" -> "español"; "en" -> "inglés"; "mixed" -> "mezclado"; else -> state.detectedLanguage
            }
            parts.add("Idioma detectado: $langName (${(state.languageConfidence * 100).toInt()}%)")
        }

        // Audio quality / noise
        when (state.voiceQuality) {
            "noisy" -> parts.add("Noisy audio. Speak more clearly.")
            "muffled" -> parts.add("Audio muted.")
            "clipping" -> parts.add("Audio saturated.")
        }
        if (state.isTooNoisy) {
            parts.add("⚠️ Ambiente muy ruidoso — reconocimiento puede fallar.")
        }

        // Prosody
        val prosody = state.prosodyFeatures
        if (prosody.stressPattern != "neutral") {
            val stressMap = mapOf("stressed" to "estresado", "relaxed" to "relajado", "emphatic" to "enfático")
            parts.add("Patrón de voz: ${stressMap[prosody.stressPattern] ?: prosody.stressPattern}")
        }

        // Turn state
        if (state.turnState != TurnState.IDLE) {
            val turnMap = mapOf(
                TurnState.USER_SPEAKING to "hablando",
                TurnState.USER_PAUSING to "pausando",
                TurnState.TURN_COMPLETE to "turno completado",
                TurnState.PROCESSING to "processing"
            )
            parts.add("Estado de turno: ${turnMap[state.turnState] ?: state.turnState.name}")
        }

        // Speaker
        if (state.speakerId != "unknown") {
            parts.add("Speaker: ${state.speakerId}")
        }

        return parts.joinToString(" ")
    }

    /**
     * Stop all listening modes and clean up resources.
     */
    fun shutdown() {
        stopWakeWordDetection()
        stopContinuousListening()
        onWakeWordDetected = null
        onVoiceEmotionChanged = null
        onTurnComplete = null
        onNoiseFeedback = null
        onIoTVoiceCommand = null
        onLanguageDetected = null
        onSTTConfidenceUpdate = null
        onContinuousListeningResult = null
    }
}
