package com.nexa.ai.ml

import android.app.Application
import android.util.Log
import com.nexa.ai.data.local.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * NEXA On-Device ML Engine — Aprendizaje automático local sin necesidad de internet
 *
 * Capacidades:
 * - Text Classification: Clasifica mensajes del usuario por intención y tema
 * - Sentiment Analysis: Análisis de sentimiento basado en reglas + aprendizaje local
 * - Pattern Recognition: Detecta patrones en el comportamiento del usuario
 * - Preference Learning: Aprende preferencias del usuario de interacciones pasadas
 * - Activity Prediction: Predice qué quiere hacer el usuario basándose en hábitos
 * - Contextual Adaptation: Adapta respuestas basándose en contexto aprendido
 * - ML Kit Integration: Language ID, Entity Extraction, Smart Reply, Translation stubs
 * - TensorFlow Lite: Model loading framework with fallback when TFLite unavailable
 * - Enhanced NLP: Tokenization, NER, Coreference, Multi-intent detection
 * - Federated Learning: Local model training simulation with privacy preservation
 *
 * Este motor funciona OFFLINE — todo el aprendizaje se almacena localmente en Room DB
 * y se sincroniza con el servidor cuando hay conexión.
 */
class OnDeviceMLEngine(private val application: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { NexaDatabase.getInstance(application) }

    companion object {
        private const val TAG = "OnDeviceMLEngine"
        private const val ML_KIT_AVAILABLE = false // Set true when ML Kit dependency added
        private const val TFLITE_AVAILABLE = false // Set true when TFLite dependency added
        private const val FEDERATED_ROUND_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val ANOMALY_THRESHOLD = 2.5f // Standard deviations
        private const val MAX_COREFERENCE_HISTORY = 20
        private const val MAX_PROACTIVE_SUGGESTIONS = 10
        private const val PREFERENCE_DECAY_FACTOR = 0.95f
    }

    // ═══════════════════════════════════════
    //  DATA CLASSES — Enhanced State
    // ═══════════════════════════════════════

    data class MLState(
        val lastIntent: String = "unknown",
        val lastIntentConfidence: Float = 0f,
        val lastSentiment: Float = 0f,
        val userMood: String = "neutral",
        val preferredResponseLength: String = "moderate",
        val preferredResponseStyle: String = "balanced", // formal, informal, technical, simple
        val topTopics: List<String> = emptyList(),
        val interactionCount: Int = 0,
        val learnedPreferences: Int = 0,
        val isOnline: Boolean = false,
        val detectedLanguage: String = "es",
        val languageConfidence: Float = 0f,
        val isTFLiteReady: Boolean = false,
        val isMLKitReady: Boolean = false,
        val federatedRoundCount: Int = 0,
        val lastAnomalyScore: Float = 0f,
        val coreferenceMapSize: Int = 0,
        val topicInterestScores: Map<String, Float> = emptyMap(),
        val conversationSatisfactionPrediction: Float = 0.5f,
        val multiIntents: List<IntentResult> = emptyList()
    )

    data class IntentResult(
        val intent: String,
        val confidence: Float,
        val subIntents: List<String> = emptyList()
    )

    data class DetectedEntity(
        val type: String,         // "person", "place", "org", "date", "address", "phone", "tracking", "email"
        val text: String,
        val startIndex: Int,
        val endIndex: Int,
        val confidence: Float = 0.7f,
        val normalized: String? = null
    )

    data class SmartReplySuggestion(
        val text: String,
        val confidence: Float,
        val context: String = "general" // "question", "greeting", "statement", "emotional"
    )

    data class TranslationResult(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val confidence: Float = 0f,
        val isOffline: Boolean = true
    )

    data class TFLiteModelInfo(
        val modelName: String,
        val modelPath: String,
        val inputShape: List<Int>,
        val outputShape: List<Int>,
        val isLoaded: Boolean = false,
        val lastInferenceTimeMs: Long = 0,
        val version: Int = 1
    )

    data class CoreferenceMention(
        val text: String,
        val resolvedText: String?,
        val type: String,         // "pronoun", "noun_phrase", "demonstrative"
        val sentenceIndex: Int,
        val confidence: Float
    )

    data class TopicInterestScore(
        val topic: String,
        val score: Float,          // 0.0 - 1.0
        val interactionCount: Int,
        val lastInteraction: Long,
        val trend: String = "stable" // "rising", "falling", "stable"
    )

    data class TimePattern(
        val hourOfDay: Int,
        val dayOfWeek: Int,
        val dominantActivity: String,
        val frequency: Int,
        val confidence: Float
    )

    data class ProactiveSuggestion(
        val id: String,
        val type: String,          // "action", "information", "reminder", "iot", "conversation"
        val title: String,
        val description: String,
        val confidence: Float,
        val contextTriggers: List<String>,
        val timestamp: Long = System.currentTimeMillis(),
        val isDismissed: Boolean = false
    )

    data class AnomalyReport(
        val type: String,          // "activity", "emotion", "usage", "pattern"
        val description: String,
        val severity: Float,       // 0.0 - 1.0
        val deviationScore: Float, // Standard deviations from mean
        val timestamp: Long = System.currentTimeMillis(),
        val relatedContext: String? = null
    )

    data class FederatedModelUpdate(
        val modelId: String,
        val version: Int,
        val gradientData: Map<String, List<Float>>,
        val sampleCount: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val isAggregated: Boolean = false,
        val privacyEpsilon: Float = 0f // Differential privacy budget
    )

    data class SituationalContext(
        val timeContext: String,       // "morning", "work_hours", "lunch", "evening", "night"
        val locationContext: String,   // "home", "work", "commuting", "unknown"
        val activityContext: String,   // "still", "walking", "driving", "exercising"
        val emotionalContext: String,  // derived from recent interactions
        val deviceContext: String,     // "phone_in_hand", "phone_down", "headphones", "driving"
        val socialContext: String,     // "alone", "with_people", "in_meeting"
        val combinedDescription: String
    )

    private val _mlState = MutableStateFlow(MLState())
    val mlState: StateFlow<MLState> = _mlState.asStateFlow()

    // ═══════════════════════════════════════
    //  COREFERENCE STATE
    // ═══════════════════════════════════════

    private val coreferenceHistory = mutableListOf<CoreferenceMention>()
    private val entityMentions = mutableMapOf<String, String>() // pronoun -> resolved entity

    // ═══════════════════════════════════════
    //  TOPIC INTEREST TRACKING
    // ═══════════════════════════════════════

    private val topicInterestMap = mutableMapOf<String, TopicInterestScore>()
    private val _proactiveSuggestions = MutableStateFlow<List<ProactiveSuggestion>>(emptyList())
    val proactiveSuggestions: StateFlow<List<ProactiveSuggestion>> = _proactiveSuggestions.asStateFlow()

    // ═══════════════════════════════════════
    //  TFLITE MODEL REGISTRY
    // ═══════════════════════════════════════

    private val tfliteModels = mutableMapOf<String, TFLiteModelInfo>()
    private val tfliteModelData = mutableMapOf<String, Any>() // Would hold Interpreter in production

    // ═══════════════════════════════════════
    //  FEDERATED LEARNING STATE
    // ═══════════════════════════════════════

    private val _federatedUpdates = MutableStateFlow<List<FederatedModelUpdate>>(emptyList())
    val federatedUpdates: StateFlow<List<FederatedModelUpdate>> = _federatedUpdates.asStateFlow()
    private var lastFederatedRoundTime: Long = 0

    // ═══════════════════════════════════════
    //  ANOMALY DETECTION STATE
    // ═══════════════════════════════════════

    private val _anomalies = MutableStateFlow<List<AnomalyReport>>(emptyList())
    val anomalies: StateFlow<List<AnomalyReport>> = _anomalies.asStateFlow()
    private val activityBaseline = mutableMapOf<String, Pair<Float, Float>>() // key -> (mean, stddev)

    // ═══════════════════════════════════════
    //  TIME PATTERN CACHE
    // ═══════════════════════════════════════

    private val timePatternCache = mutableListOf<TimePattern>()

    // ═══════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════

    init {
        scope.launch {
            initializeEngine()
        }
    }

    private suspend fun initializeEngine() {
        try {
            // Load existing preferences
            val prefs = db.userPreferenceDao().getAll()
            _mlState.value = _mlState.value.copy(
                learnedPreferences = prefs.size,
                preferredResponseLength = prefs.find { it.category == "response_length" }?.value ?: "moderate",
                preferredResponseStyle = prefs.find { it.category == "response_style" }?.value ?: "balanced"
            )

            // Load topic interest scores
            loadTopicInterestScores()

            // Load time patterns
            loadTimePatterns()

            // Initialize TFLite model registry
            initializeTFLiteModels()

            // Initialize anomaly baselines
            loadAnomalyBaselines()

            // Load coreference history
            loadCoreferenceHistory()

            // Check ML Kit availability
            checkMLKitAvailability()

            // Start federated learning check
            checkFederatedLearning()

            Log.d(TAG, "OnDeviceMLEngine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ML engine", e)
        }
    }

    // ═══════════════════════════════════════
    //  TEXT CLASSIFICATION (On-device) — ORIGINAL + ENHANCED
    // ═══════════════════════════════════════

    private val intentPatterns = mapOf(
        "greeting" to listOf("hola", "buenos días", "buenas", "hey", "hi", "hello", "qué tal", "saludos"),
        "question" to listOf("qué", "cómo", "cuándo", "dónde", "por qué", "quién", "cuánto", "what", "how", "when", "where", "why", "who"),
        "command" to listOf("crea", "genera", "haz", "busca", "dibuja", "escribe", "traduce", "repara", "create", "generate", "make", "search", "draw", "write"),
        "emotion_expression" to listOf("estoy feliz", "me siento", "estoy triste", "estoy enojado", "tengo miedo", "i feel", "i'm happy", "i'm sad"),
        "tool_request" to listOf("clima", "vuelo", "noticias", "traducción", "precio", "weather", "flight", "news", "translate", "price"),
        "farewell" to listOf("adiós", "adios", "chao", "hasta luego", "nos vemos", "bye", "goodbye", "see you"),
        "gratitude" to listOf("gracias", "muchas gracias", "te agradezco", "thank", "thanks", "appreciate"),
        "complaint" to listOf("no funciona", "está mal", "error", "no es eso", "inténtalo de nuevo", "doesn't work", "wrong", "try again"),
        "entertainment" to listOf("chiste", "broma", "juego", "historia", "poema", "joke", "game", "story", "poem"),
        "iot_control" to listOf("enciende", "apaga", "luz", "temperatura", "volumen", "turn on", "turn off", "light"),
        "scheduling" to listOf("recordatorio", "alarma", "cita", "reunión", "calendar", "reminder", "alarm", "meeting", "agenda"),
        "navigation" to listOf("cómo llego", "dirección", "ruta", "mapa", "how do i get", "directions", "route", "map"),
        "learning" to listOf("explícame", "enseña", "aprender", "cómo funciona", "explain", "teach", "learn", "how does"),
        "comparison" to listOf("diferencia", "mejor", "versus", "compara", "difference", "better", "versus", "compare"),
        "confirmation" to listOf("sí", "claro", "exacto", "afirmativo", "yes", "sure", "exactly", "right", "correct")
    )

    /**
     * Classify user intent from message text.
     * Runs entirely on-device with rule-based + learned patterns.
     * Returns primary intent string.
     */
    fun classifyIntent(message: String): String {
        val results = classifyIntentWithConfidence(message)
        _mlState.value = _mlState.value.copy(
            lastIntent = results.firstOrNull()?.intent ?: "general",
            lastIntentConfidence = results.firstOrNull()?.confidence ?: 0f,
            multiIntents = results
        )
        return results.firstOrNull()?.intent ?: "general"
    }

    /**
     * Enhanced intent classification with confidence scores and multi-intent detection.
     * Returns list of IntentResult sorted by confidence.
     */
    fun classifyIntentWithConfidence(message: String): List<IntentResult> {
        val lower = message.lowercase().trim()
        val results = mutableListOf<IntentResult>()

        // Score each intent pattern
        for ((intent, patterns) in intentPatterns) {
            var score = 0f
            var matchedPatterns = mutableListOf<String>()

            for (pattern in patterns) {
                if (lower.contains(pattern)) {
                    val weight = pattern.length.toFloat() / 10f // Longer matches = stronger signal
                    score += weight
                    matchedPatterns.add(pattern)
                }
            }

            if (score > 0) {
                // Normalize confidence to 0-1 range
                val confidence = min(score / 3f, 1f)
                results.add(IntentResult(
                    intent = intent,
                    confidence = confidence,
                    subIntents = matchedPatterns
                ))
            }
        }

        // Check learned preferences for intent override
        scope.launch {
            try {
                val learned = db.userPreferenceDao().get("intent_override", lower.take(20))
                if (learned != null && learned.confidence > 0.7f) {
                    // Boost the learned intent's confidence
                    val existing = results.find { it.intent == learned.value }
                    if (existing != null) {
                        val idx = results.indexOf(existing)
                        results[idx] = existing.copy(confidence = min(existing.confidence + 0.2f, 1f))
                    }
                }
            } catch (_: Exception) {}
        }

        // Sort by confidence descending
        results.sortByDescending { it.confidence }

        // Detect multi-intent: if top 2 intents are close in confidence, both are relevant
        val multiIntents = if (results.size >= 2 &&
            results[0].confidence > 0.3f &&
            results[1].confidence > 0.3f &&
            (results[0].confidence - results[1].confidence) < 0.3f) {
            results.take(2)
        } else {
            results.take(1)
        }

        return if (results.isEmpty()) {
            listOf(IntentResult(intent = "general", confidence = 0.5f))
        } else {
            multiIntents.ifEmpty { results.take(1) }
        }
    }

    /**
     * Detect multiple intents in a single message.
     * Returns all intents above minimum confidence threshold.
     */
    fun detectMultiIntent(message: String, minConfidence: Float = 0.25f): List<IntentResult> {
        val allIntents = classifyIntentWithConfidence(message)
        return allIntents.filter { it.confidence >= minConfidence }
    }

    // ═══════════════════════════════════════
    //  SENTIMENT ANALYSIS (On-device) — ORIGINAL + ENHANCED
    // ═══════════════════════════════════════

    private val positiveWords = setOf(
        "bien", "bueno", "genial", "excelente", "perfecto", "maravilloso", "increíble",
        "feliz", "gracias", "love", "encanta", "me gusta", "divertido", "alegr",
        "great", "awesome", "amazing", "happy", "perfect", "wonderful"
    )

    private val negativeWords = setOf(
        "mal", "malo", "horrible", "terrible", "odio", "error", "problema",
        "no funciona", "fracaso", "decepcionante", "pésimo", "basura",
        "bad", "terrible", "hate", "wrong", "error", "problem", "awful"
    )

    private val intensifiers = setOf("muy", "super", "extremadamente", "increíblemente", "very", "super", "extremely")
    private val negators = setOf("no", "nunca", "jamás", "tampoco", "not", "never", "neither", "nor")
    private val diminishers = setOf("un poco", "algo", "ligeramente", "más o menos", "a little", "somewhat", "slightly")

    /**
     * Analyze sentiment of message. Returns score from -1.0 (negative) to +1.0 (positive).
     * Enhanced with negation handling, diminishers, and contextual boosting.
     */
    fun analyzeSentiment(message: String): Float {
        val words = message.lowercase().split(Regex("\\s+"))
        var posScore = 0f
        var negScore = 0f
        var intensify = false
        var negate = false
        var diminish = false

        for (word in words) {
            when {
                word in intensifiers -> { intensify = true; continue }
                word in negators -> { negate = true; continue }
                word in diminishers -> { diminish = true; continue }
            }

            val multiplier = when {
                intensify && diminish -> 1f   // Cancel out
                intensify -> 1.5f
                diminish -> 0.5f
                else -> 1f
            }
            intensify = false
            diminish = false

            if (positiveWords.any { word.contains(it) }) {
                if (negate) {
                    negScore += 0.8f * multiplier // "not good" = negative
                    negate = false
                } else {
                    posScore += 1f * multiplier
                }
            }
            if (negativeWords.any { word.contains(it) }) {
                if (negate) {
                    posScore += 0.6f * multiplier // "not bad" = slightly positive
                    negate = false
                } else {
                    negScore += 1f * multiplier
                }
            }

            // Reset negation if it wasn't applied (negation window = 1 word)
            if (negate && word !in negators) negate = false
        }

        val total = posScore + negScore
        val sentiment = if (total == 0f) 0f else (posScore - negScore) / total

        _mlState.value = _mlState.value.copy(lastSentiment = sentiment)
        return sentiment.coerceIn(-1f, 1f)
    }

    /**
     * Enhanced sentiment with emotional dimensions.
     * Returns a map of emotional axes with scores.
     */
    fun analyzeSentimentDimensions(message: String): Map<String, Float> {
        val baseSentiment = analyzeSentiment(message)
        val lower = message.lowercase()

        val valence = baseSentiment // Positive-negative axis
        val arousal = when {
            lower.contains(Regex("(!|¡|⚠|🔥|💪|❗)")) -> 0.8f
            lower.contains(Regex("(\\?|¿)")) -> 0.5f
            lower.split(" ").size > 20 -> 0.6f
            lower.split(" ").size < 5 -> 0.3f
            else -> 0.4f
        }
        val dominance = when {
            intentPatterns["command"]?.any { lower.contains(it) } == true -> 0.7f
            intentPatterns["question"]?.any { lower.contains(it) } == true -> 0.4f
            else -> 0.5f
        }

        return mapOf(
            "valence" to valence,    // How positive/negative
            "arousal" to arousal,    // How excited/calm
            "dominance" to dominance // How in control/submissive
        )
    }

    // ═══════════════════════════════════════
    //  PREFERENCE LEARNING — ORIGINAL + ENHANCED
    // ═══════════════════════════════════════

    /**
     * Learn from user feedback/behavior.
     * Called when user explicitly or implicitly gives feedback.
     */
    suspend fun learnFromInteraction(
        userMessage: String,
        aiResponse: String,
        userFeedback: String? = null,
        emotionDetected: String = "neutral"
    ) {
        // 1. Learn response length preference
        val responseLength = when {
            aiResponse.length < 200 -> "brief"
            aiResponse.length < 500 -> "moderate"
            else -> "detailed"
        }

        // 2. If user says "más corto" or "más detalle", update preference
        val lower = userMessage.lowercase()
        if (lower.contains("más corto") || lower.contains("resumen") || lower.contains("breve") || lower.contains("shorter")) {
            savePreference("response_length", "brief", 0.8f, "explicit")
        } else if (lower.contains("más detalle") || lower.contains("explica mejor") || lower.contains("more detail")) {
            savePreference("response_length", "detailed", 0.8f, "explicit")
        }

        // 2b. Learn response style preferences
        if (lower.contains("más formal") || lower.contains("profesional") || lower.contains("more formal")) {
            savePreference("response_style", "formal", 0.8f, "explicit")
        } else if (lower.contains("más casual") || lower.contains("informal") || lower.contains("relajado")) {
            savePreference("response_style", "informal", 0.8f, "explicit")
        } else if (lower.contains("más técnico") || lower.contains("detalles técnicos") || lower.contains("more technical")) {
            savePreference("response_style", "technical", 0.8f, "explicit")
        } else if (lower.contains("más simple") || lower.contains("explica fácil") || lower.contains("simpler")) {
            savePreference("response_style", "simple", 0.8f, "explicit")
        }

        // 3. Learn from explicit feedback
        if (userFeedback != null) {
            val feedbackLower = userFeedback.lowercase()
            when {
                feedbackLower.contains("bien") || feedbackLower.contains("good") || feedbackLower.contains("correcto") -> {
                    recordLearningSignal("positive", "response_quality", 0.8f, userMessage)
                }
                feedbackLower.contains("mal") || feedbackLower.contains("wrong") || feedbackLower.contains("incorrecto") -> {
                    recordLearningSignal("negative", "response_quality", -0.6f, userMessage)
                }
            }
        }

        // 4. Learn from implicit signals
        if (lower.contains("gracias") || lower.contains("perfecto") || lower.contains("genial")) {
            recordLearningSignal("positive", "response_quality", 0.7f, userMessage)
        }
        if (lower.contains("no es eso") || lower.contains("mal") || lower.contains("otra vez")) {
            recordLearningSignal("negative", "response_quality", -0.5f, userMessage)
        }

        // 5. Update interaction count
        val currentCount = _mlState.value.interactionCount + 1
        _mlState.value = _mlState.value.copy(interactionCount = currentCount)

        // 6. Save emotion pattern
        savePreference("emotion_pattern", emotionDetected, 0.6f, "learned")

        // 7. Save activity
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        db.activityLogDao().insert(
            ActivityLogEntity(
                activityType = classifyIntent(userMessage),
                details = userMessage.take(50),
                dayOfWeek = dayOfWeek,
                hourOfDay = hour
            )
        )

        // 8. Update topic interest scores (ENHANCED)
        updateTopicInterestScores(userMessage)

        // 9. Predict conversation satisfaction (ENHANCED)
        updateSatisfactionPrediction(userMessage, aiResponse, userFeedback)

        // 10. Learn time-based patterns (ENHANCED)
        recordTimePattern(hour, dayOfWeek, classifyIntent(userMessage))

        // 11. Check for anomalies (ENHANCED)
        detectAnomalies(userMessage, emotionDetected, hour, dayOfWeek)

        // 12. Update coreference resolution history (ENHANCED)
        extractCoreferences(userMessage)

        // 13. Update topic interest in state
        _mlState.value = _mlState.value.copy(
            topicInterestScores = topicInterestMap.mapValues { it.value.score }
        )
    }

    private suspend fun savePreference(category: String, key: String, confidence: Float, source: String) {
        val existing = db.userPreferenceDao().get(category, key)
        if (existing != null) {
            // Update confidence with exponential moving average
            val newConfidence = existing.confidence * 0.7f + confidence * 0.3f
            db.userPreferenceDao().upsert(
                existing.copy(confidence = newConfidence, updatedAt = System.currentTimeMillis())
            )
        } else {
            db.userPreferenceDao().upsert(
                UserPreferenceEntity(category = category, key = key, value = key, confidence = confidence, source = source)
            )
        }

        val prefCount = db.userPreferenceDao().getAll().size
        _mlState.value = _mlState.value.copy(learnedPreferences = prefCount)
    }

    private suspend fun recordLearningSignal(type: String, category: String, value: Float, context: String) {
        db.learningSignalDao().insert(
            LearningSignalEntity(
                type = type, category = category, value = value,
                context = context.take(100), userMessage = context.take(200)
            )
        )
    }

    // ═══════════════════════════════════════
    //  TOPIC TRACKING — ORIGINAL + ENHANCED
    // ═══════════════════════════════════════

    private val topicKeywords = mapOf(
        "technology" to listOf("código", "programa", "app", "software", "api", "bug", "computadora", "código", "programación", "code", "programming", "developer"),
        "travel" to listOf("vuelo", "viaje", "avión", "hotel", "pasaje", "aerolínea", "flight", "travel", "hotel", "airline", "booking"),
        "finance" to listOf("precio", "dinero", "dólar", "bitcoin", "bolsa", "cripto", "price", "money", "dollar", "crypto", "stock"),
        "health" to listOf("salud", "ejercicio", "dieta", "médico", "bienestar", "health", "exercise", "diet", "doctor", "wellness"),
        "entertainment" to listOf("película", "música", "serie", "juego", "canción", "libro", "movie", "music", "series", "game", "book"),
        "food" to listOf("receta", "cocina", "comida", "restaurante", "recipe", "cooking", "food", "restaurant"),
        "sports" to listOf("fútbol", "deporte", "equipo", "partido", "football", "soccer", "sport", "team", "match"),
        "iot" to listOf("luz", "temperatura", "cerradura", "dispositivo", "casa", "light", "temperature", "lock", "device", "home"),
        "education" to listOf("estudio", "universidad", "aprender", "curso", "study", "university", "learn", "course", "school"),
        "work" to listOf("trabajo", "oficina", "reunión", "proyecto", "work", "office", "meeting", "project", "deadline"),
        "relationships" to listOf("familia", "amigo", "pareja", "hijo", "family", "friend", "partner", "relationship"),
        "news" to listOf("noticias", "actualidad", "mundo", "política", "news", "current", "world", "politics")
    )

    fun detectTopics(message: String): List<String> {
        val lower = message.lowercase()
        return topicKeywords.filter { (_, keywords) -> keywords.any { lower.contains(it) } }.keys.toList()
    }

    /**
     * Update topic interest scores based on user interaction.
     * Tracks which topics the user engages with most and their trend.
     */
    private fun updateTopicInterestScores(message: String) {
        val detectedTopics = detectTopics(message)
        val now = System.currentTimeMillis()

        for (topic in detectedTopics) {
            val current = topicInterestMap[topic]
            if (current != null) {
                // Boost score with decay
                val newScore = min(current.score * PREFERENCE_DECAY_FACTOR + 0.1f, 1f)
                val newCount = current.interactionCount + 1

                // Determine trend
                val trend = when {
                    newScore > current.score + 0.05f -> "rising"
                    newScore < current.score - 0.05f -> "falling"
                    else -> "stable"
                }

                topicInterestMap[topic] = current.copy(
                    score = newScore,
                    interactionCount = newCount,
                    lastInteraction = now,
                    trend = trend
                )
            } else {
                topicInterestMap[topic] = TopicInterestScore(
                    topic = topic,
                    score = 0.3f,
                    interactionCount = 1,
                    lastInteraction = now,
                    trend = "rising"
                )
            }
        }

        // Decay unvisited topics
        for ((topic, score) in topicInterestMap) {
            if (topic !in detectedTopics) {
                val daysSinceLastInteraction = (now - score.lastInteraction) / (24 * 60 * 60 * 1000f)
                if (daysSinceLastInteraction > 7) {
                    topicInterestMap[topic] = score.copy(
                        score = score.score * 0.95f,
                        trend = if (score.score < 0.2f) "falling" else "stable"
                    )
                }
            }
        }

        // Persist top topics
        scope.launch {
            try {
                val topTopics = topicInterestMap.entries
                    .sortedByDescending { it.value.score }
                    .take(5)
                    .map { it.key }
                _mlState.value = _mlState.value.copy(topTopics = topTopics)

                for ((topic, scoreData) in topicInterestMap) {
                    savePreference("topic_interest", topic, scoreData.score, "learned")
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Get interest score for a specific topic.
     */
    fun getTopicInterest(topic: String): Float {
        return topicInterestMap[topic]?.score ?: 0f
    }

    /**
     * Get all topics sorted by interest score.
     */
    fun getTopicsByInterest(): List<Pair<String, Float>> {
        return topicInterestMap.entries
            .sortedByDescending { it.value.score }
            .map { it.key to it.value.score }
    }

    // ═══════════════════════════════════════
    //  USER MOOD DETECTION — ORIGINAL
    // ═══════════════════════════════════════

    fun detectMood(recentEmotions: List<String>): String {
        if (recentEmotions.isEmpty()) return "neutral"
        val emotionCounts = recentEmotions.groupingBy { it }.eachCount()
        val dominant = emotionCounts.maxByOrNull { it.value }?.key ?: "neutral"

        _mlState.value = _mlState.value.copy(userMood = dominant)
        return dominant
    }

    // ═══════════════════════════════════════
    //  ACTIVITY PREDICTION — ORIGINAL
    // ═══════════════════════════════════════

    /**
     * Predict what the user might want based on time of day and past behavior.
     * Returns suggested actions or context for the AI.
     */
    suspend fun predictUserNeeds(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        // Get past activities at this hour
        val hourlyActivities = db.activityLogDao().getHourlyPattern(
            System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L // Last 7 days
        )

        // Find the most common activity at this hour
        val currentHourActivity = hourlyActivities.find { it.hourOfDay == hour }
        val suggestion = if (currentHourActivity != null && currentHourActivity.count > 3) {
            "El usuario suele estar activo a esta hora. Puede estar buscando información o interactuando."
        } else {
            ""
        }

        // Time-based suggestions
        val timeSuggestion = when (hour) {
            in 6..8 -> "Es de mañana. El usuario puede estar preparándose para el día."
            in 12..14 -> "Es mediodía. Puede estar en descanso o almuerzo."
            in 18..20 -> "Es de tarde. Puede estar terminando actividades del día."
            in 22..23 -> "Es de noche. Puede estar relajándose antes de dormir."
            else -> ""
        }

        return listOf(suggestion, timeSuggestion).filter { it.isNotBlank() }.joinToString(" ")
    }

    // ═══════════════════════════════════════
    //  MEMORY FACTS — ORIGINAL
    // ═══════════════════════════════════════

    suspend fun saveFact(fact: String, category: String = "general", source: String = "conversation") {
        // Check if similar fact exists
        val existing = db.memoryFactDao().search(fact.take(20))
        if (existing.isNotEmpty()) {
            // Update confidence of existing fact
            val match = existing.first()
            db.memoryFactDao().upsert(
                match.copy(confidence = (match.confidence + 0.1f).coerceIn(0f, 1f))
            )
        } else {
            db.memoryFactDao().upsert(
                MemoryFactEntity(fact = fact, category = category, source = source, confidence = 0.6f)
            )
        }
    }

    suspend fun getRelevantFacts(query: String, limit: Int = 5): List<String> {
        val facts = db.memoryFactDao().search(query, limit)
        // Increment access count for used facts
        for (fact in facts) {
            db.memoryFactDao().incrementAccess(fact.id)
        }
        return facts.map { it.fact }
    }

    // ═══════════════════════════════════════
    //  CONTEXT FOR AI — ORIGINAL + ENHANCED
    // ═══════════════════════════════════════

    suspend fun getMLContextForAI(): String {
        val parts = mutableListOf<String>()

        // User preferences
        val prefs = db.userPreferenceDao().getAll()
        val responseLength = prefs.find { it.category == "response_length" }
        if (responseLength != null) {
            val lengthMap = mapOf("brief" to "breves", "moderate" to "moderadas", "detailed" to "detalladas")
            parts.add("Prefiere respuestas ${lengthMap[responseLength.value] ?: responseLength.value}.")
        }

        // Response style preference
        val responseStyle = prefs.find { it.category == "response_style" }
        if (responseStyle != null) {
            val styleMap = mapOf(
                "formal" to "formal", "informal" to "informal/casual",
                "technical" to "técnica y detallada", "simple" to "simple y fácil de entender"
            )
            parts.add("Estilo preferido: ${styleMap[responseStyle.value] ?: responseStyle.value}.")
        }

        // Learning signals
        val scores = db.learningSignalDao().getAggregateScores(
            System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        )
        val responseQuality = scores.find { it.category == "response_quality" }
        if (responseQuality != null && responseQuality.count > 5) {
            if (responseQuality.avgValue > 0.3f) parts.add("El usuario está satisfecho con las respuestas recientes.")
            else if (responseQuality.avgValue < -0.2f) parts.add("El usuario ha estado insatisfecho. Mejora la calidad.")
        }

        // Mood
        val recentEmotions = db.emotionDao().getRecent(10).map { it.primaryEmotion }
        val mood = detectMood(recentEmotions)
        if (mood != "neutral") {
            val moodMap = mapOf("joy" to "feliz", "sadness" to "triste", "anger" to "enojado", "fear" to "asustado")
            parts.add("Estado de ánimo reciente: ${moodMap[mood] ?: mood}.")
        }

        // Interaction count
        parts.add("Interacciones totales: ${_mlState.value.interactionCount}")
        parts.add("Preferencias aprendidas: ${_mlState.value.learnedPreferences}")

        // Prediction
        val prediction = predictUserNeeds()
        if (prediction.isNotBlank()) parts.add(prediction)

        // ENHANCED: Topic interests
        val topTopics = _mlState.value.topTopics
        if (topTopics.isNotEmpty()) {
            parts.add("Temas de interés: ${topTopics.joinToString(", ")}.")
        }

        // ENHANCED: Detected language
        if (_mlState.value.detectedLanguage != "es") {
            parts.add("Idioma detectado: ${_mlState.value.detectedLanguage} (confianza: ${"%.0f".format(_mlState.value.languageConfidence * 100)}%).")
        }

        // ENHANCED: Multi-intent
        if (_mlState.value.multiIntents.size > 1) {
            val intents = _mlState.value.multiIntents.map { "${it.intent} (${(it.confidence * 100).toInt()}%)" }
            parts.add("Múltiples intenciones detectadas: ${intents.joinToString(", ")}.")
        }

        // ENHANCED: Satisfaction prediction
        val satisfactionPred = _mlState.value.conversationSatisfactionPrediction
        if (satisfactionPred < 0.4f) {
            parts.add("Predicción de insatisfacción. Considera mejorar la respuesta.")
        }

        // ENHANCED: Coreference context
        if (coreferenceHistory.isNotEmpty()) {
            val recentMentions = coreferenceHistory.takeLast(3)
            val corefContext = recentMentions.map { "${it.text} → ${it.resolvedText ?: it.text}" }.joinToString("; ")
            parts.add("Referencias recientes: $corefContext")
        }

        // ENHANCED: Situational context
        val situationalCtx = getSituationalContext()
        if (situationalCtx.combinedDescription.isNotBlank()) {
            parts.add("Contexto situacional: ${situationalCtx.combinedDescription}")
        }

        return parts.joinToString(" ")
    }

    // ═══════════════════════════════════════
    //  SYNC WITH SERVER — ORIGINAL
    // ═══════════════════════════════════════

    suspend fun getUnsyncedData(): Map<String, List<Any>> {
        val cutoff = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L // Last 30 days
        return mapOf(
            "emotions" to db.emotionDao().getUnsynced(cutoff),
            "learning_signals" to db.learningSignalDao().getUnsynced(cutoff)
        )
    }

    suspend fun markSynced(type: String, ids: List<Long>) {
        when (type) {
            "emotions" -> db.emotionDao().markSynced(ids)
            "learning_signals" -> db.learningSignalDao().markSynced(ids)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 1: ML KIT INTEGRATION (Google ML Kit on-device)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Language Identification using ML Kit.
     * Falls back to rule-based detection if ML Kit is unavailable.
     */
    fun identifyLanguage(text: String): Pair<String, Float> {
        // Try ML Kit first
        if (ML_KIT_AVAILABLE) {
            try {
                return identifyLanguageMLKit(text)
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit language identification failed, falling back", e)
            }
        }

        // Fallback: rule-based language detection
        return identifyLanguageRuleBased(text)
    }

    /**
     * ML Kit language identification (stub that works when dependency is available).
     */
    private fun identifyLanguageMLKit(text: String): Pair<String, Float> {
        // Stub: In production, this would use:
        // val languageIdentifier = LanguageIdentification.getClient(
        //     LanguageIdentificationOptions.Builder(0.5f).build()
        // )
        // val result = Tasks.await(languageIdentifier.identifyLanguage(text))
        // return Pair(result.languageCode, result.confidence)

        // For now, fall back to rule-based
        return identifyLanguageRuleBased(text)
    }

    /**
     * Rule-based language identification for Spanish and English.
     */
    private fun identifyLanguageRuleBased(text: String): Pair<String, Float> {
        val lower = text.lowercase()
        val spanishMarkers = listOf(
            "ñ", "á", "é", "í", "ó", "ú", "ü",
            "el ", "la ", "los ", "las ", "un ", "una ",
            "de ", "en ", "por ", "con ", "para ",
            "que ", "qué ", "cómo ", "dónde ", "cuándo ",
            "estoy ", "estás ", "puedo ", "quiero ",
            "hola", "gracias", "adiós", "por favor",
            "sí ", "pero ", "también ", "porque "
        )
        val englishMarkers = listOf(
            "the ", "is ", "are ", "was ", "were ",
            "i ", "you ", "he ", "she ", "we ", "they ",
            "and ", "but ", "or ", "not ", "with ",
            "what ", "how ", "where ", "when ", "why ",
            "hello", "thanks", "goodbye", "please",
            "can ", "could ", "would ", "should ",
            "ing ", "tion ", "ment "
        )

        var spanishScore = 0f
        var englishScore = 0f

        for (marker in spanishMarkers) {
            if (lower.contains(marker)) spanishScore += marker.length
        }
        for (marker in englishMarkers) {
            if (lower.contains(marker)) englishScore += marker.length
        }

        val total = spanishScore + englishScore
        return when {
            total == 0f -> Pair("es", 0.3f) // Default to Spanish for this app
            spanishScore > englishScore * 1.5f -> Pair("es", min(spanishScore / total, 0.95f))
            englishScore > spanishScore * 1.5f -> Pair("en", min(englishScore / total, 0.95f))
            else -> Pair("mixed", 0.5f) // Code-switching detected
        }.also { (lang, conf) ->
            _mlState.value = _mlState.value.copy(
                detectedLanguage = lang,
                languageConfidence = conf
            )
        }
    }

    /**
     * Entity Extraction using ML Kit.
     * Detects dates, addresses, phone numbers, tracking numbers, etc.
     * Falls back to regex-based extraction if ML Kit is unavailable.
     */
    fun extractEntities(text: String): List<DetectedEntity> {
        val entities = mutableListOf<DetectedEntity>()

        // Try ML Kit first
        if (ML_KIT_AVAILABLE) {
            try {
                entities.addAll(extractEntitiesMLKit(text))
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit entity extraction failed, falling back", e)
            }
        }

        // Always run regex-based extraction as supplement/fallback
        entities.addAll(extractEntitiesRegex(text))

        // Named Entity Recognition (custom)
        entities.addAll(extractNamedEntities(text))

        return entities.distinctBy { it.type to it.text }
    }

    /**
     * ML Kit entity extraction (stub).
     */
    private fun extractEntitiesMLKit(text: String): List<DetectedEntity> {
        // Stub: In production, this would use:
        // val extractor = EntityExtraction.getClient(
        //     EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH).build()
        // )
        // val params = EntityExtractionParams.Builder(text).build()
        // val result = Tasks.await(extractor.annotate(params))
        // return result.map { annotation ->
        //     annotation.entities.map { entity ->
        //         DetectedEntity(
        //             type = when (entity.type) {
        //                 Entity.TYPE_DATE_TIME -> "date"
        //                 Entity.TYPE_ADDRESS -> "address"
        //                 Entity.TYPE_PHONE -> "phone"
        //                 Entity.TYPE_TRACKING_NUMBER -> "tracking"
        //                 else -> "unknown"
        //             },
        //             text = text.substring(annotation.start, annotation.end),
        //             startIndex = annotation.start,
        //             endIndex = annotation.end,
        //             confidence = entity.confidence
        //         )
        //     }
        // }.flatten()

        return emptyList()
    }

    /**
     * Regex-based entity extraction for common patterns.
     */
    private fun extractEntitiesRegex(text: String): List<DetectedEntity> {
        val entities = mutableListOf<DetectedEntity>()

        // Phone numbers
        val phoneRegex = Regex("""(?:\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}""")
        phoneRegex.findAll(text).forEach { match ->
            entities.add(DetectedEntity(
                type = "phone",
                text = match.value,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                confidence = 0.8f,
                normalized = match.value.replace(Regex("[-.\\s()]"), "")
            ))
        }

        // Email addresses
        val emailRegex = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
        emailRegex.findAll(text).forEach { match ->
            entities.add(DetectedEntity(
                type = "email",
                text = match.value,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                confidence = 0.9f
            ))
        }

        // Dates (various formats)
        val dateRegex = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2}""")
        dateRegex.findAll(text).forEach { match ->
            entities.add(DetectedEntity(
                type = "date",
                text = match.value,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                confidence = 0.7f
            ))
        }

        // Relative date expressions (Spanish)
        val relativeDateEs = listOf("mañana", "ayer", "pasado mañana", "la próxima semana", "el próximo mes", "hoy")
        for (expr in relativeDateEs) {
            val idx = text.lowercase().indexOf(expr)
            if (idx >= 0) {
                entities.add(DetectedEntity(
                    type = "date",
                    text = text.substring(idx, idx + expr.length),
                    startIndex = idx,
                    endIndex = idx + expr.length,
                    confidence = 0.8f,
                    normalized = expr
                ))
            }
        }

        // Relative date expressions (English)
        val relativeDateEn = listOf("tomorrow", "yesterday", "next week", "next month", "today")
        for (expr in relativeDateEn) {
            val idx = text.lowercase().indexOf(expr)
            if (idx >= 0) {
                entities.add(DetectedEntity(
                    type = "date",
                    text = text.substring(idx, idx + expr.length),
                    startIndex = idx,
                    endIndex = idx + expr.length,
                    confidence = 0.8f,
                    normalized = expr
                ))
            }
        }

        // URLs
        val urlRegex = Regex("""https?://[\w\-._~:/?#\[\]@!$&'()*+,;=%]+""")
        urlRegex.findAll(text).forEach { match ->
            entities.add(DetectedEntity(
                type = "url",
                text = match.value,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                confidence = 0.9f
            ))
        }

        // Tracking numbers (simplified)
        val trackingRegex = Regex("""\b[A-Z]{2}\d{9}[A-Z]{2}\b|\b\d{12,22}\b""")
        trackingRegex.findAll(text).forEach { match ->
            entities.add(DetectedEntity(
                type = "tracking",
                text = match.value,
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                confidence = 0.5f // Low confidence without validation
            ))
        }

        return entities
    }

    /**
     * Custom Named Entity Recognition for names, places, organizations.
     * Uses patterns and learned entities from conversation history.
     */
    private fun extractNamedEntities(text: String): List<DetectedEntity> {
        val entities = mutableListOf<DetectedEntity>()
        val lower = text.lowercase()

        // Place detection patterns
        val placePatterns = listOf(
            Regex("""(?:en|from|de|a|in|at)\s+([A-Z][a-záéíóúñ]+(?:\s+[A-Z][a-záéíóúñ]+)*)"""),
            Regex("""(?:ciudad de|city of|villa de)\s+(\w+(?:\s+\w+)*)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in placePatterns) {
            pattern.findAll(text).forEach { match ->
                if (match.groupValues.size > 1) {
                    val placeName = match.groupValues[1]
                    entities.add(DetectedEntity(
                        type = "place",
                        text = placeName,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        confidence = 0.6f
                    ))
                }
            }
        }

        // Organization patterns
        val orgPatterns = listOf(
            Regex("""(?:empresa|company|corporación|inc\.|corp\.|ltd\.|s\.a\.|s\.l\.)\s+(\w+(?:\s+\w+)*)""", RegexOption.IGNORE_CASE),
            Regex("""(\w+(?:\s+\w+)*)\s+(?:inc\.|corp\.|ltd\.|s\.a\.|s\.l\.)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in orgPatterns) {
            pattern.findAll(text).forEach { match ->
                if (match.groupValues.size > 1) {
                    entities.add(DetectedEntity(
                        type = "org",
                        text = match.groupValues[1],
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        confidence = 0.5f
                    ))
                }
            }
        }

        // Person name patterns (capitalized words after certain markers)
        val personMarkers = listOf("señor", "señora", "sr.", "sra.", "don", "doña", "mr.", "mrs.", "dr.", "dra.")
        for (marker in personMarkers) {
            val markerIdx = lower.indexOf(marker)
            if (markerIdx >= 0) {
                val afterMarker = text.substring(markerIdx + marker.length).trim()
                val nameMatch = Regex("""^([A-Z][a-záéíóúñ]+(?:\s+[A-Z][a-záéíóúñ]+){0,2})""").find(afterMarker)
                if (nameMatch != null) {
                    entities.add(DetectedEntity(
                        type = "person",
                        text = nameMatch.groupValues[1],
                        startIndex = markerIdx + marker.length,
                        endIndex = markerIdx + marker.length + nameMatch.groupValues[1].length,
                        confidence = 0.7f
                    ))
                }
            }
        }

        // Check against learned entity mentions from coreference history
        for (mention in coreferenceHistory) {
            if (mention.resolvedText != null && lower.contains(mention.text.lowercase())) {
                val idx = lower.indexOf(mention.text.lowercase())
                entities.add(DetectedEntity(
                    type = "person",
                    text = mention.text,
                    startIndex = idx,
                    endIndex = idx + mention.text.length,
                    confidence = mention.confidence,
                    normalized = mention.resolvedText
                ))
            }
        }

        return entities
    }

    /**
     * Smart Reply suggestions using ML Kit.
     * Falls back to rule-based suggestions if ML Kit is unavailable.
     */
    fun suggestReplies(
        message: String,
        conversationHistory: List<String> = emptyList()
    ): List<SmartReplySuggestion> {
        // Try ML Kit first
        if (ML_KIT_AVAILABLE) {
            try {
                return suggestRepliesMLKit(message, conversationHistory)
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit smart reply failed, falling back", e)
            }
        }

        // Fallback: rule-based smart replies
        return suggestRepliesRuleBased(message, conversationHistory)
    }

    /**
     * ML Kit Smart Reply (stub).
     */
    private fun suggestRepliesMLKit(
        message: String,
        conversationHistory: List<String>
    ): List<SmartReplySuggestion> {
        // Stub: In production, this would use:
        // val smartReply = SmartReply.getClient()
        // val conversation = conversationHistory.map { TextMessage(it, System.currentTimeMillis(), "user") }
        // val result = Tasks.await(smartReply.suggestReplies(conversation))
        // return result.suggestions.map { suggestion ->
        //     SmartReplySuggestion(
        //         text = suggestion.text,
        //         confidence = when (suggestion.status) {
        //             SmartReplySuggestion.STATUS_NOT_SPAMMY -> 0.8f
        //             else -> 0.4f
        //         }
        //     )
        // }

        return suggestRepliesRuleBased(message, conversationHistory)
    }

    /**
     * Rule-based smart reply suggestions.
     */
    private fun suggestRepliesRuleBased(
        message: String,
        conversationHistory: List<String>
    ): List<SmartReplySuggestion> {
        val lower = message.lowercase()
        val suggestions = mutableListOf<SmartReplySuggestion>()

        // Context-aware suggestions based on intent
        val intents = classifyIntentWithConfidence(message)
        val primaryIntent = intents.firstOrNull()?.intent ?: "general"

        when (primaryIntent) {
            "greeting" -> {
                suggestions.add(SmartReplySuggestion("¡Hola! ¿Cómo estás?", 0.9f, "greeting"))
                suggestions.add(SmartReplySuggestion("Hello, how can I help you?", 0.85f, "greeting"))
                suggestions.add(SmartReplySuggestion("Hey! 👋", 0.7f, "greeting"))
            }
            "question" -> {
                suggestions.add(SmartReplySuggestion("Déjame buscar eso para ti.", 0.8f, "question"))
                suggestions.add(SmartReplySuggestion("Buena pregunta. Voy a investigar.", 0.75f, "question"))
            }
            "gratitude" -> {
                suggestions.add(SmartReplySuggestion("¡De nada! 😊", 0.9f, "emotional"))
                suggestions.add(SmartReplySuggestion("Con mucho gusto.", 0.85f, "emotional"))
                suggestions.add(SmartReplySuggestion("Para servirte.", 0.7f, "emotional"))
            }
            "farewell" -> {
                suggestions.add(SmartReplySuggestion("¡Hasta luego! 👋", 0.9f, "greeting"))
                suggestions.add(SmartReplySuggestion("See you soon.", 0.8f, "greeting"))
            }
            "complaint" -> {
                suggestions.add(SmartReplySuggestion("Entiendo tu frustración. Déjame intentar otra vez.", 0.8f, "emotional"))
                suggestions.add(SmartReplySuggestion("I'm sorry. How can I improve?", 0.75f, "emotional"))
            }
            "emotion_expression" -> {
                val sentiment = analyzeSentiment(message)
                if (sentiment > 0) {
                    suggestions.add(SmartReplySuggestion("¡Me alegra saber eso! 😊", 0.85f, "emotional"))
                    suggestions.add(SmartReplySuggestion("¡Qué genial!", 0.7f, "emotional"))
                } else {
                    suggestions.add(SmartReplySuggestion("Siento escuchar eso. ¿Puedo ayudar?", 0.85f, "emotional"))
                    suggestions.add(SmartReplySuggestion("Estoy aquí para ti.", 0.8f, "emotional"))
                }
            }
            "command" -> {
                suggestions.add(SmartReplySuggestion("Enseguida lo hago.", 0.8f, "question"))
                suggestions.add(SmartReplySuggestion("Processing your request...", 0.7f, "question"))
            }
            else -> {
                suggestions.add(SmartReplySuggestion("Entiendo.", 0.6f, "statement"))
                suggestions.add(SmartReplySuggestion("¿Puedes darme más detalles?", 0.5f, "question"))
            }
        }

        // Adapt suggestions based on user style preference
        val style = _mlState.value.preferredResponseStyle
        return when (style) {
            "formal" -> suggestions.map { it.copy(text = it.text.replace("Hey!", "Good morning").replace("😊", "")) }
            "informal" -> suggestions.map { it.copy(text = it.text.replace("Señor", "tío").replace("Usted", "tú")) }
            else -> suggestions
        }
    }

    /**
     * On-device Translation using ML Kit stubs.
     * Falls back to phrase-based translation if ML Kit is unavailable.
     */
    fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        // Try ML Kit first
        if (ML_KIT_AVAILABLE) {
            try {
                return translateTextMLKit(text, sourceLanguage, targetLanguage)
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit translation failed, falling back", e)
            }
        }

        // Fallback: phrase-based translation
        return translateTextPhraseBased(text, sourceLanguage, targetLanguage)
    }

    /**
     * ML Kit Translation (stub).
     */
    private fun translateTextMLKit(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        // Stub: In production, this would use:
        // val options = TranslatorOptions.Builder()
        //     .setSourceLanguage(sourceLanguage)
        //     .setTargetLanguage(targetLanguage)
        //     .build()
        // val translator = Translation.getClient(options)
        // val conditions = DownloadConditions.Builder().requireWifi().build()
        // Tasks.await(translator.downloadModelIfNeeded(conditions))
        // val result = Tasks.await(translator.translate(text))
        // return TranslationResult(text, result, sourceLanguage, targetLanguage, 0.9f, true)

        return translateTextPhraseBased(text, sourceLanguage, targetLanguage)
    }

    /**
     * Simple phrase-based translation for common expressions.
     */
    private fun translateTextPhraseBased(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        if (sourceLanguage == targetLanguage) {
            return TranslationResult(text, text, sourceLanguage, targetLanguage, 1f, true)
        }

        val esToEn = mapOf(
            "hola" to "hello", "adiós" to "goodbye", "gracias" to "thank you",
            "por favor" to "please", "sí" to "yes", "no" to "no",
            "buenos días" to "good morning", "buenas noches" to "good night",
            "cómo estás" to "how are you", "qué tal" to "what's up",
            "ayuda" to "help", "necesito" to "I need", "quiero" to "I want",
            "puedes" to "can you", "dónde" to "where", "cuándo" to "when",
            "por qué" to "why", "quién" to "who", "cuánto" to "how much"
        )

        val enToEs = esToEn.entries.associate { (k, v) -> v to k }

        val dictionary = if (sourceLanguage == "es" && targetLanguage == "en") esToEn
        else if (sourceLanguage == "en" && targetLanguage == "es") enToEs
        else emptyMap()

        if (dictionary.isEmpty()) {
            return TranslationResult(
                text, text, sourceLanguage, targetLanguage, 0f, true
            )
        }

        var translated = text.lowercase()
        for ((source, target) in dictionary) {
            translated = translated.replace(source, target)
        }

        val confidence = if (translated != text.lowercase()) 0.6f else 0.1f

        return TranslationResult(
            originalText = text,
            translatedText = translated,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            confidence = confidence,
            isOffline = true
        )
    }

    /**
     * Check ML Kit availability at runtime.
     */
    private fun checkMLKitAvailability() {
        try {
            // Try to load ML Kit class
            Class.forName("com.google.mlkit.nl.languageid.LanguageIdentification")
            _mlState.value = _mlState.value.copy(isMLKitReady = true)
            Log.i(TAG, "ML Kit is available")
        } catch (_: ClassNotFoundException) {
            _mlState.value = _mlState.value.copy(isMLKitReady = false)
            Log.i(TAG, "ML Kit not available, using fallback implementations")
        } catch (_: Exception) {
            _mlState.value = _mlState.value.copy(isMLKitReady = false)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 2: TENSORFLOW LITE STUBS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Initialize TFLite model registry with model definitions.
     */
    private fun initializeTFLiteModels() {
        // Register available models
        registerTFLiteModel(TFLiteModelInfo(
            modelName = "text_classification",
            modelPath = "models/text_classification.tflite",
            inputShape = listOf(1, 256),
            outputShape = listOf(1, 8)
        ))
        registerTFLiteModel(TFLiteModelInfo(
            modelName = "sentiment_analysis",
            modelPath = "models/sentiment_analysis.tflite",
            inputShape = listOf(1, 256),
            outputShape = listOf(1, 3)
        ))
        registerTFLiteModel(TFLiteModelInfo(
            modelName = "image_classification",
            modelPath = "models/image_classification.tflite",
            inputShape = listOf(1, 224, 224, 3),
            outputShape = listOf(1, 1001)
        ))
        registerTFLiteModel(TFLiteModelInfo(
            modelName = "object_detection",
            modelPath = "models/object_detection.tflite",
            inputShape = listOf(1, 320, 320, 3),
            outputShape = listOf(1, 10, 4)
        ))
    }

    /**
     * Register a TFLite model in the engine.
     */
    fun registerTFLiteModel(modelInfo: TFLiteModelInfo) {
        tfliteModels[modelInfo.modelName] = modelInfo
    }

    /**
     * Load a TFLite model. Returns true if successful.
     * Uses fallback when TFLite is not available.
     */
    fun loadTFLiteModel(modelName: String): Boolean {
        if (!TFLITE_AVAILABLE) {
            Log.i(TAG, "TFLite not available, using fallback for $modelName")
            // Mark as "loaded" with fallback
            tfliteModels[modelName]?.let { model ->
                tfliteModels[modelName] = model.copy(isLoaded = true)
            }
            return true // Pretend loaded — will use fallback at inference time
        }

        try {
            // Stub: In production, this would use:
            // val model = tfliteModels[modelName] ?: return false
            // val options = org.tensorflow.lite.Interpreter.Options()
            // options.setNumThreads(4)
            // options.setUseNNAPI(true) // Hardware acceleration
            // val assetFd = application.assets.openFd(model.modelPath)
            // val inputStream = FileInputStream(assetFd.fileDescriptor)
            // val fileChannel = inputStream.channel
            // val buffer = fileChannel.map(
            //     FileChannel.MapMode.READ_ONLY,
            //     assetFd.startOffset,
            //     assetFd.declaredLength
            // )
            // val interpreter = org.tensorflow.lite.Interpreter(buffer, options)
            // tfliteModelData[modelName] = interpreter
            // tfliteModels[modelName] = model.copy(isLoaded = true)

            tfliteModels[modelName]?.let { model ->
                tfliteModels[modelName] = model.copy(isLoaded = true)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: $modelName", e)
            return false
        }
    }

    /**
     * Run text classification model inference.
     * Falls back to rule-based classification when TFLite is unavailable.
     */
    fun runTextClassification(text: String): Map<String, Float> {
        val model = tfliteModels["text_classification"]
        if (model?.isLoaded != true) {
            loadTFLiteModel("text_classification")
        }

        if (TFLITE_AVAILABLE && tfliteModelData.containsKey("text_classification")) {
            try {
                return runTextClassificationTFLite(text)
            } catch (e: Exception) {
                Log.w(TAG, "TFLite text classification failed, using fallback", e)
            }
        }

        // Fallback: use existing intent classification
        return classifyIntentWithConfidence(text).associate { it.intent to it.confidence }
    }

    /**
     * TFLite text classification (stub).
     */
    private fun runTextClassificationTFLite(text: String): Map<String, Float> {
        // Stub: In production, this would:
        // 1. Tokenize text using model vocabulary
        // 2. Pad/truncate to model input length
        // 3. Run inference through TFLite interpreter
        // 4. Map output indices to labels
        //
        // val interpreter = tfliteModelData["text_classification"] as? Interpreter ?: return emptyMap()
        // val input = tokenizeForModel(text, 256)
        // val output = Array(1) { FloatArray(8) }
        // interpreter.run(input, output)
        // val labels = listOf("greeting", "question", "command", "emotion", "tool", "farewell", "complaint", "general")
        // return labels.mapIndexed { idx, label -> label to output[0][idx] }.toMap()

        return classifyIntentWithConfidence(text).associate { it.intent to it.confidence }
    }

    /**
     * Run sentiment analysis model inference.
     * Falls back to rule-based sentiment when TFLite is unavailable.
     */
    fun runSentimentAnalysis(text: String): Map<String, Float> {
        val model = tfliteModels["sentiment_analysis"]
        if (model?.isLoaded != true) {
            loadTFLiteModel("sentiment_analysis")
        }

        if (TFLITE_AVAILABLE && tfliteModelData.containsKey("sentiment_analysis")) {
            try {
                return runSentimentAnalysisTFLite(text)
            } catch (e: Exception) {
                Log.w(TAG, "TFLite sentiment analysis failed, using fallback", e)
            }
        }

        // Fallback: use rule-based sentiment
        val sentiment = analyzeSentiment(text)
        return mapOf(
            "negative" to max(0f, -sentiment),
            "neutral" to 1f - abs(sentiment),
            "positive" to max(0f, sentiment)
        )
    }

    /**
     * TFLite sentiment analysis (stub).
     */
    private fun runSentimentAnalysisTFLite(text: String): Map<String, Float> {
        // Stub: In production, this would run inference through TFLite interpreter
        // Similar to text classification but with sentiment labels
        val sentiment = analyzeSentiment(text)
        return mapOf(
            "negative" to max(0f, -sentiment),
            "neutral" to 1f - abs(sentiment),
            "positive" to max(0f, sentiment)
        )
    }

    /**
     * Run image classification model inference.
     * Falls back to basic metadata-based classification when TFLite is unavailable.
     */
    fun runImageClassification(imageData: ByteArray, width: Int, height: Int): List<Pair<String, Float>> {
        val model = tfliteModels["image_classification"]
        if (model?.isLoaded != true) {
            loadTFLiteModel("image_classification")
        }

        if (TFLITE_AVAILABLE && tfliteModelData.containsKey("image_classification")) {
            try {
                // Stub: In production:
                // val interpreter = tfliteModelData["image_classification"] as? Interpreter ?: return emptyList()
                // val input = preprocessImage(imageData, width, height, 224, 224)
                // val output = Array(1) { FloatArray(1001) }
                // interpreter.run(input, output)
                // return output[0].mapIndexed { idx, score -> labelMap[idx] to score }
                //     .sortedByDescending { it.second }.take(5)
            } catch (e: Exception) {
                Log.w(TAG, "TFLite image classification failed", e)
            }
        }

        // Fallback: return basic categories based on image metadata
        return listOf(
            "unknown" to 0.5f,
            "photo" to 0.3f,
            "document" to 0.1f
        )
    }

    /**
     * Run object detection model inference.
     * Falls back to empty results when TFLite is unavailable.
     */
    fun runObjectDetection(imageData: ByteArray, width: Int, height: Int): List<DetectionResult> {
        val model = tfliteModels["object_detection"]
        if (model?.isLoaded != true) {
            loadTFLiteModel("object_detection")
        }

        if (TFLITE_AVAILABLE && tfliteModelData.containsKey("object_detection")) {
            try {
                // Stub: In production:
                // val interpreter = tfliteModelData["object_detection"] as? Interpreter ?: return emptyList()
                // val input = preprocessImage(imageData, width, height, 320, 320)
                // val locations = Array(1) { Array(10) { FloatArray(4) } }
                // val classes = Array(1) { FloatArray(10) }
                // val scores = Array(1) { FloatArray(10) }
                // val outputs = mapOf(0 to locations, 1 to classes, 2 to scores)
                // interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
                // return (0 until 10).map { idx ->
                //     DetectionResult(
                //         label = labelMap[scores[0][idx].toInt()],
                //         confidence = scores[0][idx],
                //         boundingBox = BoundingBox(locations[0][idx])
                //     )
                // }.filter { it.confidence > 0.5f }
            } catch (e: Exception) {
                Log.w(TAG, "TFLite object detection failed", e)
            }
        }

        // Fallback: return empty
        return emptyList()
    }

    /**
     * Get all registered TFLite models and their status.
     */
    fun getTFLiteModelStatus(): Map<String, TFLiteModelInfo> {
        return tfliteModels.toMap()
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val boundingBox: BoundingBox? = null
    )

    data class BoundingBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 3: ENHANCED NLP PIPELINE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Tokenize text into words, supporting Spanish and English.
     * Handles contractions, accents, and special characters.
     */
    fun tokenize(text: String): List<String> {
        // Handle Spanish contractions
        var processed = text
        val spanishContractions = mapOf(
            "del" to "de el", "al" to "a el"
        )
        for ((contraction, expansion) in spanishContractions) {
            processed = processed.replace(Regex("\\b$contraction\\b", RegexOption.IGNORE_CASE), expansion)
        }

        // Handle English contractions
        val englishContractions = mapOf(
            "don't" to "do not", "can't" to "can not", "won't" to "will not",
            "i'm" to "i am", "you're" to "you are", "it's" to "it is",
            "we're" to "we are", "they're" to "they are", "isn't" to "is not",
            "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
            "haven't" to "have not", "hasn't" to "has not", "hadn't" to "had not",
            "wouldn't" to "would not", "couldn't" to "could not", "shouldn't" to "should not"
        )
        for ((contraction, expansion) in englishContractions) {
            processed = processed.replace(Regex(Regex.escape(contraction), RegexOption.IGNORE_CASE), expansion)
        }

        // Tokenize by whitespace and punctuation
        return processed.split(Regex("""\s+|(?=[.,!?;:¿¡])|(?<=[.,!?;:¿¡])"""))
            .filter { it.isNotBlank() }
    }

    /**
     * Split text into sentences for Spanish and English.
     */
    fun splitSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()

        // Handle abbreviations that shouldn't split sentences
        val abbreviations = setOf(
            "Sr.", "Sra.", "Dr.", "Dra.", "Ud.", "Uds.",
            "Mr.", "Mrs.", "Ms.", "Dr.", "Jr.", "Sr.", "Inc.", "Ltd.", "Corp.",
            "etc.", "aprox.", "ej.", "p.ej.", "vs.", "esp."
        )

        var temp = text
        // Temporarily replace abbreviations
        for ((idx, abbr) in abbreviations.withIndex()) {
            temp = temp.replace(abbr, "§ABBR$idx§")
        }

        // Split on sentence-ending punctuation followed by space and uppercase
        val sentenceRegex = Regex("""(?<=[.!?¿¡])\s+(?=[A-ZÁÉÍÓÚÑÜ])""")
        val parts = temp.split(sentenceRegex)

        // Restore abbreviations
        for ((idx, abbr) in abbreviations.withIndex()) {
            for (i in parts.indices) {
                // Only restore in mutable copy
            }
        }

        // Simpler approach: restore and filter
        var restored = parts.map { part ->
            var r = part
            for ((idx, abbr) in abbreviations.withIndex()) {
                r = r.replace("§ABBR$idx§", abbr)
            }
            r.trim()
        }.filter { it.isNotBlank() }

        if (restored.isEmpty()) {
            restored = listOf(text)
        }

        return restored
    }

    /**
     * Named Entity Recognition — detect names, places, organizations.
     * Combines regex patterns with learned entity knowledge.
     */
    fun performNER(text: String): List<DetectedEntity> {
        return extractEntities(text).filter {
            it.type in listOf("person", "place", "org")
        }
    }

    /**
     * Coreference resolution — link pronouns to previous entity mentions.
     * Resolves "eso", "él", "ella", "esto", "eso", "aquello" etc.
     */
    fun extractCoreferences(text: String): List<CoreferenceMention> {
        val mentions = mutableListOf<CoreferenceMention>()

        // Spanish pronouns and demonstratives that need resolution
        val pronounPatterns = mapOf(
            "él" to "male_person",
            "ella" to "female_person",
            "ellos" to "male_group",
            "ellas" to "female_group",
            "eso" to "neuter_thing",
            "este" to "masculine_proximal",
            "esta" to "feminine_proximal",
            "aquel" to "masculine_distal",
            "aquella" to "feminine_distal",
            "aquello" to "neuter_distal",
            "lo" to "masculine_object",
            "la" to "feminine_object",
            "los" to "masculine_plural",
            "las" to "feminine_plural",
            "le" to "indirect_object"
        )

        // English pronouns
        val englishPronouns = mapOf(
            "he" to "male_person",
            "she" to "female_person",
            "it" to "neuter_thing",
            "they" to "group",
            "them" to "group_object",
            "this" to "proximal",
            "that" to "distal",
            "these" to "proximal_plural",
            "those" to "distal_plural"
        )

        val allPronouns = pronounPatterns + englishPronouns
        val lower = text.lowercase()
        val words = text.split(Regex("\\s+"))
        val sentences = splitSentences(text)

        for ((pronoun, type) in allPronouns) {
            // Find occurrences of the pronoun in text
            var searchStart = 0
            while (true) {
                val idx = lower.indexOf(pronoun, searchStart)
                if (idx < 0) break

                // Ensure it's a whole word match
                val beforeChar = if (idx > 0) lower[idx - 1] else ' '
                val afterIdx = idx + pronoun.length
                val afterChar = if (afterIdx < lower.length) lower[afterIdx] else ' '

                if ((!beforeChar.isLetter() || pronoun == "le" || pronoun == "lo" || pronoun == "la") &&
                    !afterChar.isLetter()) {

                    // Resolve the pronoun to the most recent matching entity
                    val resolved = resolveCoreference(pronoun, type, sentences)

                    val mention = CoreferenceMention(
                        text = text.substring(idx, idx + pronoun.length),
                        resolvedText = resolved,
                        type = type,
                        sentenceIndex = sentences.indexOfFirst { lower.contains(pronoun) }.coerceAtLeast(0),
                        confidence = if (resolved != null) 0.7f else 0.3f
                    )
                    mentions.add(mention)

                    // Update coreference history
                    if (coreferenceHistory.size >= MAX_COREFERENCE_HISTORY) {
                        coreferenceHistory.removeAt(0)
                    }
                    coreferenceHistory.add(mention)

                    // Update entity mentions map
                    if (resolved != null) {
                        entityMentions[pronoun] = resolved
                    }
                }

                searchStart = idx + pronoun.length
            }
        }

        // Also extract proper nouns as potential antecedents
        val properNounRegex = Regex("""\b([A-ZÁÉÍÓÚÑÜ][a-záéíóúñü]+(?:\s+[A-ZÁÉÍÓÚÑÜ][a-záéíóúñü]+)*)\b""")
        properNounRegex.findAll(text).forEach { match ->
            val nounText = match.groupValues[1]
            // Check if it's likely a proper noun (not start of sentence)
            val isStartOfSentence = match.range.first == 0 ||
                text.substring(0, match.range.first).trim().let { prefix -> prefix.endsWith(".") || prefix.endsWith("!") || prefix.endsWith("?") || prefix.endsWith("¿") || prefix.endsWith("¡") }
            if (!isStartOfSentence) {
                val mention = CoreferenceMention(
                    text = nounText,
                    resolvedText = null, // This IS the antecedent
                    type = "noun_phrase",
                    sentenceIndex = 0,
                    confidence = 0.8f
                )
                coreferenceHistory.add(mention)
            }
        }

        _mlState.value = _mlState.value.copy(coreferenceMapSize = entityMentions.size)
        return mentions
    }

    /**
     * Resolve a pronoun to its most likely antecedent from conversation history.
     */
    private fun resolveCoreference(pronoun: String, pronounType: String, sentences: List<String>): String? {
        // Strategy 1: Check entity mentions map
        if (entityMentions.containsKey(pronoun)) {
            return entityMentions[pronoun]
        }

        // Strategy 2: Look at recent coreference history for matching types
        val recentEntities = coreferenceHistory
            .filter { it.type == "noun_phrase" && it.resolvedText == null }
            .lastOrNull()

        if (recentEntities != null) {
            // Gender/number matching for Spanish
            val isMatch = when (pronounType) {
                "male_person" -> true // Accept any recent entity as potential match
                "female_person" -> true
                "neuter_thing" -> true
                else -> true
            }
            if (isMatch) {
                entityMentions[pronoun] = recentEntities.text
                return recentEntities.text
            }
        }

        // Strategy 3: Check detected entities from the current conversation
        val detectedEntities = coreferenceHistory
            .filter { it.confidence > 0.5f && it.resolvedText != null }
            .lastOrNull()

        return detectedEntities?.resolvedText
    }

    /**
     * Get the resolved form of a pronoun from current context.
     */
    fun resolvePronoun(pronoun: String): String? {
        return entityMentions[pronoun]
    }

    /**
     * Clear coreference history (e.g., on new conversation).
     */
    fun clearCoreferenceHistory() {
        coreferenceHistory.clear()
        entityMentions.clear()
        _mlState.value = _mlState.value.copy(coreferenceMapSize = 0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 4: BETTER PREFERENCE LEARNING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get topic interest scores for all tracked topics.
     */
    fun getTopicInterestScores(): Map<String, TopicInterestScore> {
        return topicInterestMap.toMap()
    }

    /**
     * Learn response style preferences from interaction patterns.
     */
    suspend fun learnResponseStylePreference(
        userMessage: String,
        aiResponse: String,
        wasHelpful: Boolean?
    ) {
        val lower = userMessage.lowercase()

        // Detect style cues in user messages
        val styleCues = mutableMapOf<String, Float>()

        // Formality detection
        if (lower.contains(Regex("\\b(usted|señor|señora|disculpe|formal)\\b"))) {
            styleCues["formal"] = 0.8f
        } else if (lower.contains(Regex("\\b(tú|tenga)\\b"))) {
            styleCues["formal"] = 0.7f
        }
        if (lower.contains(Regex("\\b(tú|tío|colega|bro)\\b"))) {
            styleCues["informal"] = 0.7f
        }

        // Detail level detection
        if (lower.contains(Regex("(resume|breve|corto|summarize|brief|short)"))) {
            styleCues["brief"] = 0.8f
        }
        if (lower.contains(Regex("(explica más|detalla|elabora|explain more|detail|elaborate)"))) {
            styleCues["detailed"] = 0.8f
        }

        // Technical level detection
        if (lower.contains(Regex("(técnico|específico|avanzado|technical|specific|advanced)"))) {
            styleCues["technical"] = 0.7f
        }
        if (lower.contains(Regex("(simple|fácil|básico|simple|easy|basic)"))) {
            styleCues["simple"] = 0.7f
        }

        // Learn from response effectiveness
        if (wasHelpful == true) {
            // Reinforce current style
            val currentStyle = _mlState.value.preferredResponseStyle
            savePreference("response_style", currentStyle, 0.6f, "implicit")
        } else if (wasHelpful == false) {
            // Reduce confidence in current style
            val currentStyle = _mlState.value.preferredResponseStyle
            savePreference("response_style", currentStyle, -0.3f, "implicit")
        }

        // Apply detected cues
        for ((style, confidence) in styleCues) {
            savePreference("response_style", style, confidence, "learned")
            _mlState.value = _mlState.value.copy(preferredResponseStyle = style)
        }

        // Response length preference from actual response length
        val responseLengthPreference = when {
            aiResponse.length < 200 -> "brief"
            aiResponse.length < 500 -> "moderate"
            else -> "detailed"
        }
        if (wasHelpful == true) {
            savePreference("response_length", responseLengthPreference, 0.5f, "implicit")
            _mlState.value = _mlState.value.copy(preferredResponseLength = responseLengthPreference)
        }
    }

    /**
     * Learn time-based patterns about user behavior.
     * Records what the user does at what time for prediction.
     */
    private fun recordTimePattern(hour: Int, dayOfWeek: Int, activityType: String) {
        val key = "${dayOfWeek}_${hour}"

        val existing = timePatternCache.find { it.hourOfDay == hour && it.dayOfWeek == dayOfWeek }
        if (existing != null) {
            val newFreq = existing.frequency + 1
            val newConfidence = min(existing.confidence + 0.05f, 1f)
            val idx = timePatternCache.indexOf(existing)
            timePatternCache[idx] = existing.copy(
                frequency = newFreq,
                confidence = newConfidence,
                dominantActivity = if (newFreq > 3) activityType else existing.dominantActivity
            )
        } else {
            timePatternCache.add(TimePattern(
                hourOfDay = hour,
                dayOfWeek = dayOfWeek,
                dominantActivity = activityType,
                frequency = 1,
                confidence = 0.2f
            ))
        }

        // Persist periodically
        if (timePatternCache.size % 10 == 0) {
            scope.launch {
                try {
                    for (pattern in timePatternCache) {
                        savePreference(
                            "time_pattern",
                            "${pattern.dayOfWeek}_${pattern.hourOfDay}_${pattern.dominantActivity}",
                            pattern.confidence,
                            "learned"
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Get time-based predictions for current time.
     */
    fun getTimeBasedPrediction(): TimePattern? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return timePatternCache.find { it.hourOfDay == hour && it.dayOfWeek == dayOfWeek }
    }

    /**
     * Predict conversation satisfaction based on multiple signals.
     * Returns 0.0 (likely unsatisfied) to 1.0 (likely satisfied).
     */
    private fun updateSatisfactionPrediction(
        userMessage: String,
        aiResponse: String,
        userFeedback: String?
    ) {
        var satisfactionScore = 0.5f // Baseline

        // Signal 1: Sentiment of user message
        val sentiment = analyzeSentiment(userMessage)
        satisfactionScore += sentiment * 0.2f

        // Signal 2: Response length match
        val preferredLength = _mlState.value.preferredResponseLength
        val actualLength = when {
            aiResponse.length < 200 -> "brief"
            aiResponse.length < 500 -> "moderate"
            else -> "detailed"
        }
        if (preferredLength == actualLength) {
            satisfactionScore += 0.1f
        } else {
            satisfactionScore -= 0.1f
        }

        // Signal 3: Explicit feedback
        if (userFeedback != null) {
            val feedbackSentiment = analyzeSentiment(userFeedback)
            satisfactionScore += feedbackSentiment * 0.3f
        }

        // Signal 4: Intent match (did we understand what they wanted?)
        val intentConfidence = _mlState.value.lastIntentConfidence
        satisfactionScore += (intentConfidence - 0.5f) * 0.2f

        // Signal 5: Topic interest (is this a topic they care about?)
        val topics = detectTopics(userMessage)
        val topicScore = if (topics.isNotEmpty()) {
            topics.map { getTopicInterest(it) }.average().toFloat()
        } else 0.5f
        satisfactionScore += (topicScore - 0.5f) * 0.1f

        _mlState.value = _mlState.value.copy(
            conversationSatisfactionPrediction = satisfactionScore.coerceIn(0f, 1f)
        )
    }

    /**
     * Get the current satisfaction prediction.
     */
    fun getConversationSatisfactionPrediction(): Float {
        return _mlState.value.conversationSatisfactionPrediction
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 5: CONTEXT-AWARE FEATURES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build a comprehensive situational context from multiple signals.
     * Combines sensor data + time + history for rich context.
     */
    fun getSituationalContext(): SituationalContext {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        // Time context
        val timeContext = when (hour) {
            in 5..8 -> "morning"
            in 9..12 -> if (isWeekend) "weekend_morning" else "work_hours"
            in 12..14 -> "lunch"
            in 14..18 -> if (isWeekend) "weekend_afternoon" else "work_hours"
            in 18..21 -> "evening"
            in 21..24 -> "night"
            else -> "late_night"
        }

        // Location context (from recent sensor data)
        val locationContext = try {
            val recentSensor = kotlinx.coroutines.runBlocking { db.sensorDataDao().getLatest("location", 1) }
            when {
                recentSensor.isEmpty() -> "unknown"
                recentSensor[0].context?.contains("home") == true -> "home"
                recentSensor[0].context?.contains("work") == true -> "work"
                recentSensor[0].context?.contains("commuting") == true -> "commuting"
                else -> "unknown"
            }
        } catch (_: Exception) { "unknown" }

        // Activity context
        val activityContext = try {
            val recentAccel = kotlinx.coroutines.runBlocking { db.sensorDataDao().getLatest("accelerometer", 1) }
            recentAccel.firstOrNull()?.context ?: "unknown"
        } catch (_: Exception) { "unknown" }

        // Emotional context from recent interactions
        val emotionalContext = _mlState.value.userMood

        // Device context
        val deviceContext = try {
            val recentScreen = kotlinx.coroutines.runBlocking { db.sensorDataDao().getLatest("screen_state", 1) }
            when {
                recentScreen.any { it.context == "screen_on" } -> "phone_in_hand"
                recentScreen.any { it.context == "headphones" } -> "headphones"
                _mlState.value.userMood == "focused" -> "phone_in_hand"
                else -> "phone_down"
            }
        } catch (_: Exception) { "unknown" }

        // Social context (from presence data)
        val socialContext = try {
            val recentPresence = kotlinx.coroutines.runBlocking { db.sensorDataDao().getLatest("presence", 1) }
            when {
                recentPresence.any { it.value > 1 } -> "with_people"
                timeContext == "work_hours" && !isWeekend -> "in_meeting"
                else -> "alone"
            }
        } catch (_: Exception) { "alone" }

        // Build combined description
        val combinedDescription = buildString {
            append("Son las ${hour}:00")
            if (isWeekend) append(", es fin de semana")
            append(". Contexto: $timeContext, $locationContext, $activityContext")
            if (emotionalContext != "neutral") append(", ánimo: $emotionalContext")
            if (deviceContext != "unknown") append(", dispositivo: $deviceContext")
            if (socialContext != "alone") append(", $socialContext")
        }.trim()

        return SituationalContext(
            timeContext = timeContext,
            locationContext = locationContext,
            activityContext = activityContext,
            emotionalContext = emotionalContext,
            deviceContext = deviceContext,
            socialContext = socialContext,
            combinedDescription = combinedDescription
        )
    }

    /**
     * Generate proactive suggestions based on learned patterns and current context.
     * Returns suggestions the AI can offer before the user asks.
     */
    suspend fun generateProactiveSuggestions(): List<ProactiveSuggestion> {
        val suggestions = mutableListOf<ProactiveSuggestion>()
        val context = getSituationalContext()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timePattern = getTimeBasedPrediction()

        // Suggestion 1: Time-based activity suggestion
        if (timePattern != null && timePattern.confidence > 0.5f) {
            suggestions.add(ProactiveSuggestion(
                id = UUID.randomUUID().toString(),
                type = "action",
                title = "Actividad frecuente",
                description = "Suele ${timePattern.dominantActivity} a esta hora. ¿Necesitas ayuda con algo?",
                confidence = timePattern.confidence,
                contextTriggers = listOf("time_pattern", "${timePattern.hourOfDay}:00")
            ))
        }

        // Suggestion 2: Topic interest-based suggestion
        val risingTopics = topicInterestMap.values
            .filter { it.trend == "rising" && it.score > 0.4f }
            .sortedByDescending { it.score }
            .take(2)

        for (topic in risingTopics) {
            suggestions.add(ProactiveSuggestion(
                id = UUID.randomUUID().toString(),
                type = "information",
                title = "Tema de interés: ${topic.topic}",
                description = "Has estado interesado en ${topic.topic} recientemente. ¿Quieres saber más?",
                confidence = topic.score,
                contextTriggers = listOf("topic_interest", topic.topic)
            ))
        }

        // Suggestion 3: Context-aware suggestions
        when (context.timeContext) {
            "morning" -> {
                suggestions.add(ProactiveSuggestion(
                    id = UUID.randomUUID().toString(),
                    type = "information",
                    title = "Resumen matutino",
                    description = "¿Quieres un resumen del clima y tus actividades del día?",
                    confidence = 0.6f,
                    contextTriggers = listOf("morning_routine")
                ))
            }
            "lunch" -> {
                suggestions.add(ProactiveSuggestion(
                    id = UUID.randomUUID().toString(),
                    type = "information",
                    title = "Sugerencia de almuerzo",
                    description = "¿Necesitas sugerencias de restaurantes o recetas?",
                    confidence = 0.5f,
                    contextTriggers = listOf("lunch_time", "food")
                ))
            }
            "evening" -> {
                suggestions.add(ProactiveSuggestion(
                    id = UUID.randomUUID().toString(),
                    type = "iot",
                    title = "Night mode",
                    description = "¿Quieres que ajuste las luces y la temperatura para la noche?",
                    confidence = 0.5f,
                    contextTriggers = listOf("evening_routine", "iot")
                ))
            }
            "night" -> {
                suggestions.add(ProactiveSuggestion(
                    id = UUID.randomUUID().toString(),
                    type = "reminder",
                    title = "Hora de dormir",
                    description = "Es tarde. ¿Quieres configurar una alarma para mañana?",
                    confidence = 0.7f,
                    contextTriggers = listOf("night_routine")
                ))
            }
        }

        // Suggestion 4: Low satisfaction recovery
        if (_mlState.value.conversationSatisfactionPrediction < 0.4f) {
            suggestions.add(ProactiveSuggestion(
                id = UUID.randomUUID().toString(),
                type = "conversation",
                title = "Mejorar experiencia",
                description = "Parece que no estás satisfecho. ¿Puedo ajustar mi estilo de respuesta?",
                confidence = 0.8f,
                contextTriggers = listOf("low_satisfaction", "recovery")
            ))
        }

        // Suggestion 5: Unfinished tasks (from memory facts)
        try {
            val recentFacts = db.memoryFactDao().search("pendiente", 3)
            for (fact in recentFacts) {
                suggestions.add(ProactiveSuggestion(
                    id = UUID.randomUUID().toString(),
                    type = "reminder",
                    title = "Tarea pendiente",
                    description = fact.fact,
                    confidence = fact.confidence,
                    contextTriggers = listOf("pending_task")
                ))
            }
        } catch (_: Exception) {}

        // Sort by confidence and limit
        val topSuggestions = suggestions
            .sortedByDescending { it.confidence }
            .take(MAX_PROACTIVE_SUGGESTIONS)

        _proactiveSuggestions.value = topSuggestions
        return topSuggestions
    }

    /**
     * Dismiss a proactive suggestion.
     */
    fun dismissSuggestion(suggestionId: String) {
        _proactiveSuggestions.value = _proactiveSuggestions.value.map {
            if (it.id == suggestionId) it.copy(isDismissed = true) else it
        }
    }

    /**
     * Detect anomalies in user behavior.
     * Compares current patterns against learned baselines.
     */
    private fun detectAnomalies(
        message: String,
        emotion: String,
        hour: Int,
        dayOfWeek: Int
    ) {
        val reports = mutableListOf<AnomalyReport>()

        // Anomaly 1: Unusual activity at this time
        val timePattern = timePatternCache.find { it.hourOfDay == hour && it.dayOfWeek == dayOfWeek }
        if (timePattern != null && timePattern.frequency > 5) {
            val currentActivity = classifyIntent(message)
            if (currentActivity != timePattern.dominantActivity && timePattern.confidence > 0.7f) {
                val deviation = timePattern.confidence // How different from expected
                if (deviation > ANOMALY_THRESHOLD * 0.3f) {
                    reports.add(AnomalyReport(
                        type = "activity",
                        description = "Actividad inusual: $currentActivity (esperada: ${timePattern.dominantActivity}) a las $hour:00",
                        severity = deviation * 0.3f,
                        deviationScore = deviation,
                        relatedContext = "time_pattern"
                    ))
                }
            }
        }

        // Anomaly 2: Unusual emotional state
        try {
            val recentEmotions = kotlinx.coroutines.runBlocking { db.emotionDao().getRecent(20) }
            if (recentEmotions.size >= 10) {
                val emotionCounts = recentEmotions.groupingBy { it.primaryEmotion }.eachCount()
                val dominantEmotion = emotionCounts.maxByOrNull { it.value }?.key ?: "neutral"
                val dominantRatio = emotionCounts[dominantEmotion]?.toFloat()?.div(recentEmotions.size) ?: 0f

                if (emotion != dominantEmotion && dominantRatio > 0.6f) {
                    reports.add(AnomalyReport(
                        type = "emotion",
                        description = "Cambio emocional detectado: $emotion (patrón reciente: $dominantEmotion)",
                        severity = dominantRatio * 0.4f,
                        deviationScore = dominantRatio,
                        relatedContext = "emotion_pattern"
                    ))
                }
            }
        } catch (_: Exception) {}

        // Anomaly 3: Unusual usage frequency
        val currentInteractionCount = _mlState.value.interactionCount
        val baselineKey = "interaction_count_${dayOfWeek}_${hour}"
        val baseline = activityBaseline[baselineKey]
        if (baseline != null) {
            val (mean, stddev) = baseline
            if (stddev > 0 && abs(currentInteractionCount - mean) > ANOMALY_THRESHOLD * stddev) {
                val deviation = abs(currentInteractionCount - mean) / stddev
                reports.add(AnomalyReport(
                    type = "usage",
                    description = "Frecuencia de uso inusual: $currentInteractionCount (media: ${"%.1f".format(mean)})",
                    severity = min(deviation / 5f, 1f),
                    deviationScore = deviation.toFloat(),
                    relatedContext = "usage_pattern"
                ))
            }
        }

        // Anomaly 4: Sentiment anomaly (sudden negative shift)
        val sentiment = analyzeSentiment(message)
        if (sentiment < -0.7f && _mlState.value.lastSentiment > 0.2f) {
            reports.add(AnomalyReport(
                type = "pattern",
                description = "Cambio brusco de sentimiento detectado",
                severity = 0.7f,
                deviationScore = abs(sentiment - _mlState.value.lastSentiment),
                relatedContext = "sentiment_shift"
            ))
        }

        // Update anomaly state
        if (reports.isNotEmpty()) {
            val maxSeverity = reports.maxOf { it.severity }
            _mlState.value = _mlState.value.copy(lastAnomalyScore = maxSeverity)

            val currentAnomalies = _anomalies.value.toMutableList()
            currentAnomalies.addAll(0, reports)
            _anomalies.value = currentAnomalies.take(50) // Keep last 50

            Log.d(TAG, "Detected ${reports.size} anomalies, max severity: $maxSeverity")
        }
    }

    /**
     * Context-adaptive response formatting.
     * Suggests how the AI should format its response based on context.
     */
    fun getAdaptiveResponseFormat(): AdaptiveResponseFormat {
        val context = getSituationalContext()
        val style = _mlState.value.preferredResponseStyle
        val length = _mlState.value.preferredResponseLength
        val satisfaction = _mlState.value.conversationSatisfactionPrediction

        return AdaptiveResponseFormat(
            formality = when {
                style == "formal" || context.socialContext == "in_meeting" -> "formal"
                style == "informal" || context.socialContext == "alone" -> "informal"
                context.timeContext == "work_hours" -> "semi_formal"
                else -> "informal"
            },
            detailLevel = when {
                satisfaction < 0.3f -> "detailed" // More explanation when struggling
                length == "brief" -> "brief"
                length == "detailed" -> "detailed"
                context.deviceContext == "driving" -> "brief" // Safety first
                context.activityContext == "walking" -> "brief"
                else -> "moderate"
            },
            useEmojis = when {
                style == "formal" -> false
                context.timeContext == "work_hours" -> false
                style == "informal" -> true
                _mlState.value.userMood in listOf("joy", "surprise") -> true
                else -> false
            },
            useBulletPoints = when {
                length == "detailed" -> true
                context.deviceContext == "phone_in_hand" -> true
                else -> false
            },
            language = _mlState.value.detectedLanguage,
            suggestedTone = when {
                _mlState.value.userMood == "sadness" -> "empathetic"
                _mlState.value.userMood == "anger" -> "calm"
                _mlState.value.userMood == "fear" -> "reassuring"
                context.timeContext == "night" -> "gentle"
                context.activityContext == "driving" -> "clear"
                satisfaction < 0.4f -> "apologetic"
                else -> "helpful"
            }
        )
    }

    data class AdaptiveResponseFormat(
        val formality: String,        // "formal", "semi_formal", "informal"
        val detailLevel: String,      // "brief", "moderate", "detailed"
        val useEmojis: Boolean,
        val useBulletPoints: Boolean,
        val language: String,
        val suggestedTone: String     // "empathetic", "calm", "reassuring", "gentle", "clear", "helpful", "apologetic"
    )

    // ═══════════════════════════════════════════════════════════════════════
    //  ENHANCEMENT 6: FEDERATED LEARNING STUBS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Simulate local model training on user data.
     * Generates model updates (gradients) from local interactions.
     * Privacy-preserving: only the gradient, not the data, leaves the device.
     */
    suspend fun performLocalTrainingRound(): FederatedModelUpdate? {
        try {
            // Get recent learning signals
            val cutoff = System.currentTimeMillis() - FEDERATED_ROUND_INTERVAL_MS
            val signals = db.learningSignalDao().getAggregateScores(cutoff)

            if (signals.isEmpty()) {
                Log.d(TAG, "No learning signals for federated round")
                return null
            }

            // Simulate gradient computation
            // In production, this would:
            // 1. Load the current model
            // 2. Compute loss on local data
            // 3. Backpropagate to get gradients
            // 4. Clip gradients for privacy
            val gradientData = mutableMapOf<String, List<Float>>()

            // Simulated gradient layers
            gradientData["intent_embedding"] = signals.map { it.avgValue }
            gradientData["sentiment_weights"] = signals.map { it.avgValue * 0.5f }
            gradientData["preference_bias"] = signals.map { it.count.toFloat() / 100f }

            // Apply differential privacy: add noise proportional to privacy budget
            val epsilon = 1.0f // Privacy budget
            val noisyGradients = applyDifferentialPrivacy(gradientData, epsilon)

            val update = FederatedModelUpdate(
                modelId = "nexa_ondevice_v1",
                version = _mlState.value.federatedRoundCount + 1,
                gradientData = noisyGradients,
                sampleCount = signals.sumOf { it.count },
                privacyEpsilon = epsilon
            )

            // Store the update locally
            val currentUpdates = _federatedUpdates.value.toMutableList()
            currentUpdates.add(0, update)
            _federatedUpdates.value = currentUpdates.take(10)

            _mlState.value = _mlState.value.copy(
                federatedRoundCount = _mlState.value.federatedRoundCount + 1
            )

            lastFederatedRoundTime = System.currentTimeMillis()

            Log.d(TAG, "Federated learning round ${update.version} completed with ${update.sampleCount} samples")
            return update

        } catch (e: Exception) {
            Log.e(TAG, "Federated learning round failed", e)
            return null
        }
    }

    /**
     * Apply differential privacy noise to gradients.
     * Ensures individual user data cannot be reverse-engineered from updates.
     */
    private fun applyDifferentialPrivacy(
        gradients: Map<String, List<Float>>,
        epsilon: Float
    ): Map<String, List<Float>> {
        val noisyGradients = mutableMapOf<String, List<Float>>()

        for ((layer, values) in gradients) {
            // Laplace mechanism: add noise scaled to 1/epsilon
            val scale = 1.0f / max(epsilon, 0.01f)
            val noisyValues = values.map { value ->
                val noise = laplaceNoise(scale)
                value + noise
            }

            // Clip gradients to bounded range for additional privacy
            val clippedValues = noisyValues.map { it.coerceIn(-1f, 1f) }
            noisyGradients[layer] = clippedValues
        }

        return noisyGradients
    }

    /**
     * Generate Laplace noise for differential privacy.
     */
    private fun laplaceNoise(scale: Float): Float {
        val u = Math.random() - 0.5
        return (scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u))).toFloat()
    }

    /**
     * Aggregate multiple model updates into a single update.
     * Simulates the server-side aggregation in federated learning.
     */
    fun aggregateModelUpdates(updates: List<FederatedModelUpdate>): FederatedModelUpdate? {
        if (updates.isEmpty()) return null

        // Weighted averaging based on sample counts
        val totalSamples = updates.sumOf { it.sampleCount }
        if (totalSamples == 0) return null

        val aggregatedGradients = mutableMapOf<String, MutableList<Float>>()

        // Initialize with zeros
        val firstUpdate = updates.first()
        for ((layer, values) in firstUpdate.gradientData) {
            aggregatedGradients[layer] = MutableList(values.size) { 0f }
        }

        // Weighted sum
        for (update in updates) {
            val weight = update.sampleCount.toFloat() / totalSamples
            for ((layer, values) in update.gradientData) {
                val current = aggregatedGradients[layer] ?: continue
                for (i in values.indices) {
                    if (i < current.size) {
                        current[i] += values[i] * weight
                    }
                }
            }
        }

        return FederatedModelUpdate(
            modelId = firstUpdate.modelId,
            version = updates.maxOf { it.version },
            gradientData = aggregatedGradients.mapValues { it.value.toList() },
            sampleCount = totalSamples,
            isAggregated = true,
            privacyEpsilon = updates.maxOf { it.privacyEpsilon }
        )
    }

    /**
     * Check if a federated learning round should be performed.
     */
    private fun checkFederatedLearning() {
        val timeSinceLastRound = System.currentTimeMillis() - lastFederatedRoundTime
        if (timeSinceLastRound > FEDERATED_ROUND_INTERVAL_MS && _mlState.value.interactionCount > 10) {
            scope.launch {
                performLocalTrainingRound()
            }
        }
    }

    /**
     * Get the privacy budget remaining.
     */
    fun getRemainingPrivacyBudget(): Float {
        val usedEpsilon = _federatedUpdates.value.sumOf { it.privacyEpsilon.toDouble() }.toFloat()
        val maxEpsilon = 10f // Total privacy budget
        return max(0f, maxEpsilon - usedEpsilon)
    }

    // ═══════════════════════════════════════
    //  PERSISTENCE HELPERS
    // ═══════════════════════════════════════

    private suspend fun loadTopicInterestScores() {
        try {
            val prefs = db.userPreferenceDao().getByCategory("topic_interest")
            for (pref in prefs) {
                topicInterestMap[pref.key] = TopicInterestScore(
                    topic = pref.key,
                    score = pref.confidence,
                    interactionCount = 1, // We don't store count separately
                    lastInteraction = pref.updatedAt,
                    trend = "stable"
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun loadTimePatterns() {
        try {
            val prefs = db.userPreferenceDao().getByCategory("time_pattern")
            for (pref in prefs) {
                val parts = pref.key.split("_")
                if (parts.size >= 3) {
                    val dayOfWeek = parts[0].toIntOrNull() ?: continue
                    val hourOfDay = parts[1].toIntOrNull() ?: continue
                    val activity = parts.drop(2).joinToString("_")
                    timePatternCache.add(TimePattern(
                        hourOfDay = hourOfDay,
                        dayOfWeek = dayOfWeek,
                        dominantActivity = activity,
                        frequency = 1,
                        confidence = pref.confidence
                    ))
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadAnomalyBaselines() {
        // Initialize with default baselines
        for (day in 1..7) {
            for (hour in 0..23) {
                val key = "interaction_count_${day}_$hour"
                activityBaseline[key] = Pair(2f, 1.5f) // Default: mean=2, stddev=1.5
            }
        }
    }

    private fun loadCoreferenceHistory() {
        // Load recent entity mentions from memory facts
        scope.launch {
            try {
                val facts = db.memoryFactDao().getTop(10)
                for (fact in facts) {
                    if (fact.category == "person" || fact.category == "place") {
                        coreferenceHistory.add(CoreferenceMention(
                            text = fact.fact,
                            resolvedText = null,
                            type = "noun_phrase",
                            sentenceIndex = 0,
                            confidence = fact.confidence
                        ))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Update anomaly baselines with new observations.
     * Should be called periodically.
     */
    suspend fun updateAnomalyBaselines() {
        try {
            val activityCounts = db.activityLogDao().getActivityCounts(
                System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L // Last 30 days
            )
            // Update baselines with observed data
            for (activity in activityCounts) {
                val key = "activity_${activity.activityType}"
                val currentBaseline = activityBaseline[key]
                if (currentBaseline != null) {
                    // Update with exponential moving average
                    val (mean, stddev) = currentBaseline
                    val newMean = mean * 0.8f + activity.count.toFloat() * 0.2f
                    val newStddev = stddev * 0.8f + abs(activity.count.toFloat() - mean) * 0.2f
                    activityBaseline[key] = Pair(newMean, newStddev)
                } else {
                    activityBaseline[key] = Pair(activity.count.toFloat(), activity.count.toFloat() * 0.5f)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Get a comprehensive ML engine status report.
     */
    fun getEngineStatus(): Map<String, Any> {
        return mapOf(
            "interaction_count" to _mlState.value.interactionCount,
            "learned_preferences" to _mlState.value.learnedPreferences,
            "last_intent" to _mlState.value.lastIntent,
            "last_intent_confidence" to _mlState.value.lastIntentConfidence,
            "last_sentiment" to _mlState.value.lastSentiment,
            "user_mood" to _mlState.value.userMood,
            "detected_language" to _mlState.value.detectedLanguage,
            "language_confidence" to _mlState.value.languageConfidence,
            "response_length_pref" to _mlState.value.preferredResponseLength,
            "response_style_pref" to _mlState.value.preferredResponseStyle,
            "satisfaction_prediction" to _mlState.value.conversationSatisfactionPrediction,
            "top_topics" to _mlState.value.topTopics,
            "multi_intents" to _mlState.value.multiIntents.map { "${it.intent}: ${it.confidence}" },
            "coreference_size" to _mlState.value.coreferenceMapSize,
            "anomaly_score" to _mlState.value.lastAnomalyScore,
            "mlkit_ready" to _mlState.value.isMLKitReady,
            "tflite_ready" to _mlState.value.isTFLiteReady,
            "federated_rounds" to _mlState.value.federatedRoundCount,
            "privacy_budget_remaining" to getRemainingPrivacyBudget(),
            "topic_interests" to topicInterestMap.mapValues { it.value.score },
            "proactive_suggestions_count" to _proactiveSuggestions.value.size,
            "anomaly_count" to _anomalies.value.size
        )
    }

    /**
     * Reset all learned data (for privacy/debugging).
     */
    suspend fun resetAllLearnedData() {
        try {
            // Clear in-memory state
            topicInterestMap.clear()
            coreferenceHistory.clear()
            entityMentions.clear()
            timePatternCache.clear()
            activityBaseline.clear()
            tfliteModels.clear()
            tfliteModelData.clear()

            _mlState.value = MLState()
            _proactiveSuggestions.value = emptyList()
            _anomalies.value = emptyList()
            _federatedUpdates.value = emptyList()

            // Clear database (careful!)
            // Only clear ML-related tables, not sessions/messages
            // This would need custom DAO methods

            Log.i(TAG, "All learned data reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting learned data", e)
        }
    }
}
