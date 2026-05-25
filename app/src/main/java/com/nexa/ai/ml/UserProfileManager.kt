package com.nexa.ai.ml

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * UserProfileManager — Deep user profiling for personalized AI responses.
 * Builds a comprehensive user profile including vocabulary style, topic preferences,
 * communication patterns, and interaction history.
 */

data class UserProfile(
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val preferredLanguage: String = "en",
    val communicationStyle: CommunicationStyle = CommunicationStyle.BALANCED,
    val vocabularyLevel: VocabularyLevel = VocabularyLevel.STANDARD,
    val preferredResponseLength: ResponseLength = ResponseLength.MEDIUM,
    val topicInterests: Map<String, Float> = emptyMap(), // topic -> interest score (0-1)
    val interactionPatterns: InteractionPatterns = InteractionPatterns(),
    val personalityTraits: Map<String, Float> = emptyMap(), // trait -> score
    val technicalLevel: TechnicalLevel = TechnicalLevel.MODERATE,
    val formalityPreference: FormalityLevel = FormalityLevel.MIXED,
    val favoriteTopics: List<String> = emptyList(),
    val avoidedTopics: List<String> = emptyList(),
    val name: String = "",
    val timeZone: String = "",
    val interactionCount: Int = 0
)

enum class CommunicationStyle(val description: String) {
    CASUAL("Casual and relaxed — uses informal language, emojis, and humor"),
    FORMAL("Formal and professional — uses proper grammar and structure"),
    TECHNICAL("Technical and precise — uses industry terminology"),
    CREATIVE("Creative and expressive — uses metaphors and vivid language"),
    BALANCED("Balanced — adapts to context and switches between styles")
}

enum class VocabularyLevel(val description: String) {
    SIMPLE("Simple words, short sentences, easy to understand"),
    STANDARD("Standard vocabulary, clear and direct"),
    ADVANCED("Advanced vocabulary, nuanced expression"),
    TECHNICAL("Technical jargon, domain-specific terms"),
    ACADEMIC("Academic language, formal and research-oriented")
}

enum class ResponseLength(val description: String, val minWords: Int, val maxWords: Int) {
    BRIEF("Short and to the point", 10, 50),
    MEDIUM("Moderate length with some detail", 50, 200),
    DETAILED("Comprehensive and thorough", 200, 500),
    COMPREHENSIVE("Very thorough, covers all aspects", 500, 2000)
}

enum class TechnicalLevel(val description: String) {
    BEGINNER("Little to no technical knowledge"),
    BASIC("Basic understanding of common technology"),
    MODERATE("Comfortable with technology, some technical knowledge"),
    ADVANCED("Strong technical background, understands complex concepts"),
    EXPERT("Expert-level technical knowledge")
}

enum class FormalityLevel(val description: String) {
    INFORMAL("Informal, friendly, uses first names"),
    MIXED("Adapts formality based on context"),
    FORMAL("Formal, uses titles and proper structure")
}

data class InteractionPatterns(
    val averageMessagesPerSession: Float = 5f,
    val preferredTimeOfDay: String = "afternoon", // morning, afternoon, evening, night
    val averageSessionDuration: Int = 300, // seconds
    val averageResponseReadTime: Int = 10, // seconds
    val frequencyOfVoiceMode: Float = 0.3f, // 0-1
    val frequencyOfQuickActions: Float = 0.2f, // 0-1
    val frequencyOfImageGeneration: Float = 0.1f, // 0-1
    val mostUsedFeatures: List<String> = emptyList(),
    val typicalSessionTopics: List<String> = emptyList()
)

class UserProfileManager {

    companion object {
        private const val TAG = "UserProfileManager"
        private const val MAX_TOPIC_HISTORY = 100
        private const val MIN_INTERACTIONS_FOR_PROFILE = 5
    }

    private var profile: UserProfile = UserProfile(userId = "default")
    private val topicHistory = mutableListOf<Pair<String, Long>>() // topic -> timestamp
    private val messageLengths = mutableListOf<Int>()
    private val sessionStartTimes = mutableListOf<Long>()
    private val vocabularyWords = mutableMapOf<String, Int>() // word -> frequency

    fun initialize(userId: String) {
        profile = profile.copy(userId = userId)
        Log.i(TAG, "UserProfile initialized for: $userId")
    }

    fun getProfile(): UserProfile = profile

