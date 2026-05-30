package com.nexa.ai.automotive.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTSManager — Thread-safe, offline-ready manager for Text-to-Speech confirmations
 * in Nexa AI Automotive (v5.3-auto-certified).
 */
object TTSManager {
    private const val TAG = "NexaTTS"
    private var tts: TextToSpeech? = null
    private var isReady = false

    /**
     * Initializes the TTS engine with the given application context.
     */
    fun init(context: Context) {
        if (tts != null && isReady) return

        Log.i(TAG, "Initializing TTS Engine...")
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("es", "ES")
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Spanish locale not supported, falling back to default locale")
                    tts?.language = Locale.getDefault()
                }
                isReady = true
                Log.i(TAG, "TTS Engine initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TTS Engine")
                isReady = false
            }
        }
    }

    /**
     * Speaks the given text using Text-to-Speech queue flush strategy.
     */
    fun speak(text: String) {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready or uninitialized. Request dropped: $text")
            return
        }

        try {
            Log.d(TAG, "Speaking: $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexa_tts_utterance")
        } catch (e: Exception) {
            Log.e(TAG, "Error during speak: ${e.message}", e)
        }
    }

    /**
     * Releases the TTS native resources.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down TTS Engine...")
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isReady = false
    }
}
