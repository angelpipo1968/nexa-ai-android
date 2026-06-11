package com.nexa.ai.translator

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * LanguageDetector — Uses ML Kit for fast on-device language identification.
 */
object LanguageDetector {
    
    private const val TAG = "NexaLangDetect"
    
    // Map of language codes to display names
    val SUPPORTED_LANGUAGES = mapOf(
        "es" to "Español", "en" to "English", "fr" to "Français",
        "de" to "Deutsch", "it" to "Italiano", "pt" to "Português",
        "ru" to "Русский", "ja" to "日本語", "ko" to "한국어",
        "zh" to "中文", "ar" to "العربية", "hi" to "हिन्दी",
        "tr" to "Türkçe", "nl" to "Nederlands", "pl" to "Polski",
        "sv" to "Svenska", "da" to "Dansk", "no" to "Norsk",
        "fi" to "Suomi", "el" to "Ελληνικά", "cs" to "Čeština",
        "ro" to "Română", "hu" to "Magyar", "uk" to "Українська"
    )
    
    /**
     * Detect the language of the given text.
     * @return ISO 639-1 language code (e.g., "es", "en") or null if detection fails
     */
    suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        
        return try {
            val identifier = LanguageIdentification.getClient()
            suspendCancellableCoroutine { continuation ->
                identifier.identifyLanguage(text)
                    .addOnSuccessListener { langCode ->
                        if (langCode != "und" && langCode in SUPPORTED_LANGUAGES) {
                            Log.d(TAG, "Detected language: $langCode (${SUPPORTED_LANGUAGES[langCode]})")
                            continuation.resume(langCode)
                        } else {
                            Log.w(TAG, "Unknown language code: $langCode")
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Language detection failed: ${e.message}")
                        continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Language detection error: ${e.message}")
            null
        }
    }
    
    /**
     * Get display name for a language code.
     */
    fun getDisplayName(langCode: String): String {
        return SUPPORTED_LANGUAGES[langCode] ?: langCode.uppercase()
    }
}
