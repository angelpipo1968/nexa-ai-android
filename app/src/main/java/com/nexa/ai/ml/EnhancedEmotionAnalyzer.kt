package com.nexa.ai.ml

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EnhancedEmotionAnalyzer — Advanced emotion detection with visual feedback.
 * Analyzes text for underlying emotions and provides emoji-based visual indicators.
 */

data class EmotionProfile(
    val primaryEmotion: Emotion,
    val secondaryEmotion: Emotion? = null,
    val confidence: Float,
    val valence: Float,       // -1.0 (negative) to +1.0 (positive)
    val arousal: Float,        // 0.0 (calm) to 1.0 (excited)
    val dominance: Float,      // 0.0 (submissive) to 1.0 (dominant)
    val emoji: String,         // Visual emoji representation
    val color: String,         // Hex color for UI theming
    val suggestedTone: String, // Suggested AI response tone
    val triggers: List<String> // Words/phrases that triggered this emotion
)

enum class Emotion(val displayName: String, val emoji: String, val color: String, val tone: String) {
    JOY("Joy", "😊", "#FFD700", "warm and enthusiastic"),
    SADNESS("Sadness", "😢", "#4169E1", "gentle and empathetic"),
    ANGER("Anger", "😠", "#FF4444", "calm and de-escalating"),
    FEAR("Fear", "😨", "#8B008B", "reassuring and supportive"),
    SURPRISE("Surprise", "😲", "#FF8C00", "engaging and informative"),
    DISGUST("Disgust", "🤢", "#006400", "neutral and non-judgmental"),
    TRUST("Trust", "🤝", "#228B22", "reliable and honest"),
    ANTICIPATION("Anticipation", "🤔", "#DDA0DD", "helpful and forward-looking"),
    LOVE("Love", "❤️", "#FF1493", "warm and affectionate"),
    CONFUSION("Confusion", "😕", "#DAA520", "clear and explanatory"),
    FRUSTRATION("Frustration", "😤", "#B22222", "patient and solution-oriented"),
    EXCITEMENT("Excitement", "🤩", "#FF6347", "energetic and encouraging"),
    BOREDOM("Boredom", "😐", "#808080", "engaging and varied"),
    HOPE("Hope", "🌟", "#32CD32", "optimistic and supportive"),
    GRATITUDE("Gratitude", "🙏", "#FFB6C1", "appreciative and humble"),
    NEUTRAL("Neutral", "😐", "#A0A0A0", "neutral and helpful"),
    ANXIETY("Anxiety", "😰", "#9370DB", "calm and grounding"),
    CURIOSITY("Curiosity", "🧐", "#20B2AA", "informative and detailed"),
    DETERMINATION("Determination", "💪", "#8B4513", "motivating and practical"),
    PRIDE("Pride", "🏆", "#DAA520", "acknowledging and validating")
}

class EnhancedEmotionAnalyzer {

