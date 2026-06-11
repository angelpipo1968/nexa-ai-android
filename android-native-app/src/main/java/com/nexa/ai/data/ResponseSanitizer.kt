package com.nexa.ai.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResponseSanitizer — Cleans and formats AI responses before display.
 *
 * Handles:
 * - Removing unwanted markdown or formatting artifacts
 * - Trimming whitespace
 * - Ensuring proper encoding
 */
@Singleton
class ResponseSanitizer @Inject constructor() {

    /**
     * Sanitize an AI response for display.
     * @param raw The raw AI response text
     * @return The cleaned response text
     */
    fun sanitize(raw: String): String {
        return raw.trim()
    }

    /**
     * Sanitize an AI response for TTS (text-to-speech).
     * Removes markdown, URLs, and other non-speakable content.
     * @param raw The raw AI response text
     * @return Text suitable for TTS
     */
    fun sanitizeForTTS(raw: String): String {
        var cleaned = raw.trim()
        // Remove URLs
        cleaned = cleaned.replace(Regex("https?://\\S+"), "")
        // Remove markdown formatting
        cleaned = cleaned.replace(Regex("[*_~`]+"), "")
        // Collapse whitespace
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()
        return cleaned
    }
}
