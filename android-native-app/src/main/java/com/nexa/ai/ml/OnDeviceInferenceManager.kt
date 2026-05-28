package com.nexa.ai.ml

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ═══════════════════════════════════════════════════════════
 *  NEXA AI — On-Device Inference Manager
 *  Qualcomm Nexa SDK wrapper for on-device AI inference
 *  Uses Snapdragon NPU for hardware-accelerated inference
 *  Falls back gracefully via reflection if SDK is unavailable
 * ═══════════════════════════════════════════════════════════
 */
class OnDeviceInferenceManager(private val context: Context) {

    companion object {
        private const val TAG = "NexaOnDevice"

        // Model configuration
        const val MODEL_CHAT = "ministral-3b-instruct-q4"  // Fast chat model
        const val MODEL_VISION = "qwen2-vl-instruct-q4"    // Vision model (when available)

        // State
        @Volatile
        private var isInitialized = false
    }

    // ─── State ────────────────────────────────────
    var isReady: Boolean = false
        private set

    var currentModel: String? = null
        private set

    // Nexa SDK engine (loaded via reflection for safety)
    private var nexaEngine: Any? = null
    private var nexaGenerateMethod: java.lang.reflect.Method? = null
    private var nexaAnalyzeMethod: java.lang.reflect.Method? = null
    private var nexaLoadModelMethod: java.lang.reflect.Method? = null
    private var nexaShutdownMethod: java.lang.reflect.Method? = null

    // ML Kit
    private var languageIdentifier: LanguageIdentifier? = null

    // ─── Initialization ──────────────────────────

