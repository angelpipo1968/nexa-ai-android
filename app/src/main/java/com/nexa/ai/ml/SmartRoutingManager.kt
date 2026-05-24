package com.nexa.ai.ml

import android.content.Context
import android.util.Log
import com.nexa.ai.data.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ═══════════════════════════════════════════════════════════════════
 *  NEXA AI — Smart Routing Manager v2
 *  Automatic online/offline AI routing
 *
 *  Modes:
 *  - ONLINE: Always use cloud API (Groq 70B / NEXA backend)
 *  - ON_DEVICE: Always use local model (Nexa SDK)
 *  - HYBRID: Auto-switch based on network, complexity, device
 *
 *  Routing logic:
 *  1. No network → on-device (if model available)
 *  2. Simple queries (<100 chars) → on-device (faster, saves data)
 *  3. Tool-related queries → online (needs API access)
 *  4. Vision with image → online (cloud VLM is better) or on-device VLM
 *  5. Complex queries (>500 chars) → online (better reasoning)
 * ═══════════════════════════════════════════════════════════════════
 */
@Singleton
class SmartRoutingManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val networkMonitor: NetworkMonitor
) {

    companion object {
        private const val TAG = "NexaSmartRoute"
        private const val SIMPLE_QUERY_MAX_CHARS = 100
        private const val COMPLEX_QUERY_MIN_CHARS = 500
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val onDeviceManager = OnDeviceInferenceManager(appContext)

    // ─── Routing State ───────────────────────────

    enum class InferenceMode(val label: String) {
        ONLINE("Cloud"),
        ON_DEVICE("On-Device"),
        HYBRID("Auto"),
    }

    private val _currentMode = MutableStateFlow(InferenceMode.HYBRID)
    val currentMode: StateFlow<InferenceMode> = _currentMode.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastRoutingDecision = MutableStateFlow<RoutingDecision?>(null)
    val lastRoutingDecision: StateFlow<RoutingDecision?> = _lastRoutingDecision.asStateFlow()

    private val _isOnDeviceActive = MutableStateFlow(false)
    val isOnDeviceActive: StateFlow<Boolean> = _isOnDeviceActive.asStateFlow()

    // ─── Network Detection ───────────────────────

    fun updateNetworkStatus(connected: Boolean) {
        val wasOnline = _isOnline.value
        _isOnline.value = connected

        if (!wasOnline && connected) {
            Log.i(TAG, "Network restored — back to HYBRID mode")
            _currentMode.value = InferenceMode.HYBRID
        } else if (wasOnline && !connected && onDeviceManager.isReady.value && onDeviceManager.getDownloadedModels().isNotEmpty()) {
            Log.i(TAG, "Network lost — switching to ON_DEVICE mode")
            _currentMode.value = InferenceMode.ON_DEVICE
            _isOnDeviceActive.value = true
        }
    }

    fun setMode(mode: InferenceMode) {
        _currentMode.value = mode
        Log.i(TAG, "Mode changed to ${mode.label}")
    }

    // ─── Routing Decision ────────────────────────

    data class RoutingDecision(
        val useOnDevice: Boolean,
        val reason: String,
        val confidence: Float,
        val fallbackMessage: String? = null,
        val mode: InferenceMode = InferenceMode.HYBRID,
    )

    /**
     * Determine routing for a chat query.
     */
    fun routeChat(query: String): RoutingDecision {
        val decision = shouldUseOnDevice(query, hasImage = false)
        _lastRoutingDecision.value = decision
        _isOnDeviceActive.value = decision.useOnDevice
        return decision
    }

    /**
     * Determine routing for a vision query.
     */
    fun routeVision(): RoutingDecision {
        val online = _isOnline.value
        val hasVisionModel = onDeviceManager.getDownloadedModels().any {
            val model = OnDeviceInferenceManager.AVAILABLE_MODELS.find { m -> m.id == it.id }
            model?.type?.name == "VLM"
        }

        // Prefer cloud for vision (GLM-4.6V is much better than on-device VLM)
        if (online) {
            val decision = RoutingDecision(
                useOnDevice = false,
                reason = "Visión → Cloud GLM-4.6V (mayor calidad)",
                confidence = 0.95f,
            )
            _lastRoutingDecision.value = decision
            _isOnDeviceActive.value = false
            return decision
        }

        // Offline: try on-device VLM if available
        if (hasVisionModel) {
            val decision = RoutingDecision(
                useOnDevice = true,
                reason = "Offline — usando VLM local (SmolVLM)",
                confidence = 0.7f,
            )
            _lastRoutingDecision.value = decision
            _isOnDeviceActive.value = true
            return decision
        }

        val decision = RoutingDecision(
            useOnDevice = false,
            reason = "Sin conexión y sin modelo de visión local",
            confidence = 0.0f,
            fallbackMessage = "No hay conexión a internet y no hay modelo de visión descargado. Conéctate a internet o descarga un modelo VLM en Ajustes.",
        )
        _lastRoutingDecision.value = decision
        _isOnDeviceActive.value = false
        return decision
    }

    private fun shouldUseOnDevice(query: String, hasImage: Boolean): RoutingDecision {
        val mode = _currentMode.value
        val online = _isOnline.value
        val onDeviceReady = onDeviceManager.isReady.value
        val hasDownloadedModels = onDeviceManager.getDownloadedModels().isNotEmpty()

        // Force mode: ONLINE
        if (mode == InferenceMode.ONLINE) {
            return RoutingDecision(
                useOnDevice = false,
                reason = "Modo Cloud forzado por usuario",
                confidence = 1.0f,
                mode = mode,
            )
        }

        // Force mode: ON_DEVICE
        if (mode == InferenceMode.ON_DEVICE) {
            if (!hasDownloadedModels) {
                return RoutingDecision(
                    useOnDevice = false,
                    reason = "Modo On-Device seleccionado pero sin modelos descargados",
                    confidence = 0.0f,
                    fallbackMessage = "No hay modelos descargados. Ve a Ajustes > IA Offline para descargar uno.",
                    mode = mode,
                )
            }
            return RoutingDecision(
                useOnDevice = true,
                reason = "Modo On-Device forzado por usuario",
                confidence = 1.0f,
                mode = mode,
            )
        }

        // HYBRID mode — intelligent routing

        // Rule 1: No network → on-device
        if (!online) {
            return if (hasDownloadedModels) {
                RoutingDecision(
                    useOnDevice = true,
                    reason = "Sin conexión — modo offline automático",
                    confidence = 1.0f,
                )
            } else {
                RoutingDecision(
                    useOnDevice = false,
                    reason = "Sin conexión y sin modelos locales",
                    confidence = 0.0f,
                    fallbackMessage = "Sin conexión a internet. Para usar IA offline, descarga un modelo en Ajustes.",
                )
            }
        }

        // Rule 2: Tool-related queries → always online
        if (hasToolKeywords(query)) {
            return RoutingDecision(
                useOnDevice = false,
                reason = "Consulta con herramientas → Cloud (necesita APIs externas)",
                confidence = 0.95f,
            )
        }

        // Rule 3: Complex queries → online (better reasoning with 70B models)
        if (query.length > COMPLEX_QUERY_MIN_CHARS || isComplexQuery(query)) {
            return RoutingDecision(
                useOnDevice = false,
                reason = "Consulta compleja → Cloud (Groq 70B, mejor razonamiento)",
                confidence = 0.85f,
            )
        }

        // Rule 4: Simple queries → on-device if available (faster, no latency)
        if (hasDownloadedModels && query.length < SIMPLE_QUERY_MAX_CHARS && isSimpleQuery(query)) {
            return RoutingDecision(
                useOnDevice = true,
                reason = "Consulta simple → On-Device (ultrarrápido, sin latencia)",
                confidence = 0.8f,
            )
        }

        // Rule 5: NPU available + downloaded model → on-device preference
        if (hasDownloadedModels && onDeviceManager.isNPUAvailable()) {
            return RoutingDecision(
                useOnDevice = true,
                reason = "NPU Snapdragon disponible → On-Device preferido",
                confidence = 0.6f,
            )
        }

        // Default: online
        return RoutingDecision(
            useOnDevice = false,
            reason = "Cloud preferido (mayor capacidad)",
            confidence = 0.7f,
        )
    }

    // ─── Query Analysis ──────────────────────────

    private fun isSimpleQuery(query: String): Boolean {
        val patterns = listOf(
            "hola", "buenos días", "buenas tardes", "buenas noches",
            "gracias", "adiós", "qué hora es", "qué día es", "qué fecha",
            "hello", "hi", "thanks", "bye", "good morning",
            "quién eres", "qué eres", "qué puedes hacer",
            "cómo te llamas", "cuál es tu nombre",
        )
        val lower = query.lowercase().trim()
        return patterns.any { lower.startsWith(it) || lower == it }
    }

    private fun isComplexQuery(query: String): Boolean {
        return query.contains("paso a paso") ||
               query.contains("step by step") ||
               query.contains("explica en detalle") ||
               query.contains("explain in detail") ||
               query.contains("compara") ||
               query.contains("analiza") ||
               query.contains("crea una página") ||
               query.contains("escribe código") ||
               query.count { it == '\n' } > 3
    }

    private fun hasToolKeywords(query: String): Boolean {
        val keywords = listOf(
            "busca", "busca en", "search", "google",
            "clima", "weather", "temperatura",
            "vuelo", "flight", "reserva",
            "noticias", "news",
            "genera imagen", "genera video", "dibuja",
            "traduce", "translate",
            "calcula", "cuánto es", "cuántos",
            "moneda", "dólar", "euro",
            "crear video", "create video",
        )
        val lower = query.lowercase()
        return keywords.any { lower.contains(it) }
    }

    // ─── Lifecycle ───────────────────────────────

    suspend fun initialize(): Boolean {
        // Start monitoring network status
        scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                updateNetworkStatus(isOnline)
            }
        }
        return onDeviceManager.initialize()
    }

    fun getDeviceCapabilities() = onDeviceManager.getDeviceCapabilities()
    fun getOnDeviceManager() = onDeviceManager

    fun shutdown() {
        scope.cancel()
        onDeviceManager.shutdown()
    }
}
