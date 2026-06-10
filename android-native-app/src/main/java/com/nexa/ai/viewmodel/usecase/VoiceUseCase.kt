package com.nexa.ai.viewmodel.usecase

import com.nexa.ai.voice.NaturalConversationEngine
import com.nexa.ai.voice.SpeechManager
import com.nexa.ai.voice.VoiceEnhancer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceUseCase — Wraps VoiceEnhancer and NaturalConversationEngine functionality.
 *
 * Provides a unified interface for the ViewModel to:
 * - Start/stop voice sessions (wake word + speech recognition)
 * - Process voice input through the conversation engine
 * - Track voice state (listening, processing, speaking, idle)
 * - Retrieve conversation context for AI prompts
 *
 * This use case is Hilt-injectable and manages the lifecycle of voice
 * subsystems independently from the ViewModel.
 */
@Singleton
class VoiceUseCase @Inject constructor(
    private val voiceEnhancer: VoiceEnhancer,
    private val conversationEngine: NaturalConversationEngine,
    private val speechManager: SpeechManager
) {

    // ─── Voice State ────────────────────────────

    enum class VoiceSessionState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING
    }

    data class VoiceUiState(
        val sessionState: VoiceSessionState = VoiceSessionState.IDLE,
        val isWakeWordActive: Boolean = false,
        val detectedEmotion: String = "neutral",
        val detectedEmotionConfidence: Float = 0f,
        val detectedLanguage: String = "unknown",
        val languageConfidence: Float = 0f,
        val conversationTopic: String = "",
        val turnCount: Int = 0,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ─── Session Control ────────────────────────

    /**
     * Start a voice session: activates SpeechManager listening
     * and VoiceEnhancer wake word detection.
     */
    fun startVoiceSession() {
        // FIX: Do NOT start wake word detection here — it opens a separate AudioRecord
        // that competes with SpeechRecognizer for the microphone, causing "not connected" errors.
        // Wake word should only be used in background/standby mode, not during active voice sessions.
        // voiceEnhancer.startWakeWordDetection()  // DISABLED: causes mic conflict

        // Track that voice session is active (for conversation context)
        _state.update { it.copy(isWakeWordActive = false, sessionState = VoiceSessionState.LISTENING) }

        // Wire voice enhancer callbacks if not already done
        voiceEnhancer.onWakeWordDetected = {
            _state.update { it.copy(sessionState = VoiceSessionState.LISTENING) }
        }

        voiceEnhancer.onVoiceEmotionChanged = { emotion, confidence ->
            _state.update { it.copy(detectedEmotion = emotion, detectedEmotionConfidence = confidence) }
        }

        voiceEnhancer.onLanguageDetected = { language, confidence ->
            _state.update { it.copy(detectedLanguage = language, languageConfidence = confidence) }
        }

        voiceEnhancer.onTurnComplete = { _, _ ->
            _state.update { it.copy(sessionState = VoiceSessionState.PROCESSING) }
        }

        android.util.Log.d("VoiceUseCase", "Voice session started — wake word DISABLED to prevent mic conflict")
    }

    /**
     * Stop a voice session: stops all voice subsystems.
     */
    fun stopVoiceSession() {
        voiceEnhancer.stopWakeWordDetection()
        voiceEnhancer.stopContinuousListening()
        _state.update {
            it.copy(
                sessionState = VoiceSessionState.IDLE,
                isWakeWordActive = false
            )
        }
        android.util.Log.d("VoiceUseCase", "Voice session stopped")
    }

    // ─── Voice Input Processing ─────────────────

    /**
     * Process voice input through the conversation engine.
     * Updates conversation context, tracks topics, and manages turn state.
     *
     * @param text The recognized voice input text
     */
    fun processVoiceInput(text: String) {
        scope.launch {
            try {
                // Track topic from user input
                val topic = conversationEngine.trackTopic(text)

                // Detect filler words for turn-taking
                val hasFiller = conversationEngine.detectFillerWords(text)

                // Analyze turn completeness
                val turnDecision = conversationEngine.analyzeTurn(text, 0L)

                _state.update {
                    it.copy(
                        conversationTopic = topic,
                        turnCount = it.turnCount + 1,
                        sessionState = if (turnDecision.isComplete) {
                            VoiceSessionState.PROCESSING
                        } else {
                            VoiceSessionState.LISTENING
                        }
                    )
                }

                android.util.Log.d("VoiceUseCase", "Processed voice input — topic: $topic, turnComplete: ${turnDecision.isComplete}")
            } catch (e: Exception) {
                android.util.Log.e("VoiceUseCase", "Error processing voice input: ${e.message}", e)
                _state.update { it.copy(lastError = e.message) }
            }
        }
    }

    /**
     * Get current conversation context from NaturalConversationEngine.
     * Used to enrich the AI system prompt with conversation awareness.
     *
     * @return A formatted string with conversation context, or empty string if none
     */
    fun getConversationContext(): String {
        val ctx = conversationEngine.context.value
        val parts = mutableListOf<String>()

        if (ctx.currentTopic.isNotEmpty()) {
            parts.add("TOPIC: ${ctx.currentTopic}")
        }

        if (ctx.lastUserIntent.isNotEmpty()) {
            parts.add("INTENT: ${ctx.lastUserIntent}")
        }

        if (ctx.conversationMood != "neutral") {
            parts.add("MOOD: ${ctx.conversationMood}")
        }

        if (ctx.conversationPhase != NaturalConversationEngine.ConversationPhase.OPENING) {
            parts.add("PHASE: ${ctx.conversationPhase.name}")
        }

        if (ctx.turnCount > 0) {
            parts.add("TURN: ${ctx.turnCount}")
        }

        val emotion = _state.value.detectedEmotion
        if (emotion != "neutral") {
            val guidelines = conversationEngine.getEmpatheticGuidelines(emotion)
            parts.add("EMPATHY: $guidelines")
        }

        return if (parts.isNotEmpty()) {
            "\nVOICE_CTX: ${parts.joinToString(" | ")}"
        } else ""
    }

    /**
     * Update conversation context after an AI response.
     * Should be called after each assistant message to maintain
     * conversation flow tracking.
     *
     * @param userMessage The user's message
     * @param aiResponse The AI's response
     * @param emotion The detected emotion (default: "neutral")
     */
    fun updateConversationAfterResponse(userMessage: String, aiResponse: String, emotion: String = "neutral") {
        scope.launch {
            try {
                conversationEngine.updateContext(userMessage, aiResponse, emotion)

                val ctx = conversationEngine.context.value
                _state.update {
                    it.copy(
                        conversationTopic = ctx.currentTopic,
                        turnCount = ctx.turnCount
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceUseCase", "Error updating conversation context: ${e.message}", e)
            }
        }
    }

    /**
     * Generate a backchannel response if appropriate for the current conversation state.
     * Used during voice mode to acknowledge the user without full AI processing.
     *
     * @return A backchannel response, or null if not appropriate
     */
    fun maybeGenerateBackchannel(): String? {
        val ctx = conversationEngine.context.value
        val timeSinceLastBackchannel = System.currentTimeMillis() - ctx.lastBackchannelTime

        return if (timeSinceLastBackchannel > 3000L) {
            val response = conversationEngine.generateBackchannel()
            response.text
        } else null
    }

    /**
     * Reset voice state to idle. Called when voice mode is deactivated.
     */
    fun resetState() {
        _state.update { VoiceUiState() }
    }
}
