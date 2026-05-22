package com.nexa.ai.voice

import android.app.Application
import com.nexa.ai.data.local.NexaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * NEXA Natural Conversation Engine — Diálogo natural y contextual
 *
 * Makes conversations feel natural and human-like with:
 * - Conversational context tracking (what was discussed, what's relevant)
 * - Filler word detection (uhm, eh, hmm) to not cut off user mid-thought
 * - Turn-taking logic (knows when user is done speaking vs pausing)
 * - Backchanneling (acknowledges with "mhm", "entiendo" during listening)
 * - Topic continuation (remembers what you were talking about)
 * - Clarification requests (asks "¿te refieres a...?" when uncertain)
 * - Empathetic response adaptation based on voice emotion
 * - Multi-turn conversation memory with persistence
 * - Language-aware responses (adapts to Spanish/English)
 * - Conversation flow state machine for natural dialogue
 * - Sentiment trajectory tracking (improving, declining, stable mood)
 */
class NaturalConversationEngine(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { NexaDatabase.getInstance(application) }

    // ═══════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Full conversation context tracking state.
     */
    data class ConversationContext(
        val currentTopic: String = "",
        val topicHistory: List<String> = emptyList(),
        val lastUserIntent: String = "",
        val pendingClarification: String? = null,
        val turnCount: Int = 0,
        val userPauses: Int = 0,
        val lastBackchannelTime: Long = 0L,
        val conversationMood: String = "neutral",
        val isUserFinished: Boolean = true,
        val fillerWordsDetected: Int = 0,
        val lastTopicChangeTime: Long = 0L,
        // Enhanced fields
        val conversationPhase: ConversationPhase = ConversationPhase.OPENING,
        val detectedLanguage: String = "unknown",
        val sentimentTrajectory: SentimentTrajectory = SentimentTrajectory.STABLE,
        val lastUserMessage: String = "",
        val lastAIResponse: String = "",
        val conversationId: String = "",
        val startedAt: Long = System.currentTimeMillis(),
        val lastTurnTime: Long = 0L,
        val repeatedTopicCount: Int = 0,
        val unresolvedQuestions: List<String> = emptyList(),
        val userEngagementLevel: Float = 0.5f,
        val needsTopicShift: Boolean = false,
        val lastClarificationTime: Long = 0L
    )

    /**
     * Conversation phase — tracks the natural flow of dialogue.
     */
    enum class ConversationPhase {
        OPENING,        // Initial greeting, establishing rapport
        EXPLORING,      // User is exploring a topic
        DEEP_DIVE,      // Deep conversation on a specific topic
        CLARIFYING,     // System is asking for clarification
        RESOLVING,      // Wrapping up a topic or question
        CLOSING         // Ending the conversation
    }

    /**
     * Sentiment trajectory — tracks how the conversation mood is evolving.
     */
    enum class SentimentTrajectory {
        IMPROVING,      // Mood is getting better
        DECLINING,      // Mood is getting worse
        STABLE,         // Mood is consistent
        VOLATILE,       // Mood is fluctuating rapidly
        UNKNOWN         // Not enough data
    }

    /**
     * Turn-taking decision result with reasoning.
     */
    data class TurnDecision(
        val isComplete: Boolean,
        val confidence: Float,
        val reason: String,
        val suggestedAction: SuggestedAction
    )

    /**
     * Suggested action after turn analysis.
     */
    enum class SuggestedAction {
        WAIT,               // User is still thinking, don't respond yet
        BACKCHANNEL,        // Send a small acknowledgment
        RESPOND,            // User is done, generate full response
        CLARIFY,            // Ask for clarification
        CHANGE_TOPIC,       // Suggest topic change (conversation stalling)
        END_CONVERSATION    // User seems done with conversation
    }

    /**
     * Backchannel response with context awareness.
     */
    data class BackchannelResponse(
        val text: String,
        val type: BackchannelType,
        val appropriateConfidence: Float
    )

    /**
     * Types of backchannel responses.
     */
    enum class BackchannelType {
        ACKNOWLEDGMENT,     // "Mhm", "Sí" — simple acknowledgment
        UNDERSTANDING,      // "Entiendo", "I see" — shows comprehension
        AGREEMENT,          // "Claro", "Right" — agrees with user
        ENCOURAGEMENT,      // "Sigue", "Go on" — encourages continuation
        EMPATHY             // "Ay", "Oh no" — empathetic reaction
    }

    /**
     * Clarification request with structured data.
     */
    data class ClarificationRequest(
        val question: String,
        val ambiguousPart: String,
        val possibleInterpretations: List<String>,
        val urgency: ClarificationUrgency
    )

    /**
     * How urgently clarification is needed.
     */
    enum class ClarificationUrgency {
        LOW,        // Minor ambiguity, can infer from context
        MEDIUM,     // Moderate ambiguity, should ask
        HIGH        // Cannot proceed without clarification
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STATE & PROPERTIES
    // ═══════════════════════════════════════════════════════════════════

    private val _context = MutableStateFlow(ConversationContext())
    val context: StateFlow<ConversationContext> = _context.asStateFlow()

    // Callbacks
    var onClarificationNeeded: ((ClarificationRequest) -> Unit)? = null
    var onBackchannelSuggested: ((BackchannelResponse) -> Unit)? = null
    var onTopicChanged: ((String, String) -> Unit)? = null  // (oldTopic, newTopic)
    var onConversationPhaseChanged: ((ConversationPhase, ConversationPhase) -> Unit)? = null
    var onEngagementChanged: ((Float) -> Unit)? = null

    // Filler words in Spanish and English
    private val fillerWords = setOf(
        "eh", "um", "uh", "ah", "em", "este", "o sea", "bueno", "pues",
        "like", "you know", "well", "I mean", "hmm", "mm", "ehh", "umm",
        "mhm", "ahh", "pues", "entonces", "verdad", "right", "so",
        "basically", "literalmente", "digamos", "como decir"
    )

    // Topic keywords mapping — comprehensive multi-language
    private val topicMap = mapOf(
        "technology" to listOf(
            "código", "programa", "app", "software", "api", "code", "program",
            "computer", "telefono", "celular", "phone", "internet", "wifi",
            "datos", "data", "base de datos", "database", "servidor", "server",
            "inteligencia artificial", "ai", "ia", "algoritmo", "algorithm",
            "robot", "automatizar", "automate", "desarrollo", "development"
        ),
        "travel" to listOf(
            "vuelo", "viaje", "avión", "hotel", "flight", "travel", "trip",
            "ticket", "boleto", "pasaporte", "passport", "maleta", "luggage",
            "aeropuerto", "airport", "reservación", "reservation", "vacaciones",
            "vacation", "destino", "destination", "turista", "tourist"
        ),
        "health" to listOf(
            "salud", "ejercicio", "doctor", "health", "exercise", "doctor",
            "medicina", "medicine", "dolor", "pain", "enfermedad", "disease",
            "hospital", "clínica", "clinic", "receta", "prescription",
            "síntoma", "symptom", "dieta", "diet", "nutrición", "nutrition",
            "cansancio", "fatiga", "fatigue", "estrés", "stress", "dormir", "sleep"
        ),
        "food" to listOf(
            "comida", "receta", "restaurante", "food", "recipe", "restaurant",
            "cocinar", "cook", "ingrediente", "ingredient", "cena", "dinner",
            "almuerzo", "lunch", "desayuno", "breakfast", "postre", "dessert",
            "sabor", "flavor", "dieta", "menu", "menú", "hambre", "hungry",
            "horno", "oven", "sartén", "pan", "carne", "verdura", "fruta"
        ),
        "iot" to listOf(
            "luz", "temperatura", "casa", "light", "temperature", "home",
            "device", "dispositivo", "encender", "apagar", "smart", "inteligente",
            "cerradura", "lock", "termostato", "thermostat", "sensor",
            "automatización", "automation", "habitación", "room", "seguridad", "security"
        ),
        "emotion" to listOf(
            "siento", "feliz", "triste", "feel", "happy", "sad", "angry",
            "enojado", "enojada", "preocupado", "preocupada", "worried",
            "ansioso", "ansiosa", "anxious", "emocionado", "emocionada",
            "excited", "frustrado", "frustrada", "frustrated", "cansado",
            "cansada", "tired", "aburrido", "aburrida", "bored", "agradecido",
            "grateful", "orgulloso", "orgullosa", "proud", "miedo", "scared",
            "nostálgico", "nostálgica", "nostalgic", "amor", "love", "odio", "hate"
        ),
        "finance" to listOf(
            "dinero", "precio", "crypto", "money", "price", "stock",
            "invertir", "invest", "ahorro", "savings", "presupuesto", "budget",
            "banco", "bank", "tarjeta", "card", "préstamo", "loan",
            "impuesto", "tax", "bitcoin", "ethereum", "accion", "share",
            "mercado", "market", "economía", "economy", "compra", "comprar", "buy"
        ),
        "entertainment" to listOf(
            "película", "música", "juego", "movie", "music", "game",
            "serie", "series", "canción", "song", "libro", "book",
            "videojuego", "videogame", "streaming", "netflix", "spotify",
            "concierto", "concert", "teatro", "theater", "podcast",
            "recomendar", "recommend", "ver", "watch", "escuchar", "listen",
            "leer", "read", "jugar", "play"
        ),
        "work" to listOf(
            "trabajo", "work", "oficina", "office", "reunión", "meeting",
            "proyecto", "project", "jefe", "boss", "colega", "colleague",
            "email", "correo", "presentación", "presentation", "deadline",
            "fecha límite", "tarea", "task", "productividad", "productivity",
            "equipo", "team", "empresa", "company", "contrato", "contract"
        ),
        "education" to listOf(
            "estudiar", "study", "escuela", "school", "universidad", "university",
            "clase", "class", "examen", "exam", "tarea", "homework",
            "aprender", "learn", "curso", "course", "profesor", "teacher",
            "estudiante", "student", "diploma", "degree", "certificación",
            "certification", "lectura", "reading", "investigación", "research"
        )
    )

    // Emotion-to-response-mood mapping for empathetic adaptation
    private val emotionResponseMap = mapOf(
        "joy" to "enthusiastic",
        "excitement" to "excited",
        "enthusiasm" to "enthusiastic",
        "sadness" to "compassionate",
        "melancholy" to "gentle",
        "anger" to "calm_reassuring",
        "frustration" to "patient",
        "fear" to "comforting",
        "anxiety" to "calming",
        "surprise" to "curious",
        "contentment" to "warm",
        "neutral" to "neutral"
    )

    // Sentiment tracking buffer
    private val sentimentHistory = mutableListOf<Pair<Long, Float>>()
    private val maxSentimentHistory = 20

    // ═══════════════════════════════════════════════════════════════════
    //  TURN-TAKING LOGIC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Analyze if user has finished speaking or is just pausing.
     * Returns true if user appears to be done with their turn.
     *
     * Enhanced with multi-factor analysis for natural conversation flow.
     */
    fun isTurnComplete(text: String, silenceDurationMs: Long, hasFillerWords: Boolean): Boolean {
        val trimmed = text.trim()

        // Definitive end signals
        if (trimmed.endsWith("?")) return true
        if (trimmed.endsWith(".") || trimmed.endsWith("!")) return true

        // Very long silence — always complete
        if (silenceDurationMs > 2500) return true

        // Silence >2s with no fillers — done
        if (silenceDurationMs > 2000 && !hasFillerWords) return true

        // Filler words with short silence — user is thinking
        if (hasFillerWords && silenceDurationMs < 1000) return false

        // Medium silence with fillers — likely thinking
        if (hasFillerWords && silenceDurationMs < 1500) return false

        // Medium silence without fillers — probably done
        if (silenceDurationMs > 1200 && !hasFillerWords) return true

        // Short silence — wait
        return false
    }

    /**
     * Detailed turn-taking analysis with reasoning.
     */
    fun analyzeTurn(text: String, silenceDurationMs: Long): TurnDecision {
        val hasFiller = detectFillerWords(text)
        val trimmed = text.trim()
        val wordCount = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        // Scoring system: each factor contributes to confidence
        var completionScore = 0f
        val reasons = mutableListOf<String>()

        // Punctuation signals
        when {
            trimmed.endsWith("?") -> { completionScore += 0.4f; reasons.add("question_mark") }
            trimmed.endsWith(".") -> { completionScore += 0.35f; reasons.add("period") }
            trimmed.endsWith("!") -> { completionScore += 0.35f; reasons.add("exclamation") }
        }

        // Silence duration signals
        when {
            silenceDurationMs > 2500 -> { completionScore += 0.4f; reasons.add("long_silence") }
            silenceDurationMs > 1500 && !hasFiller -> { completionScore += 0.3f; reasons.add("medium_silence_no_filler") }
            silenceDurationMs > 1000 && !hasFiller -> { completionScore += 0.15f; reasons.add("short_silence_no_filler") }
            silenceDurationMs < 500 -> { completionScore -= 0.2f; reasons.add("very_short_silence") }
        }

        // Filler word signals
        if (hasFiller) {
            completionScore -= 0.2f
            reasons.add("filler_words_present")
        }

        // Word count signals
        when {
            wordCount <= 1 -> { completionScore -= 0.15f; reasons.add("very_few_words") }
            wordCount in 2..4 -> { completionScore += 0.05f; reasons.add("short_phrase") }
            wordCount > 10 -> { completionScore += 0.1f; reasons.add("long_utterance") }
        }

        // Contextual signals
        val ctx = _context.value
        if (ctx.userPauses > 3) {
            completionScore += 0.1f
            reasons.add("many_pauses_habit")
        }

        val isComplete = completionScore >= 0.4f
        val confidence = completionScore.coerceIn(0f, 1f)

        val suggestedAction = when {
            !isComplete && hasFiller -> SuggestedAction.WAIT
            !isComplete && silenceDurationMs < 800 -> SuggestedAction.WAIT
            !isComplete -> SuggestedAction.BACKCHANNEL
            isComplete && needsClarification(text) -> SuggestedAction.CLARIFY
            isComplete && ctx.needsTopicShift -> SuggestedAction.CHANGE_TOPIC
            isComplete && silenceDurationMs > 5000 -> SuggestedAction.END_CONVERSATION
            isComplete -> SuggestedAction.RESPOND
            else -> SuggestedAction.WAIT
        }

        return TurnDecision(
            isComplete = isComplete,
            confidence = confidence,
            reason = reasons.joinToString(", "),
            suggestedAction = suggestedAction
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FILLER WORD DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detect filler words in speech to avoid cutting off the user.
     */
    fun detectFillerWords(text: String): Boolean {
        val lower = text.lowercase()
        return fillerWords.any { lower.contains(it) }
    }

    /**
     * Count filler words and return the count with the specific words found.
     */
    fun analyzeFillerWords(text: String): Pair<Int, List<String>> {
        val lower = text.lowercase()
        val found = fillerWords.filter { lower.contains(it) }
        return found.size to found
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TOPIC TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Track conversation topic from user message.
     * Returns the detected topic name.
     */
    fun trackTopic(message: String): String {
        val lower = message.lowercase()
        var bestTopic = ""
        var bestScore = 0

        for ((topic, keywords) in topicMap) {
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestTopic = topic
            }
        }

        if (bestTopic.isNotEmpty()) {
            val oldTopic = _context.value.currentTopic
            if (oldTopic != bestTopic) {
                val history = if (oldTopic.isNotEmpty()) {
                    _context.value.topicHistory + oldTopic
                } else {
                    _context.value.topicHistory
                }

                // Track repeated topics
                val isRepeated = history.takeLast(5).count { it == bestTopic } > 1
                val repeatedCount = if (isRepeated) _context.value.repeatedTopicCount + 1 else 0

                _context.value = _context.value.copy(
                    currentTopic = bestTopic,
                    topicHistory = history.takeLast(10),
                    lastTopicChangeTime = System.currentTimeMillis(),
                    repeatedTopicCount = repeatedCount,
                    needsTopicShift = repeatedCount > 2
                )

                if (oldTopic.isNotEmpty()) {
                    onTopicChanged?.invoke(oldTopic, bestTopic)
                }
            }
        }

        return if (bestTopic.isNotEmpty()) bestTopic else _context.value.currentTopic
    }

    /**
     * Get related topics to the current topic.
     */
    fun getRelatedTopics(topic: String): List<String> {
        return when (topic) {
            "technology" -> listOf("work", "education")
            "health" -> listOf("emotion", "food")
            "food" -> listOf("health", "entertainment")
            "travel" -> listOf("entertainment", "finance")
            "finance" -> listOf("work", "technology")
            "iot" -> listOf("technology", "home")
            "emotion" -> listOf("health", "entertainment")
            "entertainment" -> listOf("emotion", "education")
            "work" -> listOf("technology", "finance")
            "education" -> listOf("technology", "work")
            else -> emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CLARIFICATION REQUESTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Determine if clarification is needed from the user's message.
     */
    fun needsClarification(message: String): Boolean {
        val lower = message.lowercase().trim()
        val wordCount = lower.split(Regex("\\s+")).size

        // Very short messages after a topic change
        if (wordCount <= 2 &&
            _context.value.lastTopicChangeTime > System.currentTimeMillis() - 10000
        ) return true

        // Ambiguous pronouns without context
        val ambiguousPronouns = listOf("eso", "aquello", "ese", "esa", "that", "it", "this", "thing")
        if (ambiguousPronouns.any { lower.contains(it) }) {
            if (_context.value.currentTopic.isEmpty()) return true
        }

        // Vague references
        val vaguePhrases = listOf(
            "algo de eso", "lo de siempre", "esa cosa", "you know what",
            "the thing", "that stuff", "como se llama", "what's it called"
        )
        if (vaguePhrases.any { lower.contains(it) }) return true

        // Contradictory statements
        if (lower.contains("no, quiero decir") || lower.contains("no, I mean")) return true

        // Multiple possible interpretations
        if (wordCount <= 3 && _context.value.currentTopic.isEmpty()) return true

        return false
    }

    /**
     * Generate a structured clarification request.
     */
    fun generateClarification(message: String): ClarificationRequest {
        val lower = message.lowercase().trim()
        val isSpanish = _context.value.detectedLanguage != "en"

        val ambiguousPart = when {
            lower.contains("eso") || lower.contains("aquello") -> "eso/aquello"
            lower.contains("that") || lower.contains("it") -> "that/it"
            else -> message
        }

        val possibleInterpretations = mutableListOf<String>()
        val topic = _context.value.currentTopic

        // Generate interpretations based on context
        if (topic.isNotEmpty()) {
            val topicKeywords = topicMap[topic] ?: emptyList()
            topicKeywords.take(3).forEach { keyword ->
                possibleInterpretations.add(keyword)
            }
        }

        val urgency = when {
            message.split(Regex("\\s+")).size <= 1 -> ClarificationUrgency.HIGH
            _context.value.currentTopic.isEmpty() -> ClarificationUrgency.MEDIUM
            else -> ClarificationUrgency.LOW
        }

        val question = if (isSpanish) {
            when (urgency) {
                ClarificationUrgency.HIGH -> "¿Podrías ser más específico? No estoy seguro de a qué te refieres con \"$ambiguousPart\"."
                ClarificationUrgency.MEDIUM -> "¿Te refieres a algo relacionado con ${topic.ifEmpty { "lo que estábamos hablando" }}?"
                ClarificationUrgency.LOW -> "¿Podrías elaborar un poco más sobre \"$ambiguousPart\"?"
            }
        } else {
            when (urgency) {
                ClarificationUrgency.HIGH -> "Could you be more specific? I'm not sure what you mean by \"$ambiguousPart\"."
                ClarificationUrgency.MEDIUM -> "Do you mean something related to ${topic.ifEmpty { "what we were discussing" }}?"
                ClarificationUrgency.LOW -> "Could you elaborate a bit on \"$ambiguousPart\"?"
            }
        }

        val request = ClarificationRequest(
            question = question,
            ambiguousPart = ambiguousPart,
            possibleInterpretations = possibleInterpretations,
            urgency = urgency
        )

        _context.value = _context.value.copy(
            pendingClarification = question,
            lastClarificationTime = System.currentTimeMillis()
        )

        onClarificationNeeded?.invoke(request)

        return request
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BACKCHANNELING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate a backchannel response (small acknowledgment while user speaks).
     * Adapts to detected language and conversation context.
     */
    fun generateBackchannel(): BackchannelResponse {
        val isSpanish = _context.value.detectedLanguage != "en"
        val emotion = _context.value.conversationMood
        val turnCount = _context.value.turnCount

        // Determine appropriate backchannel type based on context
        val type = when {
            emotion in listOf("sadness", "melancholy", "fear", "anxiety") -> BackchannelType.EMPATHY
            turnCount > 5 && _context.value.userPauses > 2 -> BackchannelType.ENCOURAGEMENT
            _context.value.currentTopic.isNotEmpty() -> BackchannelType.UNDERSTANDING
            else -> BackchannelType.ACKNOWLEDGMENT
        }

        val text = when (type) {
            BackchannelType.ACKNOWLEDGMENT -> {
                if (isSpanish) listOf("Mhm", "Sí", "Ajá", "Ok").random()
                else listOf("Mhm", "Yeah", "Uh-huh", "OK").random()
            }
            BackchannelType.UNDERSTANDING -> {
                if (isSpanish) listOf("I see", "Sure", "Right", "I understand").random()
                else listOf("I see", "Right", "Got it", "I understand").random()
            }
            BackchannelType.AGREEMENT -> {
                if (isSpanish) listOf("Exacto", "Totalmente", "Tienes razón", "Así es").random()
                else listOf("Exactly", "Absolutely", "Right", "That's right").random()
            }
            BackchannelType.ENCOURAGEMENT -> {
                if (isSpanish) listOf("Sigue", "Adelante", "Cuéntame más", "Continúa").random()
                else listOf("Go on", "Tell me more", "Continue", "Please go ahead").random()
            }
            BackchannelType.EMPATHY -> {
                if (isSpanish) listOf("Ay", "Lo entiendo", "Qué fuerte", "Pobre").random()
                else listOf("Oh", "I'm sorry to hear that", "That sounds tough", "I feel you").random()
            }
        }

        val confidence = when (type) {
            BackchannelType.ACKNOWLEDGMENT -> 0.9f
            BackchannelType.UNDERSTANDING -> 0.85f
            BackchannelType.AGREEMENT -> 0.7f
            BackchannelType.ENCOURAGEMENT -> 0.6f
            BackchannelType.EMPATHY -> 0.75f
        }

        val response = BackchannelResponse(
            text = text,
            type = type,
            appropriateConfidence = confidence
        )

        _context.value = _context.value.copy(lastBackchannelTime = System.currentTimeMillis())
        onBackchannelSuggested?.invoke(response)

        return response
    }

    /**
     * Check if a backchannel is appropriate right now.
     */
    fun shouldBackchannel(silenceDurationMs: Long): Boolean {
        val ctx = _context.value
        val timeSinceLastBackchannel = System.currentTimeMillis() - ctx.lastBackchannelTime

        // Don't backchannel too frequently (at least 3s between backchannels)
        if (timeSinceLastBackchannel < 3000) return false

        // Backchannel during medium pauses (user might continue)
        if (silenceDurationMs in 600..1200) return true

        // Backchannel after filler words
        if (ctx.fillerWordsDetected > 0 && silenceDurationMs in 300..800) return true

        return false
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EMPATHETIC RESPONSE ADAPTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the recommended response mood based on detected voice emotion.
     * This helps the AI adapt its tone to match the user's emotional state.
     */
    fun getResponseMood(detectedEmotion: String): String {
        return emotionResponseMap[detectedEmotion] ?: "neutral"
    }

    /**
     * Get empathetic response guidelines for the AI based on user's emotion.
     */
    fun getEmpatheticGuidelines(emotion: String): String {
        val isSpanish = _context.value.detectedLanguage != "en"
        return when (emotion) {
            "joy", "excitement", "enthusiasm" ->
                if (isSpanish) "El usuario está feliz. Comparte su entusiasmo, sé positivo y energético."
                else "The user is happy. Share their enthusiasm, be positive and energetic."
            "sadness", "melancholy" ->
                if (isSpanish) "El usuario está triste. Sé compasivo, no minimices sus sentimientos. Ofrece apoyo."
                else "The user is sad. Be compassionate, don't minimize their feelings. Offer support."
            "anger", "frustration" ->
                if (isSpanish) "El usuario está enojado/frustrado. Mantén la calma, no te defiendas. Valida su frustración."
                else "The user is angry/frustrated. Stay calm, don't be defensive. Validate their frustration."
            "fear", "anxiety" ->
                if (isSpanish) "El usuario tiene miedo/ansiedad. Sé reconfortante y tranquilizador. Ofrece seguridad."
                else "The user is fearful/anxious. Be comforting and reassuring. Offer reassurance."
            "surprise", "amazement" ->
                if (isSpanish) "El usuario está sorprendido. Comparte la sorpresa y sé curioso."
                else "The user is surprised. Share the surprise and be curious."
            else ->
                if (isSpanish) "Keep a neutral and professional tone."
                else "Keep a neutral and professional tone."
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONVERSATION PHASE & FLOW MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Update conversation phase based on turn count and context.
     */
    private fun updateConversationPhase() {
        val ctx = _context.value
        val elapsed = System.currentTimeMillis() - ctx.startedAt

        val newPhase = when {
            ctx.turnCount <= 1 -> ConversationPhase.OPENING
            ctx.pendingClarification != null -> ConversationPhase.CLARIFYING
            ctx.turnCount <= 4 && ctx.currentTopic.isEmpty() -> ConversationPhase.EXPLORING
            ctx.currentTopic.isNotEmpty() && ctx.repeatedTopicCount < 2 -> ConversationPhase.DEEP_DIVE
            ctx.needsTopicShift -> ConversationPhase.EXPLORING
            ctx.unresolvedQuestions.isNotEmpty() -> ConversationPhase.RESOLVING
            elapsed > 300000 && ctx.userPauses > 5 -> ConversationPhase.CLOSING  // 5+ min with many pauses
            else -> ctx.conversationPhase
        }

        if (newPhase != ctx.conversationPhase) {
            onConversationPhaseChanged?.invoke(ctx.conversationPhase, newPhase)
            _context.value = _context.value.copy(conversationPhase = newPhase)
        }
    }

    /**
     * Calculate user engagement level based on conversation patterns.
     */
    private fun calculateEngagement(): Float {
        val ctx = _context.value
        val elapsed = System.currentTimeMillis() - ctx.startedAt
        if (elapsed == 0L) return 0.5f

        val turnsPerMinute = ctx.turnCount.toFloat() / (elapsed / 60000f).coerceAtLeast(1f)

        // More turns per minute = higher engagement (up to a point)
        val turnEngagement = when {
            turnsPerMinute > 10 -> 0.9f
            turnsPerMinute > 5 -> 0.8f
            turnsPerMinute > 3 -> 0.7f
            turnsPerMinute > 1 -> 0.5f
            turnsPerMinute > 0.5 -> 0.3f
            else -> 0.2f
        }

        // Frequent pauses might indicate disengagement
        val pauseFactor = when {
            ctx.userPauses > ctx.turnCount -> 0.3f
            ctx.userPauses > ctx.turnCount / 2 -> 0.6f
            else -> 0.8f
        }

        // Topic depth — staying on topic suggests engagement
        val topicFactor = when {
            ctx.currentTopic.isNotEmpty() && ctx.repeatedTopicCount < 2 -> 0.9f
            ctx.currentTopic.isEmpty() -> 0.5f
            else -> 0.4f
        }

        return (turnEngagement * 0.4f + pauseFactor * 0.3f + topicFactor * 0.3f)
            .coerceIn(0f, 1f)
    }

    /**
     * Track sentiment trajectory over the conversation.
     */
    private fun updateSentimentTrajectory(emotionScore: Float) {
        val now = System.currentTimeMillis()
        sentimentHistory.add(now to emotionScore)
        if (sentimentHistory.size > maxSentimentHistory) {
            sentimentHistory.removeAt(0)
        }

        if (sentimentHistory.size < 3) {
            _context.value = _context.value.copy(sentimentTrajectory = SentimentTrajectory.UNKNOWN)
            return
        }

        // Calculate trend from recent sentiment scores
        val recent = sentimentHistory.takeLast(5)
        val firstHalf = recent.take(recent.size / 2).map { it.second }.average()
        val secondHalf = recent.takeLast(recent.size / 2 + 1).map { it.second }.average()

        val diff = secondHalf - firstHalf
        val variance = recent.map { (it.second - recent.map { p -> p.second }.average()).let { d -> d * d } }.average()

        val trajectory = when {
            variance > 0.15 -> SentimentTrajectory.VOLATILE
            diff > 0.1 -> SentimentTrajectory.IMPROVING
            diff < -0.1 -> SentimentTrajectory.DECLINING
            else -> SentimentTrajectory.STABLE
        }

        _context.value = _context.value.copy(sentimentTrajectory = trajectory)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONTEXT UPDATES & PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Update conversation context after each turn.
     */
    fun updateContext(userMessage: String, aiResponse: String, emotion: String = "neutral") {
        val topic = trackTopic(userMessage)
        val hasFiller = detectFillerWords(userMessage)
        val (fillerCount, _) = analyzeFillerWords(userMessage)

        // Emotion score: map to numeric for trajectory tracking
        val emotionScore = when (emotion) {
            "joy", "excitement", "enthusiasm", "thrill" -> 1.0f
            "contentment" -> 0.8f
            "surprise", "amazement" -> 0.6f
            "neutral" -> 0.5f
            "fear", "anxiety" -> 0.3f
            "anger", "frustration" -> 0.2f
            "sadness", "melancholy" -> 0.1f
            else -> 0.5f
        }

        updateSentimentTrajectory(emotionScore)

        // Track unresolved questions
        val isQuestion = userMessage.trim().endsWith("?") ||
                detectIntent(userMessage) == "question"
        val unresolved = if (isQuestion) {
            _context.value.unresolvedQuestions + userMessage
        } else {
            // Remove resolved questions (AI answered them)
            _context.value.unresolvedQuestions.filterNot { q ->
                aiResponse.lowercase().contains(q.lowercase().take(10))
            }
        }

        _context.value = _context.value.copy(
            lastUserIntent = detectIntent(userMessage),
            lastUserMessage = userMessage,
            lastAIResponse = aiResponse,
            turnCount = _context.value.turnCount + 1,
            fillerWordsDetected = if (hasFiller) _context.value.fillerWordsDetected + fillerCount else _context.value.fillerWordsDetected,
            conversationMood = emotion,
            isUserFinished = true,
            lastTurnTime = System.currentTimeMillis(),
            unresolvedQuestions = unresolved.takeLast(5)
        )

        // Update derived values
        val engagement = calculateEngagement()
        _context.value = _context.value.copy(userEngagementLevel = engagement)
        onEngagementChanged?.invoke(engagement)

        updateConversationPhase()

        // Persist important conversation facts to memory
        scope.launch {
            try {
                val facts = extractFacts(userMessage)
                for (fact in facts) {
                    db.memoryFactDao().upsert(fact)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Extract memorable facts from user messages for long-term memory.
     */
    private fun extractFacts(message: String): List<com.nexa.ai.data.local.MemoryFactEntity> {
        val facts = mutableListOf<com.nexa.ai.data.local.MemoryFactEntity>()
        val lower = message.lowercase()

        // Personal preferences
        val preferencePatterns = mapOf(
            "preference" to listOf(
                Regex("me gusta (\\w+)"),
                Regex("prefiero (\\w+)"),
                Regex("no me gusta (\\w+)"),
                Regex("i like (\\w+)"),
                Regex("i prefer (\\w+)"),
                Regex("i don'?t like (\\w+)")
            ),
            "personal" to listOf(
                Regex("mi (\\w+) es (\\w+)"),
                Regex("i am (\\w+)"),
                Regex("my (\\w+) is (\\w+)")
            )
        )

        for ((category, patterns) in preferencePatterns) {
            for (pattern in patterns) {
                val match = pattern.find(lower)
                if (match != null) {
                    facts.add(
                        com.nexa.ai.data.local.MemoryFactEntity(
                            fact = match.value,
                            category = category,
                            source = "conversation",
                            confidence = 0.6f
                        )
                    )
                }
            }
        }

        return facts
    }

    /**
     * Detect the intent behind the user's message.
     */
    private fun detectIntent(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("?") ||
                    lower.startsWith("qué") || lower.startsWith("cuál") ||
                    lower.startsWith("cómo") || lower.startsWith("cuándo") ||
                    lower.startsWith("dónde") || lower.startsWith("por qué") ||
                    lower.startsWith("who") || lower.startsWith("what") ||
                    lower.startsWith("how") || lower.startsWith("when") ||
                    lower.startsWith("where") || lower.startsWith("why") -> "question"

            lower.contains("enciende") || lower.contains("apaga") ||
                    lower.contains("prende") || lower.contains("desactiva") ||
                    lower.contains("activa") || lower.contains("turn on") ||
                    lower.contains("turn off") -> "command"

            lower.contains("gracias") || lower.contains("thank") -> "gratitude"
            lower.contains("ayuda") || lower.contains("help") -> "help_request"

            lower.contains("adiós") || lower.contains("adios") ||
                    lower.contains("bye") || lower.contains("goodbye") ||
                    lower.contains("hasta luego") || lower.contains("see you") -> "farewell"

            lower.contains("hola") || lower.contains("hello") ||
                    lower.contains("hi") || lower.contains("hey") -> "greeting"

            lower.contains("sí") || lower.contains("claro") ||
                    lower.contains("yes") || lower.contains("sure") ||
                    lower.contains("ok") || lower.contains("vale") -> "affirmation"

            lower.contains("no") || lower.contains("nop") -> "negation"

            else -> "statement"
        }
    }

    /**
     * Set the detected language for context-aware responses.
     */
    fun setDetectedLanguage(language: String) {
        _context.value = _context.value.copy(detectedLanguage = language)
    }

    /**
     * Record a user pause during conversation.
     */
    fun recordUserPause() {
        _context.value = _context.value.copy(
            userPauses = _context.value.userPauses + 1
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AI CONTEXT GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get conversation context as a string for the AI system prompt.
     * Provides rich context for more natural AI responses.
     */
    fun getConversationContextForAI(): String {
        val ctx = _context.value
        val parts = mutableListOf<String>()

        // Current topic
        if (ctx.currentTopic.isNotEmpty()) {
            parts.add("Tema actual: ${ctx.currentTopic}")
        }

        // Topic history
        if (ctx.topicHistory.isNotEmpty()) {
            parts.add("Temas anteriores: ${ctx.topicHistory.joinToString(", ")}")
        }

        // Mood and sentiment
        if (ctx.conversationMood != "neutral") {
            val moodGuidelines = getEmpatheticGuidelines(ctx.conversationMood)
            parts.add("Estado de ánimo del usuario: ${ctx.conversationMood}")
            parts.add("Guía de respuesta: $moodGuidelines")
        }

        // Sentiment trajectory
        when (ctx.sentimentTrajectory) {
            SentimentTrajectory.DECLINING -> parts.add("⚠️ El ánimo del usuario está bajando. Sé más empático y positivo.")
            SentimentTrajectory.IMPROVING -> parts.add("El ánimo del usuario está mejorando. Mantén el tono actual.")
            SentimentTrajectory.VOLATILE -> parts.add("El ánimo del usuario es volátil. Sé cuidadoso y adaptable.")
            else -> { /* No additional guidance needed */ }
        }

        // Conversation phase
        val phaseAdvice = when (ctx.conversationPhase) {
            ConversationPhase.OPENING -> "Estás iniciando la conversación. Sé cálido y acogedor."
            ConversationPhase.EXPLORING -> "El usuario está explorando temas. Haz preguntas abiertas."
            ConversationPhase.DEEP_DIVE -> "Conversación profunda en curso. Sé detallado y reflexivo."
            ConversationPhase.CLARIFYING -> "Necesitas clarificación. Pregunta de forma natural."
            ConversationPhase.RESOLVING -> "Resolviendo tema. Resume y confirma entendimiento."
            ConversationPhase.CLOSING -> "La conversación está terminando. Cierra con calidez."
        }
        parts.add("Fase de conversación: ${ctx.conversationPhase.name}. $phaseAdvice")

        // Turn count
        parts.add("Turno de conversación: ${ctx.turnCount}")

        // Pending clarification
        if (ctx.pendingClarification != null) {
            parts.add("Pendiente clarificación: ${ctx.pendingClarification}")
        }

        // Engagement level
        if (ctx.userEngagementLevel < 0.4f) {
            parts.add("⚠️ Participación baja. Considera cambiar de tema o preguntar algo nuevo.")
        }

        // Unresolved questions
        if (ctx.unresolvedQuestions.isNotEmpty()) {
            parts.add("Preguntas sin resolver: ${ctx.unresolvedQuestions.joinToString("; ")}")
        }

        // Language
        if (ctx.detectedLanguage != "unknown") {
            val langName = when (ctx.detectedLanguage) {
                "es" -> "español"; "en" -> "inglés"; else -> ctx.detectedLanguage
            }
            parts.add("Idioma del usuario: $langName. Responde en el mismo idioma.")
        }

        // Last messages for continuity
        if (ctx.lastUserMessage.isNotEmpty()) {
            parts.add("Último mensaje del usuario: \"${ctx.lastUserMessage.take(100)}\"")
        }

        return parts.joinToString(". ")
    }

    /**
     * Get a brief summary of the conversation state for quick context.
     */
    fun getConversationSummary(): String {
        val ctx = _context.value
        return buildString {
            append("Conversación: ${ctx.turnCount} turnos")
            if (ctx.currentTopic.isNotEmpty()) append(", tema: ${ctx.currentTopic}")
            append(", ánimo: ${ctx.conversationMood}")
            append(", fase: ${ctx.conversationPhase.name}")
            append(", participación: ${(ctx.userEngagementLevel * 100).toInt()}%")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RESET & CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reset context for new conversation.
     */
    fun resetContext() {
        val oldId = _context.value.conversationId
        _context.value = ConversationContext(
            conversationId = java.util.UUID.randomUUID().toString()
        )
        sentimentHistory.clear()
    }

    /**
     * Start a new conversation session.
     */
    fun startNewConversation() {
        resetContext()
        _context.value = _context.value.copy(
            conversationPhase = ConversationPhase.OPENING,
            startedAt = System.currentTimeMillis()
        )
    }

    /**
     * Clean up resources.
     */
    fun shutdown() {
        onClarificationNeeded = null
        onBackchannelSuggested = null
        onTopicChanged = null
        onConversationPhaseChanged = null
        onEngagementChanged = null
        sentimentHistory.clear()
    }
}
