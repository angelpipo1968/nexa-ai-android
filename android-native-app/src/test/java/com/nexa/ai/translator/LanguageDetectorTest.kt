package com.nexa.ai.translator

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LanguageDetector.
 * Tests supported languages mapping.
 */
class LanguageDetectorTest {

    @Test
    fun `common languages are supported`() {
        val required = listOf("es", "en", "fr", "de", "it", "pt", "ja", "ko", "zh", "ar")
        for (lang in required) {
            assertTrue("Language code '$lang' should be supported", lang in LanguageDetector.SUPPORTED_LANGUAGES)
        }
    }

    @Test
    fun `display names are correct`() {
        assertEquals("Español", LanguageDetector.getDisplayName("es"))
        assertEquals("English", LanguageDetector.getDisplayName("en"))
        assertEquals("Français", LanguageDetector.getDisplayName("fr"))
        assertEquals("Deutsch", LanguageDetector.getDisplayName("de"))
    }

    @Test
    fun `unknown code returns uppercase code`() {
        assertEquals("XX", LanguageDetector.getDisplayName("xx"))
    }

    @Test
    fun `at least 20 languages supported`() {
        assertTrue("Should support at least 20 languages", LanguageDetector.SUPPORTED_LANGUAGES.size >= 20)
    }
}