    /**
     * Update profile based on a new user interaction.
     */
    fun recordInteraction(
        message: String,
        topic: String = "",
        isVoiceMode: Boolean = false,
        quickActionUsed: String = "",
        responseReadDuration: Int = 0
    ) {
        val now = System.currentTimeMillis()
        profile = profile.copy(
            lastUpdated = now,
            interactionCount = profile.interactionCount + 1
        )

        // Track topic
        if (topic.isNotBlank()) {
            topicHistory.add(topic to now)
            updateTopicInterests()
        }

        // Track message patterns
        messageLengths.add(message.split(" ").size)
        updateResponseLengthPreference(message.split(" ").size)

        // Track vocabulary
        extractVocabulary(message)

        // Track features
        updateInteractionPatterns(isVoiceMode, quickActionUsed, responseReadDuration)

        // Update profile periodically
        if (profile.interactionCount % MIN_INTERACTIONS_FOR_PROFILE == 0) {
            updateDerivedProfile()
        }
    }

    /**
     * Get personalized context for AI prompts.
     */
    fun getContextForAI(language: String = "en"): String {
        if (profile.interactionCount < MIN_INTERACTIONS_FOR_PROFILE) return ""

        val isEs = language == "es"
        return buildString {
            append("\n## User Profile Context\n")
            
            append("• ${if (isEs) "Estilo de comunicación" else "Communication style"}: ${profile.communicationStyle.description}\n")
            append("• ${if (isEs) "Nivel de vocabulario" else "Vocabulary level"}: ${profile.vocabularyLevel.description}\n")
            append("• ${if (isEs) "Longitud preferida" else "Preferred response length"}: ${profile.preferredResponseLength.description}\n")
            append("• ${if (isEs) "Nivel técnico" else "Technical level"}: ${profile.technicalLevel.description}\n")
            append("• ${if (isEs) "Nivel de formalidad" else "Formality"}: ${profile.formalityPreference.description}\n")

            if (profile.topicInterests.isNotEmpty()) {
                val topTopics = profile.topicInterests.entries.sortedByDescending { it.value }.take(5)
                append("• ${if (isEs) "Temas de interés" else "Topic interests"}: ")
                append(topTopics.joinToString(", ") { "${it.key} (${(it.value * 100).toInt()}%)" })
                append("\n")
            }

            if (profile.favoriteTopics.isNotEmpty()) {
                append("• ${if (isEs) "Temas favoritos" else "Favorite topics"}: ${profile.favoriteTopics.joinToString(", ")}\n")
            }

            if (profile.name.isNotBlank()) {
                append("• ${if (isEs) "Nombre del usuario" else "User name"}: ${profile.name}\n")
            }

            append("• ${if (isEs) "Interacciones totales" else "Total interactions"}: ${profile.interactionCount}\n")
        }
    }

