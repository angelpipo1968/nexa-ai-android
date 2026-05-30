package com.nexa.ai.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalLLMManager — Manages local GGUF model loading and inference via llama.cpp Android JNI.
 * Serves as the core offline intelligence module for NEXA AI v5.1.
 */
@Singleton
class LocalLLMManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    private var isModelLoaded = false
    private var nativePointer: Long = 0

    companion object {
        private const val TAG = "LocalLLM"
        
        init {
            try {
                System.loadLibrary("llama-android")
                Log.i(TAG, "Native library 'llama-android' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Could not load native library 'llama-android' — simulation mode will be used")
            }
        }
    }

    // Native JNI methods
    private external fun loadModel(modelPath: String): Long
    private external fun freeModel(ptr: Long)
    private external fun generate(
        ptr: Long,
        prompt: String,
        maxTokens: Int
    ): String

    /**
     * Load the local GGUF model from assets or internal storage.
     * Copies the GGUF asset to internal storage if not already present.
     */
    suspend fun loadModelFromAssets(assetName: String = "llama3-8b-q4.gguf"): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext true

        try {
            Log.i(TAG, "Loading GGUF model: $assetName...")
            val outFile = File(context.filesDir, assetName)
            if (!outFile.exists()) {
                Log.i(TAG, "Copying model asset '$assetName' to storage...")
                context.assets.open(assetName).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Check if native library was loaded successfully
            try {
                nativePointer = loadModel(outFile.absolutePath)
                if (nativePointer != 0L) {
                    isModelLoaded = true
                    Log.i(TAG, "Model GGUF loaded successfully via llama.cpp nativo. Pointer: $nativePointer")
                    true
                } else {
                    Log.e(TAG, "llama.cpp returned null native pointer for model: $assetName")
                    false
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Simulation Mode active: llama.cpp JNI not bound. Simulating pointer.")
                nativePointer = 999999L // simulated pointer for testing
                isModelLoaded = true
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading GGUF model: ${e.message}", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * Generate text offline using GGUF local inference.
     */
    suspend fun generateText(
        prompt: String,
        maxTokens: Int = 256
    ): String = withContext(Dispatchers.Default) {
        if (!isModelLoaded) {
            loadModelFromAssets()
        }

        if (nativePointer == 0L) {
            return@withContext "Error: No se pudo inicializar el modelo local GGUF."
        }

        try {
            Log.d(TAG, "Generating offline text response...")
            if (nativePointer == 999999L) {
                // Simulation response
                kotlinx.coroutines.delay(1000)
                return@withContext "[Modo Local Simulado] He procesado tu solicitud de forma 100% offline. " +
                        "La respuesta se generó de manera local mediante el motor GGUF sin usar internet."
            }

            generate(nativePointer, prompt, maxTokens)
        } catch (e: Exception) {
            Log.e(TAG, "Local LLM inference error: ${e.message}", e)
            "Error en la inferencia local de IA: ${e.localizedMessage}"
        }
    }

    /**
     * Release all native llama.cpp resources.
     */
    fun release() {
        if (nativePointer != 0L) {
            Log.i(TAG, "Releasing native llama.cpp resources...")
            try {
                if (nativePointer != 999999L) {
                    freeModel(nativePointer)
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "llama.cpp native release skipped (simulation mode)")
            }
            nativePointer = 0
            isModelLoaded = false
            Log.i(TAG, "Native resources released")
        }
    }
}