    companion object {
        private const val TAG = "EmotionAnalyzer"

        private val EMOTION_LEXICON = mapOf<Emotion, List<String>>(
            Emotion.JOY to listOf("happy", "glad", "great", "wonderful", "amazing", "awesome", "fantastic", "excellent",
                "love", "excited", "delighted", "cheerful", "pleased", "thrilled", "blissful", "joyful",
                "feliz", "contento", "genial", "maravilloso", "increíble", "excelente", "alegre", "encantado",
                "emocionado", "fantástico", "espléndido", "radiante"),
            Emotion.SADNESS to listOf("sad", "unhappy", "depressed", "miserable", "heartbroken", "lonely", "grief",
                "sorrow", "crying", "tears", "disappointed", "hopeless", "devastated", "upset",
                "triste", "infeliz", "deprimido", "solo", "desolado", "llorando", "decepcionado", "desesperanzado"),
            Emotion.ANGER to listOf("angry", "furious", "mad", "enraged", "irritated", "annoyed", "hate",
                "livid", "outraged", "hostile", "aggressive", "frustrated",
                "enojado", "furioso", "molesto", "irritado", "odio", "hostil", "agresivo", "indignado"),
            Emotion.FEAR to listOf("afraid", "scared", "terrified", "anxious", "worried", "nervous", "panic",
                "dread", "frightened", "alarmed", "petrified",
                "asustado", "aterrorizado", "ansioso", "preocupado", "nervioso", "pánico", "miedo", "espantado"),
            Emotion.SURPRISE to listOf("surprised", "shocked", "amazed", "astonished", "unexpected", "wow",
                "incredible", "unbelievable", "stunning",
                "sorprendido", "shockeado", "asombrado", "increíble", "inesperado", "impresionante"),
            Emotion.TRUST to listOf("trust", "believe", "reliable", "honest", "dependable", "faithful",
                "loyal", "sincere", "genuine", "authentic", "credible",
                "confiar", "creíble", "confiable", "honesto", "sincero", "leal", "auténtico"),
            Emotion.LOVE to listOf("love", "adore", "cherish", "passionate", "romantic", "affectionate",
                "caring", "devoted", "tender", "warm",
                "amor", "adorar", "querer", "apasionado", "romántico", "cariñoso", "tierno", "devoto"),
            Emotion.CONFUSION to listOf("confused", "unclear", "puzzled", "lost", "uncertain", "what",
                "how", "why", "don't understand", "doesn't make sense", "complicated",
                "confundido", "no entiendo", "incierto", "perdido", "complicado", "no tiene sentido"),
            Emotion.FRUSTRATION to listOf("frustrated", "annoyed", "stuck", "blocked", "impossible",
                "can't", "doesn't work", "broken", "failed", "wrong",
                "frustrado", "atornado", "imposible", "no funciona", "roto", "fallido", "estresado"),
            Emotion.EXCITEMENT to listOf("excited", "pumped", "stoked", "can't wait", "looking forward",
                "eager", "enthusiastic", "thrilled", "passionate", "energetic",
                "emocionado", "ansioso", "impaciente", "entusiasta", "apasionado", "energético"),
            Emotion.CURIOSITY to listOf("curious", "wonder", "interesting", "how does", "what if", "tell me",
                "explain", "why is", "how come", "I want to know", "fascinating",
                "curioso", "interesante", "cómo funciona", "qué pasa si", "explícame", "por qué"),
            Emotion.GRATITUDE to listOf("thank", "thanks", "grateful", "appreciate", "helpful", "kind",
                "generous", "wonderful", "blessed", "indebted",
                "gracias", "agradecido", "agradecer", "amable", "generoso", "bendecido"),
            Emotion.ANXIETY to listOf("anxious", "worried", "stressed", "overwhelmed", "tense", "uneasy",
                "restless", "nervous", "panic", "dread",
                "ansioso", "preocupado", "estresado", "abrumado", "tenso", "intranquilo", "nervioso"),
            Emotion.HOPE to listOf("hope", "wish", "optimistic", "looking forward", "maybe", "perhaps",
                "someday", "believe", "positive", "bright",
                "esperanza", "desear", "optimista", "quizás", "tal vez", "algún día", "positivo"),
            Emotion.BOREDOM to listOf("bored", "boring", "dull", "tedious", "monotonous", "uninteresting",
                "tiresome", "mundane", "routine", "same old",
                "aburrido", "aburrimiento", "monótono", "tedioso", "rutinario", "soso"),
            Emotion.DETERMINATION to listOf("determined", "will", "committed", "focused", "persistent",
                "resolute", "driven", "motivated", "ambitious", "goal",
                "determinado", "decidido", "comprometido", "enfocado", "persistente", "motivado")
        )

        private val INTENSIFIERS = listOf("very", "extremely", "incredibly", "absolutely", "totally",
            "really", "so", "super", "completely", "utterly", "quite", "rather",
            "muy", "extremadamente", "increíblemente", "absolutamente", "totalmente", "realmente", "súper")

        private val NEGATORS = listOf("not", "don't", "doesn't", "didn't", "won't", "can't", "isn't",
            "aren't", "wasn't", "weren't", "never", "no", "neither", "nor",
            "no", "no es", "no estoy", "no puedo", "nunca", "jamás", "tampoco")
    }

    /**
     * Analyze text for emotional content.
     */
    fun analyzeEmotion(text: String): EmotionProfile {
        val lowerText = text.lowercase()
        val words = lowerText.split(Regex("\\W+"))

        val emotionScores = mutableMapOf<Emotion, Float>()
        val emotionTriggers = mutableMapOf<Emotion, MutableList<String>>()

        // Check each emotion's lexicon
        EMOTION_LEXICON.forEach { (emotion, keywords) ->
            var score = 0f
            val triggers = mutableListOf<String>()

            keywords.forEach { keyword ->
                if (keyword in lowerText) {
                    // Check for negation
                    val keywordIndex = lowerText.indexOf(keyword)
                    val beforeText = lowerText.take(maxOf(0, keywordIndex - 20))
                    val isNegated = NEGATORS.any { it in beforeText }

                    val baseScore = if (isNegated) -0.3f else 0.5f

                    // Check for intensification
                    val isIntensified = INTENSIFIERS.any { it in beforeText }
                    val finalScore = if (isIntensified) baseScore * 1.5f else baseScore

                    score += finalScore
                    if (!isNegated) triggers.add(keyword)
                }
            }

            if (score > 0f) {
                emotionScores[emotion] = score
                emotionTriggers[emotion] = triggers
            }
        }

        // Check for exclamation marks (increase arousal)
        val exclamationCount = text.count { it == '!' }
        val questionCount = text.count { it == '?' }
        val capsRatio = text.filter { it.isUpperCase() }.length.toFloat() / maxOf(text.length, 1)

        // Determine primary and secondary emotions
        val sorted = emotionScores.entries.sortedByDescending { it.value }
        val primary = sorted.firstOrNull()?.key ?: Emotion.NEUTRAL
        val secondary = sorted.getOrNull(1)?.key
        val rawConfidence = sorted.firstOrNull()?.value ?: 0f
        val confidence = (rawConfidence / 3f).coerceIn(0f, 1f)

        // Calculate VAD (Valence-Arousal-Dominance)
        val valence = calculateValence(primary, secondary, rawConfidence)
        val arousal = calculateArousal(exclamationCount, questionCount, capsRatio, rawConfidence)
        val dominance = calculateDominance(primary, rawConfidence)

        return EmotionProfile(
            primaryEmotion = primary,
            secondaryEmotion = secondary,
            confidence = confidence,
            valence = valence,
            arousal = arousal,
            dominance = dominance,
            emoji = primary.emoji,
            color = primary.color,
            suggestedTone = primary.tone,
            triggers = emotionTriggers[primary]?.take(5) ?: emptyList()
        )
    }