    /**
     * Update a specific preference directly.
     */
    fun updatePreference(
        style: CommunicationStyle? = null,
        vocabLevel: VocabularyLevel? = null,
        responseLength: ResponseLength? = null,
        techLevel: TechnicalLevel? = null,
        formality: FormalityLevel? = null,
        name: String? = null
    ) {
        profile = profile.copy(
            communicationStyle = style ?: profile.communicationStyle,
            vocabularyLevel = vocabLevel ?: profile.vocabularyLevel,
            preferredResponseLength = responseLength ?: profile.preferredResponseLength,
            technicalLevel = techLevel ?: profile.technicalLevel,
            formalityPreference = formality ?: profile.formalityPreference,
            name = name ?: profile.name,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun startSession() {
        sessionStartTimes.add(System.currentTimeMillis())
    }

    // ─── Private update methods ───────────────────────────────

    private fun updateTopicInterests() {
        if (topicHistory.isEmpty()) return

        // Decay old topics and boost recent ones
        val now = System.currentTimeMillis()
        val recentTopics = topicHistory.filter { now - it.second < 7 * 24 * 60 * 60 * 1000L } // Last 7 days
        
        val topicScores = mutableMapOf<String, Float>()
        recentTopics.forEach { (topic, timestamp) ->
            val age = (now - timestamp).toFloat() / (7 * 24 * 60 * 60 * 1000f) // 0 (new) to 1 (old)
            val freshness = 1f - age
            val existing = topicScores[topic] ?: 0f
            topicScores[topic] = existing + freshness
        }

        // Normalize
        val maxScore = topicScores.values.maxOrNull() ?: 1f
        val normalized = topicScores.mapValues { it.value / maxScore }

        profile = profile.copy(
            topicInterests = normalized,
            favoriteTopics = normalized.entries.sortedByDescending { it.value }.take(5).map { it.key },
            lastUpdated = now
        )

        // Trim history
        if (topicHistory.size > MAX_TOPIC_HISTORY) {
            topicHistory.removeAll(topicHistory.take(topicHistory.size - MAX_TOPIC_HISTORY))
        }
    }

    private fun updateResponseLengthPreference(userMessageLength: Int) {
        // Infer preferred response length from user's own message length
        val newLength = when {
            userMessageLength <= 5 -> ResponseLength.BRIEF
            userMessageLength <= 15 -> ResponseLength.MEDIUM
            userMessageLength <= 30 -> ResponseLength.DETAILED
            else -> ResponseLength.COMPREHENSIVE
        }

        // Smooth transition (don't change on every message)
        profile = if (messageLengths.size > 10) {
            val avgLength = messageLengths.takeLast(10).average()
            val preferredLength = when {
                avgLength <= 5 -> ResponseLength.BRIEF
                avgLength <= 15 -> ResponseLength.MEDIUM
                avgLength <= 30 -> ResponseLength.DETAILED
                else -> ResponseLength.COMPREHENSIVE
            }
            profile.copy(preferredResponseLength = preferredLength)
        } else {
            profile.copy(preferredResponseLength = newLength)
        }
    }

    private fun extractVocabulary(message: String) {
        val words = message.lowercase().split(Regex("\\W+")).filter { it.length > 4 }
        words.forEach { vocabularyWords[it] = (vocabularyWords[it] ?: 0) + 1 }

        // Update vocabulary level based on complexity
        if (vocabularyWords.size > 50) {
            val complexWords = vocabularyWords.count { (word, _) -> word.length > 8 }
            val ratio = complexWords.toFloat() / vocabularyWords.size

            profile = profile.copy(
                vocabularyLevel = when {
                    ratio > 0.3f -> VocabularyLevel.ADVANCED
                    ratio > 0.15f -> VocabularyLevel.STANDARD
                    else -> VocabularyLevel.SIMPLE
                }
            )
        }
    }

    private fun updateInteractionPatterns(voiceMode: Boolean, quickAction: String, readDuration: Int) {
        val patterns = profile.interactionPatterns

        // Track voice mode frequency
        val voiceFreq = if (profile.interactionCount > 1) {
            val totalVoice = profile.interactionPatterns.frequencyOfVoiceMode * (profile.interactionCount - 1) + (if (voiceMode) 1f else 0f)
            totalVoice / profile.interactionCount
        } else if (voiceMode) 1f else 0f

        // Update time of day preference
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            hour < 21 -> "evening"
            else -> "night"
        }

        profile = profile.copy(
            interactionPatterns = patterns.copy(
                preferredTimeOfDay = timeOfDay,
                frequencyOfVoiceMode = voiceFreq,
                averageResponseReadTime = ((patterns.averageResponseReadTime * (profile.interactionCount - 1)) + readDuration) / profile.interactionCount
            )
        )
    }

    private fun updateDerivedProfile() {
        // Auto-detect communication style from interaction patterns
        val style = when {
            profile.interactionPatterns.frequencyOfQuickActions > 0.4f -> CommunicationStyle.CASUAL
            profile.vocabularyLevel == VocabularyLevel.ADVANCED || profile.vocabularyLevel == VocabularyLevel.TECHNICAL -> CommunicationStyle.TECHNICAL
            profile.interactionPatterns.frequencyOfImageGeneration > 0.3f -> CommunicationStyle.CREATIVE
            profile.formalityPreference == FormalityLevel.FORMAL -> CommunicationStyle.FORMAL
            else -> CommunicationStyle.BALANCED
        }

        profile = profile.copy(communicationStyle = style)
        Log.d(TAG, "Profile updated: style=$style, vocab=${profile.vocabularyLevel}, length=${profile.preferredResponseLength}")
    }

    fun resetProfile() {
        profile = UserProfile(userId = profile.userId)
        topicHistory.clear()
        messageLengths.clear()
        sessionStartTimes.clear()
        vocabularyWords.clear()
    }
}