    /**
     * Initialize the Nexa SDK engine and ML Kit.
     * Uses reflection to load the Nexa SDK so the app compiles
     * even if the SDK AAR is not present on the build machine.
     * Call this on app startup (background thread).
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Nexa SDK on-device inference...")

            // Try to load Nexa SDK via reflection
            try {
                val engineClass = Class.forName("ai.nexa.core.NexaEngine")
                val builderClass = Class.forName("ai.nexa.core.NexaEngine\$Builder")
                val computeUnitClass = Class.forName("ai.nexa.core.NexaEngine\$ComputeUnit")

                val npuPreferred = computeUnitClass.getField("NPU_PREFERRED").get(null)
                val builder = builderClass.getConstructor(Context::class.java).newInstance(context)

                // Set compute unit
                builderClass.getMethod("setComputeUnit", computeUnitClass).invoke(builder, npuPreferred)

                // Set model cache dir
                val cacheDir = java.io.File(context.filesDir, "nexa_models")
                cacheDir.mkdirs()
                builderClass.getMethod("setModelCacheDir", java.io.File::class.java).invoke(builder, cacheDir)

                // Build engine
                nexaEngine = builderClass.getMethod("build").invoke(builder)

                // Cache method references for faster calls
                nexaGenerateMethod = engineClass.getMethod("generate", String::class.java, String::class.java, Int::class.java)
                nexaAnalyzeMethod = engineClass.getMethod("analyzeImage", String::class.java, String::class.java)
                nexaLoadModelMethod = engineClass.getMethod("loadModel", String::class.java)
                nexaShutdownMethod = engineClass.getMethod("shutdown")

                Log.i(TAG, "Nexa SDK engine loaded successfully via reflection")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "Nexa SDK not available on this device — falling back to cloud-only mode")
            } catch (e: Exception) {
                Log.w(TAG, "Nexa SDK initialization failed: ${e.message} — cloud-only mode")
            }

            // Initialize ML Kit Language Identification
            try {
                languageIdentifier = LanguageIdentification.getClient()
                Log.i(TAG, "ML Kit Language ID initialized")
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit Language ID init failed: ${e.message}")
            }

            isInitialized = true
            isReady = nexaEngine != null || languageIdentifier != null
            Log.i(TAG, "On-device inference initialized (ready=$isReady, hasNexaEngine=${nexaEngine != null}, hasMLKit=${languageIdentifier != null})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: ${e.message}")
            isReady = false
            false
        }
    }

    // ─── Model Loading ───────────────────────────

    /**
     * Load a specific model for inference.
     * @param modelId The model identifier (e.g., MODEL_CHAT, MODEL_VISION)
     * @return true if model loaded successfully
     */
    suspend fun loadModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Cannot load model: SDK not initialized")
            return@withContext false
        }

        try {
            Log.i(TAG, "Loading model: $modelId")

            if (nexaEngine != null && nexaLoadModelMethod != null) {
                val result = nexaLoadModelMethod!!.invoke(nexaEngine, modelId)
                val loaded = result as? Boolean ?: true
                if (loaded) {
                    currentModel = modelId
                    Log.i(TAG, "Model loaded via Nexa SDK: $modelId")
                    return@withContext true
                } else {
                    Log.w(TAG, "Nexa SDK returned false for model load: $modelId")
                }
            }

            // Fallback: mark as current model for routing purposes
            // even if Nexa SDK couldn't load it
            currentModel = modelId
            Log.i(TAG, "Model marked as current (no SDK): $modelId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model $modelId: ${e.message}")
            currentModel = null
            false
        }
    }

    // ─── Text Generation ─────────────────────────

    /**
     * Generate text response on-device.
     * Uses the loaded chat model for inference.
     *
     * @param prompt User's input text
     * @param systemPrompt Optional system prompt for context
     * @param maxTokens Maximum tokens to generate (default: 512)
     * @return Generated response text, or null if failed
     */
    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512
    ): String? = withContext(Dispatchers.IO) {
        if (!isReady || currentModel == null) {
            Log.w(TAG, "Cannot generate: not ready or no model loaded")
            return@withContext null
        }

        try {
            Log.d(TAG, "Generating text on-device (model: $currentModel)")
            val startTime = System.currentTimeMillis()

            if (nexaEngine != null && nexaGenerateMethod != null) {
                val result = nexaGenerateMethod!!.invoke(
                    nexaEngine,
                    prompt,
                    systemPrompt ?: "",
                    maxTokens
                ) as? String

                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "On-device inference completed in ${elapsed}ms")
                return@withContext result
            }

            // No Nexa engine available
            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "No Nexa engine for on-device inference (${elapsed}ms)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Text generation failed: ${e.message}")
            null
        }
    }

    // ─── Image Analysis ──────────────────────────

    /**
     * Analyze an image on-device using the vision model.
     * Falls back to a basic description if vision model not available.
     *
     * @param imageBase64 Base64-encoded image data
     * @param question Optional question about the image
     * @return Analysis text, or null if failed
     */
    suspend fun analyzeImage(
        imageBase64: String,
        question: String = "Describe lo que ves en esta imagen."
    ): String? = withContext(Dispatchers.IO) {
        if (!isReady) {
            Log.w(TAG, "Cannot analyze image: engine not ready")
            return@withContext null
        }

        try {
            Log.d(TAG, "Analyzing image on-device")
            val startTime = System.currentTimeMillis()

            if (nexaEngine != null && nexaAnalyzeMethod != null) {
                val result = nexaAnalyzeMethod!!.invoke(nexaEngine, imageBase64, question) as? String
                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "On-device image analysis completed in ${elapsed}ms")
                return@withContext result
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "No vision model available for on-device analysis (${elapsed}ms)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed: ${e.message}")
            null
        }
    }

    // ─── Language Detection ──────────────────────

    /**
     * Detect the language of the input text on-device.
     * Uses ML Kit for fast language identification.
     *
     * @param text Input text to identify
     * @return ISO 639-1 language code (e.g., "es", "en"), or null
     */
    suspend fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null

        return try {
            val identifier = languageIdentifier ?: return null
            suspendCancellableCoroutine { continuation ->
                identifier.identifyLanguage(text)
                    .addOnSuccessListener { langCode ->
                        if (langCode != "und") {
                            continuation.resume(langCode)
                        } else {
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

    // ─── Smart Capabilities Check ────────────────

    /**
     * Check if the device supports NPU acceleration.
     * Checks both hardware (Qualcomm SoC) and whether the
     * Nexa SDK engine was actually instantiated.
     */
    fun isNPUAvailable(): Boolean {
        // Check hardware
        val hasQualcommHardware = android.os.Build.HARDWARE.contains("qcom") ||
               android.os.Build.SOC_MANUFACTURER.equals("Qualcomm", ignoreCase = true)

        // Also check if Nexa SDK engine was actually loaded
        return hasQualcommHardware && nexaEngine != null
    }

    /**
     * Get device AI capabilities summary.
     */
    fun getDeviceCapabilities(): DeviceCapabilities {
        return DeviceCapabilities(
            hasNPU = isNPUAvailable(),
            socModel = android.os.Build.SOC_MODEL,
            totalMemoryGB = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0 * 1024.0),
            sdkReady = isReady,
            loadedModel = currentModel,
        )
    }

    /**
     * Release all resources.
     */
    fun shutdown() {
        try {
            nexaShutdownMethod?.invoke(nexaEngine)
            languageIdentifier?.close()
            nexaEngine = null
            nexaGenerateMethod = null
            nexaAnalyzeMethod = null
            nexaLoadModelMethod = null
            nexaShutdownMethod = null
            languageIdentifier = null
            currentModel = null
            isReady = false
            isInitialized = false
            Log.i(TAG, "Nexa SDK shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error: ${e.message}")
        }
    }

    // ─── Data Classes ────────────────────────────

    data class DeviceCapabilities(
        val hasNPU: Boolean,
        val socModel: String,
        val totalMemoryGB: Double,
        val sdkReady: Boolean,
        val loadedModel: String?,
    )
}
