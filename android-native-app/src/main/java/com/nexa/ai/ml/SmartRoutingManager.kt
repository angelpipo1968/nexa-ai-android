package com.nexa.ai.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ═══════════════════════════════════════════════════════════
 *  NEXA AI — Smart Routing Manager
 *  Automatically decides between online (cloud) and on-device AI
 *  based on connectivity, task complexity, and device capabilities.
 *
 *  Hilt Singleton — inject wherever routing decisions are needed.
 * ═══════════════════════════════════════════════════════════
 */
@Singleton
class SmartRoutingManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "NexaSmartRoute"

        // Complexity thresholds for routing decisions
        private const val SIMPLE_QUERY_MAX_CHARS = 100
    }

    private val appContext = context.applicationContext
    private val onDeviceManager = OnDeviceInferenceManager(appContext)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ─── Routing State ───────────────────────────

    enum class InferenceMode {
        /** Use cloud API (Groq/OpenAI) — for complex queries */
        ONLINE,
        /** Use on-device model — for simple/quick queries and offline */
        ON_DEVICE,
        /** Hybrid — try on-device first, fallback to online */
        HYBRID,
    }

    private val _currentMode = MutableStateFlow(InferenceMode.HYBRID)
    val currentMode: StateFlow<InferenceMode> = _currentMode.asStateFlow()

    private val _isOnline = MutableStateFlow(checkNetwork())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _onDeviceReady = MutableStateFlow(false)
    val onDeviceReady: StateFlow<Boolean> = _onDeviceReady.asStateFlow()

    // ─── Network Detection ───────────────────────

    fun checkNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Update network status. Call from NetworkMonitor.
     */
    fun updateNetworkStatus(connected: Boolean) {
        _isOnline.value = connected

        // Auto-switch mode based on connectivity
        if (!connected && onDeviceManager.isReady) {
            _currentMode.value = InferenceMode.ON_DEVICE
            Log.i(TAG, "Network lost — switching to ON_DEVICE mode")
        } else if (connected) {
            _currentMode.value = InferenceMode.HYBRID
            Log.i(TAG, "Network restored — switching to HYBRID mode")
        }
    }

    // ─── Routing Decision ────────────────────────

    /**
     * Determine if a query should be handled on-device or online.
     *
     * Decision factors:
     * 1. Network availability — if offline, must use on-device
     * 2. Task type — simple questions can be answered locally
     * 3. Device capability — NPU available = faster on-device
     * 4. Model loaded — on-device model must be loaded
     * 5. Query complexity — long/complex queries go to cloud
     *
     * @param query The user's input text
     * @param hasImage Whether the query includes an image
     * @return RoutingDecision with useOnDevice flag, reason, and confidence
     */
    fun shouldUseOnDevice(query: String, hasImage: Boolean = false): RoutingDecision {
        val online = _isOnline.value
        val onDeviceReady = onDeviceManager.isReady

        // Rule 1: No network → on-device if available
        if (!online) {
            return if (onDeviceReady) {
                RoutingDecision(
                    useOnDevice = true,
                    reason = "Sin conexión — modo offline",
                    confidence = 1.0f,
                )
            } else {
                RoutingDecision(
                    useOnDevice = false,
                    reason = "Sin conexión y sin modelo local",
                    confidence = 0.0f,
                    fallbackMessage = "No hay conexión a internet y el modelo local no está disponible.",
                )
            }
        }

        // Rule 2: Image analysis → online unless vision model is loaded on-device
        if (hasImage) {
            return if (onDeviceManager.currentModel == OnDeviceInferenceManager.MODEL_VISION) {
                RoutingDecision(
                    useOnDevice = true,
                    reason = "Modelo de visión local disponible",
                    confidence = 0.6f,
                )
            } else {
                RoutingDecision(
                    useOnDevice = false,
                    reason = "Análisis de imagen → cloud VLM",
                    confidence = 0.9f,
                )
            }
        }

        // Rule 3: Simple queries → on-device if ready
        if (onDeviceReady && query.length < SIMPLE_QUERY_MAX_CHARS && isSimpleQuery(query)) {
            return RoutingDecision(
                useOnDevice = true,
                reason = "Consulta simple — on-device más rápido",
                confidence = 0.8f,
            )
        }

        // Rule 4: Complex queries → always online
        if (query.length > 500 || hasToolKeywords(query)) {
            return RoutingDecision(
                useOnDevice = false,
                reason = "Consulta compleja — requiere cloud",
                confidence = 0.9f,
            )
        }

        // Default: hybrid preference based on NPU availability
        return if (onDeviceReady && onDeviceManager.isNPUAvailable()) {
            RoutingDecision(
                useOnDevice = true,
                reason = "NPU disponible — on-device preferido",
                confidence = 0.6f,
            )
        } else {
            RoutingDecision(
                useOnDevice = false,
                reason = "Cloud preferido (mayor capacidad)",
                confidence = 0.7f,
            )
        }
    }

    // ─── Query Analysis ──────────────────────────

    private fun isSimpleQuery(query: String): Boolean {
        val simplePatterns = listOf(
            "hola", "buenos días", "buenas tardes", "buenas noches",
            "gracias", "adiós", "qué hora es", "qué día es",
            "hello", "hi", "thanks", "bye",
            "quién eres", "qué eres", "qué puedes hacer",
            "cómo te llamas", "cuál es tu nombre",
        )
        val lower = query.lowercase().trim()
        return simplePatterns.any { lower.startsWith(it) || lower == it }
    }

    private fun hasToolKeywords(query: String): Boolean {
        val toolKeywords = listOf(
            "busca", "busca en", "search", "google",
            "clima", "weather", "temperatura",
            "vuelo", "flight", "reserva",
            "noticias", "news",
            "genera imagen", "genera video", "dibuja",
            "traduce", "translate",
            "calcula", "cuánto es",
        )
        val lower = query.lowercase()
        return toolKeywords.any { lower.contains(it) }
    }

    // ─── Lifecycle ───────────────────────────────

    suspend fun initialize(): Boolean {
        val result = onDeviceManager.initialize()
        _onDeviceReady.value = result

        // Try to load the chat model
        if (result) {
            val modelLoaded = onDeviceManager.loadModel(OnDeviceInferenceManager.MODEL_CHAT)
            _onDeviceReady.value = modelLoaded
        }

        return result
    }

    fun getDeviceCapabilities() = onDeviceManager.getDeviceCapabilities()

    fun getOnDeviceManager(): OnDeviceInferenceManager = onDeviceManager

    /**
     * Generate text on-device. Returns null if on-device is not available.
     */
    suspend fun generateOnDevice(prompt: String, systemPrompt: String? = null, maxTokens: Int = 512): String? {
        return onDeviceManager.generateText(prompt, systemPrompt, maxTokens)
    }

    /**
     * Detect language of text on-device using ML Kit.
     */
    suspend fun detectLanguage(text: String): String? {
        return onDeviceManager.detectLanguage(text)
    }

    fun shutdown() {
        onDeviceManager.shutdown()
    }

    // ─── Data Classes ────────────────────────────

    data class RoutingDecision(
        val useOnDevice: Boolean,
        val reason: String,
        val confidence: Float,
        val fallbackMessage: String? = null,
    )
}