    /**
     * Get a contextual prompt addition for the AI based on detected emotion.
     */
    fun getEmotionContext(emotionProfile: EmotionProfile, language: String = "en"): String {
        if (emotionProfile.confidence < 0.15f) return ""

        val isEs = language == "es"
        return buildString {
            append("\n[User Emotion: ${emotionProfile.primaryEmotion.displayName} ")
            append("(confidence: ${(emotionProfile.confidence * 100).toInt()}%)")
            if (emotionProfile.secondaryEmotion != null) {
                append(", Secondary: ${emotionProfile.secondaryEmotion.displayName}")
            }
            append("]")
            append("\n[Response tone suggestion: ${emotionProfile.suggestedTone}]")

            // Add specific guidance based on emotion
            when (emotionProfile.primaryEmotion) {
                Emotion.SADNESS -> append("\n[Be empathetic. Acknowledge their feelings before providing information.]")
                Emotion.ANGER -> append("\n[Stay calm. Validate their frustration. Avoid being defensive.]")
                Emotion.FEAR, Emotion.ANXIETY -> append("\n[Be reassuring. Provide clear, actionable information.]")
                Emotion.CONFUSION -> append("\n[Be clear and step-by-step. Use simple explanations.]")
                Emotion.FRUSTRATION -> append("\n[Be patient. Focus on solutions. Acknowledge the difficulty.]")
                Emotion.EXCITEMENT -> append("\n[Match their energy. Be encouraging.]")
                Emotion.CURIOSITY -> append("\n[Be detailed and informative. Encourage exploration.]")
                Emotion.GRATITUDE -> append("\n[Be humble. Acknowledge their thanks warmly.]")
                else -> {}
            }
        }
    }

    /**
     * Get emoji-based visual feedback string.
     */
    fun getVisualFeedback(profile: EmotionProfile): String {
        val bar = "▓".repeat((profile.valence * 10 + 10).toInt().coerceIn(0, 20)) +
                  "░".repeat(20 - (profile.valence * 10 + 10).toInt().coerceIn(0, 20))
        return "${profile.emoji} ${profile.primaryEmotion.displayName} " +
               "(Confidence: ${(profile.confidence * 100).toInt()}%) " +
               "[${bar}]"
    }

    // ─── VAD Calculation ──────────────────────────────────────

    private fun calculateValence(primary: Emotion, secondary: Emotion?, score: Float): Float {
        val positiveEmotions = setOf(Emotion.JOY, Emotion.LOVE, Emotion.TRUST, Emotion.GRATITUDE, Emotion.HOPE, Emotion.EXCITEMENT, Emotion.PRIDE, Emotion.SURPRISE, Emotion.DETERMINATION)
        val negativeEmotions = setOf(Emotion.SADNESS, Emotion.ANGER, Emotion.FEAR, Emotion.DISGUST, Emotion.FRUSTRATION, Emotion.ANXIETY, Emotion.BOREDOM, Emotion.CONFUSION)

        var valence = when {
            primary in positiveEmotions -> 0.5f + (score * 0.1f)
            primary in negativeEmotions -> -0.5f - (score * 0.1f)
            else -> 0f
        }

        if (secondary != null) {
            valence += when {
                secondary in positiveEmotions -> 0.2f
                secondary in negativeEmotions -> -0.2f
                else -> 0f
            }
        }

        return valence.coerceIn(-1f, 1f)
    }

    private fun calculateArousal(exclamations: Int, questions: Int, capsRatio: Float, score: Float): Float {
        var arousal = score * 0.15f
        arousal += exclamations * 0.1f
        arousal += questions * 0.05f
        arousal += capsRatio * 0.3f
        return arousal.coerceIn(0f, 1f)
    }

    private fun calculateDominance(primary: Emotion, score: Float): Float {
        return when (primary) {
            Emotion.ANGER, Emotion.DETERMINATION, Emotion.PRIDE, Emotion.TRUST -> 0.6f + score * 0.1f
            Emotion.FEAR, Emotion.SADNESS, Emotion.CONFUSION, Emotion.BOREDOM -> 0.2f + score * 0.05f
            else -> 0.4f + score * 0.1f
        }.coerceIn(0f, 1f)
    }
}
