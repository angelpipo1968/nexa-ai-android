package com.nexa.ai.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EnhancedEmotionAnalyzer — tests emotion detection, VAD, and bilingual support.
 */
class EnhancedEmotionAnalyzerTest {

    private val analyzer = EnhancedEmotionAnalyzer()

    // ─── Emotion Detection Tests ─────────────────────────────

    @Test
    fun `Detect joy in English text`() {
        val profile = analyzer.analyzeEmotion("I am so happy and excited today!")
        assertEquals(Emotion.JOY, profile.primaryEmotion)
        assertTrue(profile.confidence > 0f)
        assertEquals("\uD83D\uDE0A", profile.emoji) // 😊
    }

    @Test
    fun `Detect joy in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Estoy muy feliz y contento hoy!")
        assertEquals(Emotion.JOY, profile.primaryEmotion)
        assertTrue(profile.confidence > 0f)
    }

    @Test
    fun `Detect sadness in English text`() {
        val profile = analyzer.analyzeEmotion("I feel sad and depressed about the situation")
        assertEquals(Emotion.SADNESS, profile.primaryEmotion)
        assertTrue(profile.valence < 0f) // Negative valence
    }

    @Test
    fun `Detect sadness in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Me siento triste y deprimido por la situación")
        assertEquals(Emotion.SADNESS, profile.primaryEmotion)
    }

    @Test
    fun `Detect anger in English text`() {
        val profile = analyzer.analyzeEmotion("I am furious and angry about this injustice!")
        assertEquals(Emotion.ANGER, profile.primaryEmotion)
        assertTrue(profile.valence < 0f)
    }

    @Test
    fun `Detect anger in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Estoy furioso y enojado por esta injusticia!")
        assertEquals(Emotion.ANGER, profile.primaryEmotion)
    }

    @Test
    fun `Detect fear in English text`() {
        val profile = analyzer.analyzeEmotion("I am scared and terrified of what might happen")
        assertEquals(Emotion.FEAR, profile.primaryEmotion)
    }

    @Test
    fun `Detect fear in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Estoy asustado y aterrorizado de lo que pueda pasar")
        assertEquals(Emotion.FEAR, profile.primaryEmotion)
    }

    @Test
    fun `Detect confusion in English text`() {
        val profile = analyzer.analyzeEmotion("I'm confused and don't understand how this works")
        assertEquals(Emotion.CONFUSION, profile.primaryEmotion)
    }

    @Test
    fun `Detect confusion in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Estoy confundido y no entiendo cómo funciona esto")
        assertEquals(Emotion.CONFUSION, profile.primaryEmotion)
    }

    @Test
    fun `Detect frustration in English text`() {
        val profile = analyzer.analyzeEmotion("This is so frustrating, it doesn't work at all!")
        assertEquals(Emotion.FRUSTRATION, profile.primaryEmotion)
    }

    @Test
    fun `Detect frustration in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Esto es muy frustrante, no funciona para nada!")
        assertEquals(Emotion.FRUSTRATION, profile.primaryEmotion)
    }

    @Test
    fun `Detect curiosity in English text`() {
        val profile = analyzer.analyzeEmotion("I'm curious, how does quantum mechanics actually work?")
        assertEquals(Emotion.CURIOSITY, profile.primaryEmotion)
    }

    @Test
    fun `Detect curiosity in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Tengo curiosidad, cómo funciona realmente la mecánica cuántica?")
        assertEquals(Emotion.CURIOSITY, profile.primaryEmotion)
    }

    @Test
    fun `Detect gratitude in English text`() {
        val profile = analyzer.analyzeEmotion("Thank you so much, I'm very grateful for your help!")
        assertEquals(Emotion.GRATITUDE, profile.primaryEmotion)
    }

    @Test
    fun `Detect gratitude in Spanish text`() {
        val profile = analyzer.analyzeEmotion("Muchas gracias, estoy muy agradecido por tu ayuda!")
        assertEquals(Emotion.GRATITUDE, profile.primaryEmotion)
    }

    @Test
    fun `Neutral text returns neutral emotion`() {
        val profile = analyzer.analyzeEmotion("The meeting is at 3pm tomorrow")
        assertEquals(Emotion.NEUTRAL, profile.primaryEmotion)
        assertTrue(profile.confidence < 0.2f) // Low confidence for neutral
    }

    @Test
    fun `Empty text returns neutral`() {
        val profile = analyzer.analyzeEmotion("")
        assertEquals(Emotion.NEUTRAL, profile.primaryEmotion)
    }

    // ─── Negation Tests ─────────────────────────────────────

    @Test
    fun `Negated emotion detected correctly`() {
        val profile = analyzer.analyzeEmotion("I am not happy about this at all")
        // With negation, joy score should be reduced or inverted
        assertTrue(profile.confidence < 0.3f || profile.primaryEmotion != Emotion.JOY)
    }

    // ─── Intensification Tests ───────────────────────────────

    @Test
    fun `Intensified emotion has higher confidence`() {
        val normal = analyzer.analyzeEmotion("I am happy")
        val intensified = analyzer.analyzeEmotion("I am very happy and extremely excited!")
        assertTrue(intensified.confidence >= normal.confidence)
    }

    // ─── VAD (Valence-Arousal-Dominance) Tests ───────────────

    @Test
    fun `Positive emotion has positive valence`() {
        val profile = analyzer.analyzeEmotion("I love this, it's amazing!")
        assertTrue(profile.valence > 0f)
    }

    @Test
    fun `Negative emotion has negative valence`() {
        val profile = analyzer.analyzeEmotion("I hate this, it's terrible")
        assertTrue(profile.valence < 0f)
    }

    @Test
    fun `Excited text increases arousal`() {
        val calm = analyzer.analyzeEmotion("I'm fine")
        val excited = analyzer.analyzeEmotion("I'm SO EXCITED!!! WOW!!!")
        assertTrue(excited.arousal >= calm.arousal)
    }

    @Test
    fun `VAD values are within range`() {
        val profile = analyzer.analyzeEmotion("Happy excited!")
        assertTrue(profile.valence >= -1f && profile.valence <= 1f)
        assertTrue(profile.arousal >= 0f && profile.arousal <= 1f)
        assertTrue(profile.dominance >= 0f && profile.dominance <= 1f)
    }

    // ─── Context Generation Tests ───────────────────────────

    @Test
    fun `Get emotion context returns string for high confidence`() {
        val profile = analyzer.analyzeEmotion("I'm very sad and disappointed")
        val context = analyzer.getEmotionContext(profile, "en")
        assertTrue(context.contains("Sadness"))
        assertTrue(context.contains("gentle and empathetic"))
    }

    @Test
    fun `Get emotion context returns empty for low confidence`() {
        val profile = analyzer.analyzeEmotion("The weather is cloudy")
        val context = analyzer.getEmotionContext(profile, "en")
        assertTrue(context.isBlank())
    }

    @Test
    fun `Get emotion context in Spanish`() {
        val profile = analyzer.analyzeEmotion("Estoy muy triste y decepcionado")
        val context = analyzer.getEmotionContext(profile, "es")
        assertTrue(context.contains("Sadness"))
    }

    // ─── Visual Feedback Tests ──────────────────────────────

    @Test
    fun `Visual feedback contains emoji and percentage`() {
        val profile = analyzer.analyzeEmotion("I am happy!")
        val feedback = analyzer.getVisualFeedback(profile)
        assertTrue(feedback.contains("\uD83D\uDE0A")) // Joy emoji
        assertTrue(feedback.contains("Joy"))
        assertTrue(feedback.contains("%"))
        assertTrue(feedback.contains("▓"))
    }

    // ─── Emotion Enum Tests ──────────────────────────────────

    @Test
    fun `Emotion enum has all 20 types`() {
        assertEquals(20, Emotion.entries.size)
    }

    @Test
    fun `All emotions have display names`() {
        Emotion.entries.forEach { emotion ->
            assertTrue(emotion.displayName.isNotEmpty())
        }
    }

    @Test
    fun `All emotions have emojis`() {
        Emotion.entries.forEach { emotion ->
            assertTrue(emotion.emoji.isNotEmpty())
        }
    }

    @Test
    fun `All emotions have colors`() {
        Emotion.entries.forEach { emotion ->
            assertTrue(emotion.color.startsWith("#"))
        }
    }

    @Test
    fun `All emotions have tone descriptions`() {
        Emotion.entries.forEach { emotion ->
            assertTrue(emotion.tone.isNotBlank())
        }
    }

    // ─── EmotionProfile Data Class Tests ─────────────────────

    @Test
    fun `EmotionProfile has correct defaults`() {
        val profile = analyzer.analyzeEmotion("neutral text here")
        assertNotNull(profile.primaryEmotion)
        assertTrue(profile.confidence >= 0f && profile.confidence <= 1f)
        assertNotNull(profile.suggestedTone)
        assertNotNull(profile.triggers)
    }
}
