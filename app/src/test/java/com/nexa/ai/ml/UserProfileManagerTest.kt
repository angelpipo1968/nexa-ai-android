package com.nexa.ai.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UserProfileManager — tests profiling, tracking, and context generation.
 */
class UserProfileManagerTest {

    private lateinit var manager: UserProfileManager

    @Before
    fun setUp() {
        manager = UserProfileManager()
    }

    // ─── Initialization Tests ───────────────────────────────

    @Test
    fun `Default profile values`() {
        val profile = manager.getProfile()
        assertEquals("default", profile.userId)
        assertEquals("en", profile.preferredLanguage)
        assertEquals(CommunicationStyle.BALANCED, profile.communicationStyle)
        assertEquals(VocabularyLevel.STANDARD, profile.vocabularyLevel)
        assertEquals(ResponseLength.MEDIUM, profile.preferredResponseLength)
        assertEquals(TechnicalLevel.MODERATE, profile.technicalLevel)
        assertEquals(FormalityLevel.MIXED, profile.formalityPreference)
        assertEquals(0, profile.interactionCount)
        assertTrue(profile.topicInterests.isEmpty())
        assertTrue(profile.favoriteTopics.isEmpty())
    }

    @Test
    fun `Initialize sets user ID`() {
        manager.initialize("user-123")
        assertEquals("user-123", manager.getProfile().userId)
    }

    // ─── Interaction Recording Tests ────────────────────────

    @Test
    fun `Record interaction increments count`() {
        manager.recordInteraction("Hello, how are you?")
        assertEquals(1, manager.getProfile().interactionCount)
        manager.recordInteraction("Tell me about AI")
        assertEquals(2, manager.getProfile().interactionCount)
    }

    @Test
    fun `Record interaction tracks topic`() {
        manager.recordInteraction("I want to learn about machine learning", topic = "machine learning")
        manager.recordInteraction("Tell me more about neural networks", topic = "neural networks")
        manager.recordInteraction("What about deep learning?", topic = "deep learning")

        val profile = manager.getProfile()
        assertTrue(profile.topicInterests.isNotEmpty())
        assertTrue(profile.favoriteTopics.isNotEmpty())
    }

    @Test
    fun `Record interaction with voice mode`() {
        manager.recordInteraction("Hello", isVoiceMode = true)
        assertEquals(1.0f, manager.getProfile().interactionPatterns.frequencyOfVoiceMode, 0.01f)

        manager.recordInteraction("Text input", isVoiceMode = false)
        assertEquals(0.5f, manager.getProfile().interactionPatterns.frequencyOfVoiceMode, 0.01f)
    }

    // ─── Preference Update Tests ─────────────────────────────

    @Test
    fun `Update communication style`() {
        manager.updatePreference(style = CommunicationStyle.CASUAL)
        assertEquals(CommunicationStyle.CASUAL, manager.getProfile().communicationStyle)
    }

    @Test
    fun `Update vocabulary level`() {
        manager.updatePreference(vocabLevel = VocabularyLevel.ADVANCED)
        assertEquals(VocabularyLevel.ADVANCED, manager.getProfile().vocabularyLevel)
    }

    @Test
    fun `Update multiple preferences at once`() {
        manager.updatePreference(
            style = CommunicationStyle.TECHNICAL,
            vocabLevel = VocabularyLevel.TECHNICAL,
            techLevel = TechnicalLevel.EXPERT,
            name = "Alice"
        )
        val profile = manager.getProfile()
        assertEquals(CommunicationStyle.TECHNICAL, profile.communicationStyle)
        assertEquals(VocabularyLevel.TECHNICAL, profile.vocabularyLevel)
        assertEquals(TechnicalLevel.EXPERT, profile.technicalLevel)
        assertEquals("Alice", profile.name)
    }

    // ─── Context Generation Tests ───────────────────────────

    @Test
    fun `Context returns empty for insufficient interactions`() {
        val ctx = manager.getContextForAI("en")
        assertEquals("", ctx)
    }

    @Test
    fun `Context returns data after sufficient interactions`() {
        // Record enough interactions to trigger profile generation
        for (i in 0..6) {
            manager.recordInteraction("Message number $i about programming and technology", topic = "programming")
        }

        val ctx = manager.getContextForAI("en")
        assertTrue(ctx.contains("User Profile Context"))
        assertTrue(ctx.contains("Communication style"))
        assertTrue(ctx.contains("Vocabulary level"))
    }

    @Test
    fun `Context in Spanish`() {
        for (i in 0..6) {
            manager.recordInteraction("Mensaje número $i sobre programación", topic = "programación")
        }

        val ctx = manager.getContextForAI("es")
        assertTrue(ctx.contains("Estilo de comunicación"))
        assertTrue(ctx.contains("Nivel de vocabulario"))
    }

    // ─── Session Tracking Tests ─────────────────────────────

    @Test
    fun `Start session tracks time`() {
        manager.startSession()
        // Interaction patterns should be updated with time of day
        assertTrue(manager.getProfile().interactionPatterns.preferredTimeOfDay.isNotEmpty())
    }

    // ─── Reset Tests ────────────────────────────────────────

    @Test
    fun `Reset profile clears all data`() {
        manager.initialize("test-user")
        manager.recordInteraction("Hello")
        manager.recordInteraction("World")
        manager.updatePreference(style = CommunicationStyle.FORMAL)

        manager.resetProfile()

        val profile = manager.getProfile()
        assertEquals("test-user", profile.userId) // User ID preserved
        assertEquals(0, profile.interactionCount)
        assertEquals(CommunicationStyle.BALANCED, profile.communicationStyle)
    }

    // ─── Enum Tests ─────────────────────────────────────────

    @Test
    fun `CommunicationStyle has all variants`() {
        assertEquals(5, CommunicationStyle.entries.size)
    }

    @Test
    fun `VocabularyLevel has all variants`() {
        assertEquals(5, VocabularyLevel.entries.size)
    }

    @Test
    fun `ResponseLength has all variants`() {
        assertEquals(4, ResponseLength.entries.size)
    }

    @Test
    fun `TechnicalLevel has all variants`() {
        assertEquals(5, TechnicalLevel.entries.size)
    }

    @Test
    fun `FormalityLevel has all variants`() {
        assertEquals(3, FormalityLevel.entries.size)
    }

    @Test
    fun `ResponseLength ranges are logical`() {
        assertTrue(ResponseLength.BRIEF.maxWords < ResponseLength.MEDIUM.maxWords)
        assertTrue(ResponseLength.MEDIUM.maxWords < ResponseLength.DETAILED.maxWords)
        assertTrue(ResponseLength.DETAILED.maxWords < ResponseLength.COMPREHENSIVE.maxWords)
    }

    @Test
    fun `All enums have descriptions`() {
        CommunicationStyle.entries.forEach { assertTrue(it.description.isNotEmpty()) }
        VocabularyLevel.entries.forEach { assertTrue(it.description.isNotEmpty()) }
        ResponseLength.entries.forEach { assertTrue(it.description.isNotEmpty()) }
        TechnicalLevel.entries.forEach { assertTrue(it.description.isNotEmpty()) }
        FormalityLevel.entries.forEach { assertTrue(it.description.isNotEmpty()) }
    }

    // ─── UserProfile Data Class Tests ───────────────────────

    @Test
    fun `UserProfile copy preserves values`() {
        val original = manager.getProfile()
        val updated = original.copy(name = "TestUser")
        assertEquals("TestUser", updated.name)
        assertEquals(original.userId, updated.userId)
        assertEquals(original.interactionCount, updated.interactionCount)
    }
}
