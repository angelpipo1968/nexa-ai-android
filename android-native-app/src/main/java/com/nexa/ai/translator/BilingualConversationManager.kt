package com.nexa.ai.translator

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * BilingualConversationManager — Enables real-time bilingual conversation.
 * Person A speaks Spanish, Person B speaks English, and Nexa translates in both directions.
 */
class BilingualConversationManager(context: Context) {

    companion object {
        private const val TAG = "NexaBilingual"
    }

    private var languageA: String = "es" // First person's language
    private var languageB: String = "en" // Second person's language
    private var translatorAToB: Translator? = null
    private var translatorBToA: Translator? = null
    private var isReady = false

    /**
     * Configure the bilingual conversation.
     * @param langA First person's language (ISO 639-1)
     * @param langB Second person's language (ISO 639-1)
     */
    suspend fun configure(langA: String, langB: String): Boolean {
        languageA = langA
        languageB = langB
        
        return try {
            // Create translator A → B
            val langACode = TranslateLanguage.fromLanguageTag(langA)
            val langBCode = TranslateLanguage.fromLanguageTag(langB)
            
            if (langACode == null || langBCode == null) {
                Log.e(TAG, "Unsupported language pair: $langA → $langB")
                return false
            }
            
            val optionsAToB = TranslatorOptions.Builder()
                .setSourceLanguage(langACode)
                .setTargetLanguage(langBCode)
                .build()
            translatorAToB = Translation.getClient(optionsAToB)
            
            val optionsBToA = TranslatorOptions.Builder()
                .setSourceLanguage(langBCode)
                .setTargetLanguage(langACode)
                .build()
            translatorBToA = Translation.getClient(optionsBToA)
            
            // Download models
            translatorAToB?.downloadModelIfNeeded()?.await()
            translatorBToA?.downloadModelIfNeeded()?.await()
            
            isReady = true
            Log.i(TAG, "Bilingual conversation ready: $langA ↔ $langB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure bilingual: ${e.message}")
            isReady = false
            false
        }
    }

    /**
     * Translate text, auto-detecting source language and translating to the other.
     * @param text Text to translate
     * @return Translated text, or null if translation failed
     */
    suspend fun translate(text: String): String? {
        if (!isReady) return null
        
        // Detect source language
        val detectedLang = LanguageDetector.detect(text)
        
        return try {
            val translator = if (detectedLang == languageA) {
                Log.d(TAG, "Detected $languageA → translating to $languageB")
                translatorAToB
            } else {
                Log.d(TAG, "Detected $languageB → translating to $languageA")
                translatorBToA
            }
            
            translator?.translate(text)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}")
            null
        }
    }

    /**
     * Translate from a specific source language.
     */
    suspend fun translateFrom(text: String, sourceLang: String): String? {
        if (!isReady) return null
        
        return try {
            val translator = if (sourceLang == languageA) translatorAToB else translatorBToA
            translator?.translate(text)?.await()
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}")
            null
        }
    }

    fun getLanguageA() = languageA
    fun getLanguageB() = languageB
    fun isReady() = isReady

    fun shutdown() {
        translatorAToB?.close()
        translatorBToA?.close()
        isReady = false
    }
}
