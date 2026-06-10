package com.nexa.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.PersistedMessage
import com.nexa.ai.data.PersistedSession
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.ResponseSanitizer
import com.nexa.ai.data.StreamEvent
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.iot.IoTManager
import com.nexa.ai.media.VideoGenerator
import com.nexa.ai.ml.EnhancedEmotionAnalyzer
import com.nexa.ai.ml.OnDeviceMLEngine
import com.nexa.ai.ml.OnDeviceInferenceManager
import com.nexa.ai.ml.SmartRoutingManager
import com.nexa.ai.ml.SmartRoutingManager.InferenceMode
import com.nexa.ai.ml.UserProfileManager
import com.nexa.ai.memory.EpisodicMemoryManager
import com.nexa.ai.memory.MemoryStats
import com.nexa.ai.sensors.NexaSensorManager
import com.nexa.ai.ui.NexaStrings
import com.nexa.ai.voice.NaturalConversationEngine
import com.nexa.ai.voice.VoiceEnhancer
import com.nexa.ai.voice.NexaSpeechService
import com.nexa.ai.voice.SpeechManager
import com.nexa.ai.web.WebResultProcessor
import com.nexa.ai.web.WebSearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nexa.ai.data.ContextProvider
import com.nexa.ai.viewmodel.usecase.ChatUseCase
import com.nexa.ai.viewmodel.usecase.VoiceUseCase

@HiltViewModel
class NexaViewModel @Inject constructor(
    application: Application,
    private val chatUseCase: ChatUseCase, // ✅ New UseCase
    private val voiceUseCase: VoiceUseCase, // ✅ Inject VoiceUseCase
    private val speechManager: SpeechManager,
    private val voiceEnhancer: VoiceEnhancer,
    private val repository: NexaRepository,

    private val contextProvider: ContextProvider,
    private val responseSanitizer: ResponseSanitizer,
    private val locationStore: LocationStore,
    private val updateChecker: UpdateChecker,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val conversationEngine: NaturalConversationEngine,
    private val sensorManager: NexaSensorManager,
    private val iotManager: IoTManager,
    private val mlEngine: OnDeviceMLEngine,
    private val videoGenerator: VideoGenerator,
    // ─── NEW: Previously orphaned modules, now wired in ───
    private val webSearchManager: WebSearchManager,
    private val webResultProcessor: WebResultProcessor,
    private val episodicMemoryManager: EpisodicMemoryManager,
    private val enhancedEmotionAnalyzer: EnhancedEmotionAnalyzer,
    private val userProfileManager: UserProfileManager,
    private val localLLMManager: com.nexa.ai.offline.LocalLLMManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()
    // Expose use case states
    val chatState = chatUseCase.state
    val voiceState = voiceUseCase.state

    // Managers still created locally (not yet in DI module — SpeechManager and AuthManager depend on Activity lifecycle)
    // Removed redundant local SpeechManager; using injected instance
    private val authManager = AuthManager(application)

    // ─── Smart Router: Online/Offline AI routing ───
    private val smartRouter = SmartRoutingManager(application)

    // Track last synced assistant message to avoid duplicate UI additions and trigger TTS in voiceMode
    private var lastSyncedAssistantMessage: String? = null

    // Voice command handler — extracted from this ViewModel to reduce complexity
    private val voiceCommandsHandler = VoiceCommandsHandler(iotManager, videoGenerator)

    // BroadcastReceiver for Persistent background speech recognition service
    // FIX: Only update UI state from broadcasts — do NOT re-invoke SpeechManager callbacks.
    // SpeechManager handles its own callbacks directly. Re-invoking them from broadcasts
    // caused double processing (broadcast -> callback -> same logic again).
    private val speechReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent == null) return
            when (intent.action) {
                NexaSpeechService.ACTION_SPEECH_RESULT -> {
                    // Only update UI — SpeechManager.onSpeechResult handles the actual logic
                    val text = intent.getStringExtra(NexaSpeechService.EXTRA_TEXT) ?: return
                    _uiState.update { it.copy(inputText = text) }
                }
                NexaSpeechService.ACTION_SPEECH_PARTIAL -> {
                    // Only update UI partial text display
                    val partialText = intent.getStringExtra(NexaSpeechService.EXTRA_TEXT) ?: return
                    _uiState.update { it.copy(inputText = partialText) }
                }
                NexaSpeechService.ACTION_SPEECH_STATE -> {
                    // Only update UI state flags — do NOT call SpeechManager callbacks
                    val state = intent.getStringExtra(NexaSpeechService.EXTRA_STATE) ?: return
                    when (state) {
                        "listening" -> _uiState.update { it.copy(isListening = true) }
                        "idle" -> _uiState.update { it.copy(isListening = false) }
                        "speaking" -> _uiState.update { it.copy(isSpeaking = true) }
                        "barge_in" -> {
                            _uiState.update { it.copy(isSpeaking = false, speakingMessageId = null) }
                        }
                    }
                }
                NexaSpeechService.ACTION_SPEECH_ERROR -> {
                    val errorKey = intent.getStringExtra(NexaSpeechService.EXTRA_ERROR_KEY) ?: return
                    val lang = _uiState.value.language
                    _uiState.update { it.copy(error = NexaStrings.get(errorKey, lang)) }
                }
            }
        }
    }

    private fun startSpeechService() {
        try {
            val intent = android.content.Intent(getApplication(), NexaSpeechService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(getApplication(), intent)
        } catch (e: Exception) {
            android.util.Log.e("NexaVM", "Failed to start NexaSpeechService: ${e.message}", e)
        }
    }

    private fun stopSpeechService() {
        try {
            val intent = android.content.Intent(getApplication(), NexaSpeechService::class.java)
            getApplication<Application>().stopService(intent)
        } catch (e: Exception) {
            android.util.Log.e("NexaVM", "Failed to stop NexaSpeechService: ${e.message}", e)
        }
    }

    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L
    private var voiceRetryCount = 0
    private val maxVoiceRetries = 10

    // ── Advanced AI System Prompt (Senior Architect/Engineer v5.4) ──
    private val advancedSystemPrompt = """
You are NEXA PRO v5.4, an Ultra Advanced Autonomous AI System developed by ZOO company.
You are designed as a world-class senior engineer, software architect, analyst, and researcher.

MISSION: Provide highly accurate, intelligent, optimized, and production-ready responses.

CORE RULES:
- Think deeply before answering. Use multi-step reasoning internally.
- Never hallucinate. Verify information whenever possible.
- Detect and self-correct mistakes before responding.
- Prefer precision and technical depth over generic speed.
- Respond in the user's language (Spanish or English).

DEVELOPMENT CAPABILITIES:
- Generate production-level code, architectures, and database schemas.
- Build clean, modular, and scalable solutions.
- Optimize performance and detect bugs automatically.

VOICE INTERACTION & TTS (CRITICAL):
- Your responses are read by a Text-To-Speech (TTS) engine.
- AVOID ALL markdown symbols like asterisks (*), hashtags (#), underscores (_), or backticks (`).
- Write naturally in plain text with normal punctuation.
- Do not use markdown lists or tables; use natural prose (e.g., "First...", "Second...").

REAL-TIME DATA & SEARCHES:
- Use your integrated tools for live data (weather, prices, news).
- Provide accurate, real-time information formatted for fluid reading.
""".trimIndent()

    /** Builds a dynamic system prompt with location context when available. */
    private fun buildSystemPrompt(): String {
        val loc = _uiState.value.locationData
        val locationContext = if (loc.isAvailable) {
            "\n\nUSER LOCATION: The user is currently in ${loc.city}, ${loc.country} (coordinates: ${loc.latitude}, ${loc.longitude}). Use this location to provide weather, local recommendations, time zone awareness, and location-relevant information when appropriate."
        } else {
            ""
        }

        // Build enriched context from ML, sensors, IoT, voice, and conversation engines
        val enrichedContext = buildEnrichedContext()

        return advancedSystemPrompt + locationContext + enrichedContext
    }

    /**
     * Build enriched context from all AI enhancement subsystems.
     * This makes the AI aware of the user's physical environment, emotional state,
     * smart home devices, conversation context, and learned preferences.
     */
    private fun buildEnrichedContext(): String {
        // FIX v5.2: Memory-optimized context building
        // Only include MAX 2 compact context parts to prevent OOM on low-end devices
        val parts = mutableListOf<String>()
        val maxContextLen = 1200  // Hard limit to prevent memory pressure

        // 1. Sensor context (compact single line)
        try {
            val s = sensorManager.getContextForAI()
            if (s.isNotBlank()) parts.add("SENSOR: $s")
        } catch (_: Exception) {}

        // 2. Memory context (compact)
        try {
            val q = _uiState.value.messages.lastOrNull { it.role == "user" }?.content ?: ""
            val m = episodicMemoryManager.buildMemoryContext(q)
            if (m.isNotBlank()) parts.add("MEM: $m")
        } catch (_: Exception) {}

        // 3. Voice conversation context (topic, mood, phase)
        try {
            val voiceCtx = voiceUseCase.getConversationContext()
            if (voiceCtx.isNotBlank()) parts.add(voiceCtx)
        } catch (_: Exception) {}

        // NOTE: IoT, Video, Emotion, Profile contexts disabled to save memory
        // Re-enable selectively on devices with >4GB RAM if needed

        if (parts.isEmpty()) return ""
        val ctx = parts.joinToString(" | ")
        return if (ctx.length > maxContextLen) "\nCTX: ${ctx.take(maxContextLen)}..." else "\nCTX: $ctx"
    }

    // Debounce logic — prevents rapid/accidental voice triggers
    // v5.4: Reduced to 450ms for world-class fluidity
    private var speechDebounceJob: kotlinx.coroutines.Job? = null
    private val speechDebounceTimeMs = 450L

    // Track last successful voice result time to prevent duplicate sends
    private var lastVoiceResultAt = 0L
    private val voiceResultCooldownMs = 1000L

    private val surprisePromptsEs = listOf(
        "Cuéntame algo fascinante sobre el universo",
        "Dame una receta rápida y deliciosa",
        "¿Cuál es el mejor consejo de vida que puedes dar?",
        "Escribe un poema corto sobre la tecnología",
        "Explícame la mecánica cuántica como si tuviera 10 años",
        "¿Qué pasaría si los humanos pudieran volar?",
        "Dame 3 ideas para un negocio innovador",
        "Cuéntame una historia de ciencia ficción en 100 palabras",
        "¿Cuál es el misterio más grande de la humanidad?",
        "Dame un plan de ejercicios para 15 minutos"
    )

    private val surprisePromptsEn = listOf(
        "Tell me something fascinating about the universe",
        "Give me a quick and delicious recipe",
        "What's the best life advice you can give?",
        "Write a short poem about technology",
        "Explain quantum mechanics like I'm 10",
        "What if humans could fly?",
        "Give me 3 ideas for an innovative business",
        "Tell me a sci-fi story in 100 words"
    )

    init {
        setupSpeechCallbacks()
        // v5.4 FIX: initialize() is now idempotent — safe to call even if
        // NexaSpeechService also calls it (same singleton instance via Hilt)
        speechManager.initialize()
        
        // Register persistent Speech Service receiver (API 33+ safe)
        val filter = android.content.IntentFilter().apply {
            addAction(NexaSpeechService.ACTION_SPEECH_RESULT)
            addAction(NexaSpeechService.ACTION_SPEECH_PARTIAL)
            addAction(NexaSpeechService.ACTION_SPEECH_STATE)
            addAction(NexaSpeechService.ACTION_SPEECH_ERROR)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.registerReceiver(
                application, speechReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            application.registerReceiver(speechReceiver, filter)
        }

        // Initialize LocationStore and collect location updates
        viewModelScope.launch {
            locationStore.initialize()
            // Observe location changes
            locationStore.locationData.collect { locationData ->
                _uiState.update { it.copy(locationData = locationData) }
            }
        }
        restoreState()
        // Auto-request location on startup
        requestLocation()
        // Initialize ML & AI Enhancement subsystems
        initializeEnhancementSystems()
        // Initialize Smart Router for online/offline AI
        initializeSmartRouter()

        // ✅ Observe ChatUseCase state and sync with UI state
        viewModelScope.launch {
            chatUseCase.state.collect { chatState ->
                _uiState.update { current ->
                    current.copy(
                        isThinking = chatState.isLoading,
                        error = chatState.error
                    )
                }
                // Append new assistant message when available
                if (chatState.messages.isNotEmpty()) {
                    val last = chatState.messages.last()
                    if (last.ai != lastSyncedAssistantMessage) {
                        lastSyncedAssistantMessage = last.ai
                        val assistantMsg = Message(
                            id = "a-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}",
                            role = "assistant",
                            content = last.ai
                        )
                        updateActiveSession { session ->
                            val alreadyContains = session.messages.any { it.content == last.ai && it.role == "assistant" }
                            if (!alreadyContains) {
                                session.copy(messages = session.messages + assistantMsg)
                            } else {
                                session
                            }
                        }

                        // ✅ SPEECH SYNTHESIS: Speak if hands-free/voiceMode is enabled!
                        if (_uiState.value.voiceMode && _uiState.value.autoSpeak) {
                            speak(last.ai, assistantMsg.id)
                        }

                        // ✅ UPDATE CONVERSATION CONTEXT: Track topic, mood, and turn after AI response
                        voiceUseCase.updateConversationAfterResponse(
                            userMessage = chatState.messages.lastOrNull()?.user ?: "",
                            aiResponse = last.ai,
                            emotion = voiceEnhancer.voiceState.value.voiceEmotion
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════
    //  ML & AI ENHANCEMENT INITIALIZATION
    // ═══════════════════════════════════════

    private fun initializeEnhancementSystems() {
        // Start sensor monitoring
        try { sensorManager.startListening() } catch (_: Exception) {}

        // Initialize IoT demo devices
        viewModelScope.launch {
            try { iotManager.initDemoDevices() } catch (_: Exception) {}
        }

        // Voice enhancer: wake word detection
        voiceEnhancer.onWakeWordDetected = {
            if (_uiState.value.voiceMode) {
                android.util.Log.d("NexaVM", "Wake word detected!")
                // Already in voice mode, just acknowledge
            } else {
                // Activate voice mode on wake word
                _uiState.update { it.copy(voiceMode = true, autoSpeak = true) }
                startSpeechService()
            }
        }

        // Voice enhancer: IoT voice command detection
        voiceEnhancer.onIoTVoiceCommand = { commandText ->
            viewModelScope.launch {
                try {
                    val result = iotManager.processVoiceCommand(commandText)
                    speak(result)
                } catch (_: Exception) {}
            }
        }

        // Voice enhancer: language detection
        voiceEnhancer.onLanguageDetected = { language, confidence ->
            if (confidence > 0.7f) {
                val detectedLang = when (language) {
                    "en" -> AppLanguage.ENGLISH
                    "es" -> AppLanguage.SPANISH
                    else -> null
                }
                if (detectedLang != null && detectedLang != _uiState.value.language) {
                    android.util.Log.d("NexaVM", "Auto-detected language: $language ($confidence)")
                    // Don't auto-switch, just log for now — user may be bilingual
                }
            }
        }

        // Sensor manager: context changes
        sensorManager.onContextChanged = { oldContext, newContext ->
            android.util.Log.d("NexaVM", "Context changed: $oldContext → $newContext")
        }

        // Sensor manager: activity changes
        sensorManager.onActivityChanged = { activity ->
            android.util.Log.d("NexaVM", "Activity detected: $activity")
            // Adapt voice settings based on activity
            when (activity) {
                "driving" -> {
                    speechManager.setVolumeBoost(true)
                    speechManager.setSpeechRate(0.9f)
                }
                "still" -> {
                    speechManager.setSpeechRate(1.0f)
                }
                "running", "walking" -> {
                    speechManager.setVolumeBoost(true)
                    speechManager.setSpeechRate(1.1f)
                }
            }
        }

        // Sensor manager: battery low
        sensorManager.onBatteryLow = {
            // Reduce background activity when battery is low
            voiceEnhancer.stopWakeWordDetection()
            android.util.Log.d("NexaVM", "Battery low — reducing background activity")
        }
    }

    // ═══════════════════════════════════════
    //  SMART ROUTER INITIALIZATION
    // ═══════════════════════════════════════

    private fun initializeSmartRouter() {
        viewModelScope.launch {
            try {
                smartRouter.initialize()
                val caps = smartRouter.getDeviceCapabilities()
                _uiState.update {
                    it.copy(
                        npuAvailable = caps.hasNPU,
                        hasDownloadedModels = caps.loadedModel != null,
                        inferenceMode = InferenceMode.HYBRID.name,
                    )
                }
                android.util.Log.d("NexaVM", "Smart Router initialized — NPU: ${caps.hasNPU}, Model: ${caps.loadedModel}")
            } catch (e: Exception) {
                android.util.Log.w("NexaVM", "Smart Router init failed: ${e.message}")
            }
        }
    }

    /** Switch inference mode (called from Settings UI). */
    fun setInferenceMode(mode: InferenceMode) {
        smartRouter.setMode(SmartRoutingManager.InferenceMode.valueOf(mode.name))
        _uiState.update { it.copy(inferenceMode = mode.name) }
    }

    /** Get device AI capabilities for settings display. */
    fun getDeviceCapabilities(): OnDeviceInferenceManager.DeviceCapabilities {
        return smartRouter.getDeviceCapabilities()
    }

    /** Get the on-device inference manager for model downloads. */
    fun getOnDeviceManager(): OnDeviceInferenceManager {
        return smartRouter.getOnDeviceManager()
    }

    // ═══════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════

    private fun setupSpeechCallbacks() {
        speechManager.onListeningStateChanged = { isListening ->
            _uiState.update { it.copy(isListening = isListening) }
            if (isListening) voiceRetryCount = 0 // Reset the counter if it starts listening properly!
        }
        
        speechManager.onSpeakingStateChanged = { isSpeaking, messageId ->
            _uiState.update { it.copy(isSpeaking = isSpeaking, speakingMessageId = messageId) }

            if (_uiState.value.voiceMode) {
                if (isSpeaking) {
                    // Barge-in: start AudioRecord monitor while AI is speaking
                    // AudioRecord works reliably even when TTS is active
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(150) // Quick settle, then monitor
                        if (_uiState.value.voiceMode && _uiState.value.isSpeaking) {
                            speechManager.startBargeInMonitor()
                        }
                    }
                } else {
                    // AI stopped speaking (finished or interrupted)
                    speechManager.stopBargeInMonitor()
                    // Start actual speech recognition if not already listening
                    if (!_uiState.value.isListening && !_uiState.value.isThinking) {
                        viewModelScope.launch {
                            // ═══ v5.4 SEAMLESS TRANSITION IMPROVEMENT ═══
                            // Reduced from 700ms to 150ms for near-instant conversation.
                            kotlinx.coroutines.delay(150)
                            if (_uiState.value.voiceMode && !_uiState.value.isListening &&
                                !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                                speechManager.startListening()
                            }
                        }
                    }
                }
            }
        }
        
        // Barge-in: AudioRecord detected user voice while AI was speaking
        speechManager.onBargeInDetected = {
            if (_uiState.value.voiceMode) {
                speechManager.stopBargeInMonitor()
                speechManager.stopSpeaking()
                // Update UI to show barge-in state
                _uiState.update { it.copy(isSpeaking = false, speakingMessageId = null) }
                // Start actual speech recognition now
                viewModelScope.launch {
                    // ═══ v5.1 FIX ═══
                    // Increased from 80ms to 200ms — gives audio system
                    // more time to stabilize after stopping TTS output
                    kotlinx.coroutines.delay(200)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening) {
                        speechManager.startListening()
                    }
                }
            }
        }
        
        speechManager.onSpeechResult = { text ->
            if (_uiState.value.voiceMode) {
                // Prevent duplicate sends from rapid recognition results
                val now = System.currentTimeMillis()
                if (now - lastVoiceResultAt < voiceResultCooldownMs) {
                    android.util.Log.d("NexaVM", "Dropping duplicate voice result: $text")
                } else {
                    lastVoiceResultAt = now

                    // Barge-in: if AI was speaking, it's already stopped by onBargeInDetected
                    // but double-check in case onBeginningOfSpeech didn't fire
                    if (_uiState.value.isSpeaking) {
                        speechManager.stopSpeaking()
                    }

                    // Apply debounce logic to avoid rapid/accidental triggers
                    speechDebounceJob?.cancel()
                    speechDebounceJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(speechDebounceTimeMs)

                        speechManager.stopListening() // Force stop before processing

                        if (text.trim().length >= 2) {
                            kotlinx.coroutines.delay(200)
                            sendMessage(text)
                        } else {
                            // Accidental noise, restart listening with delay
                            kotlinx.coroutines.delay(800)
                            if (_uiState.value.voiceMode && !_uiState.value.isListening) {
                                speechManager.startListening()
                            }
                        }
                    }
                }
            } else {
                sendMessage(text)
            }
        }
        
        speechManager.onSpeechPartial = { text ->
            _uiState.update { it.copy(inputText = text) }
        }
        
        speechManager.onError = { errorKey ->
            if (_uiState.value.voiceMode) {
                voiceRetryCount++

                if (voiceRetryCount >= maxVoiceRetries) {
                    // If it fails too many times, turn off voice mode to avoid annoying the user
                    _uiState.update { it.copy(voiceMode = false) }
                    speechManager.stopListening()
                    stopVoiceMode()
                } else {
                    // Normal retry with exponential backoff
                    val delayMs = (2000L * (1 + voiceRetryCount / 3)).coerceAtMost(5000L)
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(delayMs)
                        if (_uiState.value.voiceMode && !_uiState.value.isListening && !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                            speechManager.startListening()
                        }
                    }
                }
            } else {
                val lang = _uiState.value.language
                _uiState.update { it.copy(error = NexaStrings.get(errorKey, lang)) }
            }
        }
        
        speechManager.onInputTextChanged = { text ->
            _uiState.update { it.copy(inputText = text) }
        }
        
        // Voice mode: retry on recognition ended without match
        speechManager.onRecognitionEnded = {
            if (_uiState.value.voiceMode) {
                viewModelScope.launch {
                    // ═══ v5.4 FAST RE-ARM ═══
                    // Reduced from 2000ms to 400ms for near-instant re-listening
                    kotlinx.coroutines.delay(400)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening &&
                        !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                        speechManager.startListening()
                    }
                }
            }
        }
        
        // Real-time volume level for visual feedback in voice mode
        speechManager.onVolumeLevelChanged = { level ->
            if (_uiState.value.voiceMode) {
                _uiState.update { it.copy(voiceVolumeLevel = level) }
            }
        }

        // Proximity sensor: auto-switch earpiece/speaker
        speechManager.onProximityChanged = { isNearEar ->
            // Proximity is handled internally by SpeechManager for audio routing
            // This callback is for future UI updates if needed
            android.util.Log.d("NexaVM", "Proximity changed: nearEar=$isNearEar")
        }
    }

    // ═══════════════════════════════════════
    //  STATE PERSISTENCE
    // ═══════════════════════════════════════

    private fun restoreState() {
        viewModelScope.launch {
            // Restore user
            val user = authManager.restoreUser()
            if (user != null) {
                _uiState.value = _uiState.value.copy(user = user)
            }

            // Restore persisted preferences (theme, language, voice)
            val savedTheme = settingsStore.themeMode.first()
            val savedLanguage = settingsStore.language.first()
            val savedVoice = settingsStore.voiceType.first()
            val savedAccent = settingsStore.accentColor.first()
            val savedGroqKey = settingsStore.groqApiKey.first()
            val savedUseLocalLLM = settingsStore.useLocalLLM.first()
            val savedAllowSync = settingsStore.allowSync.first()
            val savedMaxTokens = settingsStore.maxTokens.first()
            
            _uiState.value = _uiState.value.copy(
                themeMode = savedTheme,
                language = savedLanguage,
                voiceType = savedVoice,
                accentColor = savedAccent,
                groqApiKey = savedGroqKey,
                useLocalLLM = savedUseLocalLLM,
                allowSync = savedAllowSync,
                maxTokens = savedMaxTokens
            )
            speechManager.setLanguage(savedLanguage)
            speechManager.setVoiceType(savedVoice)

            // Restore sessions
            val savedSessions = sessionStore.sessions.first()
            val savedActiveId = sessionStore.activeSessionId.first()

            if (savedSessions.isNotEmpty()) {
                val sessions = savedSessions.map { ps ->
                    ChatSession(
                        id = ps.id,
                        title = ps.title,
                        messages = ps.messages.map { pm ->
                            Message(id = pm.id, role = pm.role, content = pm.content)
                        },
                        createdAt = ps.createdAt,
                        updatedAt = ps.updatedAt
                    )
                }
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    activeSessionId = savedActiveId ?: sessions.firstOrNull()?.id
                )
            } else {
                createNewSession()
            }

            checkForUpdates()
        }
    }

    private fun persistSessions() {
        viewModelScope.launch {
            val sessions = _uiState.value.sessions.map { s ->
                PersistedSession(
                    id = s.id,
                    title = s.title,
                    messages = s.messages.map { m ->
                        PersistedMessage(id = m.id, role = m.role, content = m.content)
                    },
                    createdAt = s.createdAt,
                    updatedAt = s.updatedAt
                )
            }
            sessionStore.save(sessions, _uiState.value.activeSessionId)
        }
    }

    // ═══════════════════════════════════════
    //  AUTO-UPDATE
    // ═══════════════════════════════════════

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val info = updateChecker.checkForUpdate(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
                if (info != null) {
                    _uiState.value = _uiState.value.copy(
                        updateInfo = info,
                        showUpdateDialog = true
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)
    }

    fun openUpdatePage() {
        val info = _uiState.value.updateInfo ?: return
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)
        updateChecker.downloadAndInstall(context, info.downloadUrl, info.versionName)
    }

    // ═══════════════════════════════════════
    //  LOGIN / REGISTER
    // ═══════════════════════════════════════

    fun navigateToLogin() {
        _uiState.value = _uiState.value.copy(
            currentScreen = Screen.LOGIN,
            loginEmail = "",
            loginPassword = "",
            loginError = null
        )
    }

    fun navigateToRegister() {
        _uiState.value = _uiState.value.copy(
            currentScreen = Screen.REGISTER,
            registerName = "",
            registerEmail = "",
            registerPassword = "",
            registerConfirmPassword = "",
            registerError = null
        )
    }

    fun navigateToChat() {
        _uiState.value = _uiState.value.copy(currentScreen = Screen.CHAT)
    }

    fun navigateToLottery() {
        _uiState.value = _uiState.value.copy(currentScreen = Screen.LOTTERY, drawerOpen = false)
    }

    fun navigateToTranslator() {
        _uiState.value = _uiState.value.copy(currentScreen = Screen.TRANSLATOR, drawerOpen = false)
    }
    

    fun updateLoginEmail(email: String) {
        _uiState.value = _uiState.value.copy(loginEmail = email)
    }

    fun updateLoginPassword(password: String) {
        _uiState.value = _uiState.value.copy(loginPassword = password)
    }

    fun updateRegisterName(name: String) {
        _uiState.value = _uiState.value.copy(registerName = name)
    }

    fun updateRegisterEmail(email: String) {
        _uiState.value = _uiState.value.copy(registerEmail = email)
    }

    fun updateRegisterPassword(password: String) {
        _uiState.value = _uiState.value.copy(registerPassword = password)
    }

    fun updateRegisterConfirmPassword(password: String) {
        _uiState.value = _uiState.value.copy(registerConfirmPassword = password)
    }

    fun login() {
        val email = _uiState.value.loginEmail.trim()
        val password = _uiState.value.loginPassword
        val lang = _uiState.value.language

        _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)

            when (val result = authManager.login(email, password)) {
                is LoginResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        user = result.user,
                        currentScreen = Screen.CHAT,
                        isLoggingIn = false
                    )
                }
                is LoginResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        loginError = NexaStrings.get(result.messageKey, lang),
                        isLoggingIn = false
                    )
                }
            }
        }
    }

    fun register() {
        val name = _uiState.value.registerName.trim()
        val email = _uiState.value.registerEmail.trim()
        val password = _uiState.value.registerPassword
        val confirm = _uiState.value.registerConfirmPassword
        val lang = _uiState.value.language

        _uiState.value = _uiState.value.copy(isRegistering = true, registerError = null)

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)

            when (val result = authManager.register(name, email, password, confirm)) {
                is RegisterResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        user = result.user,
                        currentScreen = Screen.CHAT,
                        isRegistering = false
                    )
                }
                is RegisterResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        registerError = NexaStrings.get(result.messageKey, lang),
                        isRegistering = false
                    )
                }
            }
        }
    }

    fun logout() {
        speechManager.stopSpeaking()
        viewModelScope.launch {
            authManager.logout()
            sessionStore.clear()
        }
        _uiState.value = _uiState.value.copy(
            user = UserData(),
            sessions = emptyList(),
            activeSessionId = null,
            currentScreen = Screen.CHAT,
            drawerOpen = false
        )
        createNewSession()
    }

    // ═══════════════════════════════════════
    //  SPEECH (delegated to SpeechManager)
    // ═══════════════════════════════════════

    fun speak(text: String, messageId: String? = null) {
        speechManager.speak(text, messageId, _uiState.value.speakingMessageId)
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
    }

    fun startListening() {
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    // ═══════════════════════════════════════
    //  SURPRISE ME
    // ═══════════════════════════════════════

    fun surpriseMe() {
        val prompts = when (_uiState.value.language) {
            AppLanguage.SPANISH -> surprisePromptsEs
            AppLanguage.ENGLISH -> surprisePromptsEn
        }
        sendMessage(prompts.random())
    }

    // ═══════════════════════════════════════
    //  ATTACHMENT
    // ═══════════════════════════════════════

    fun setPendingAttachment(fileName: String) {
        _uiState.value = _uiState.value.copy(pendingAttachment = fileName)
    }

    fun clearPendingAttachment() {
        _uiState.value = _uiState.value.copy(pendingAttachment = null)
    }

    // ═══════════════════════════════════════
    //  SESSION MANAGEMENT
    // ═══════════════════════════════════════

    fun createNewSession() {
        val session = ChatSession()
        _uiState.value = _uiState.value.copy(
            sessions = listOf(session) + _uiState.value.sessions,
            activeSessionId = session.id,
            drawerOpen = false
        )
        persistSessions()
    }

    fun switchSession(sessionId: String) {
        speechManager.stopSpeaking()
        _uiState.value = _uiState.value.copy(
            activeSessionId = sessionId,
            drawerOpen = false,
            error = null
        )
        persistSessions()
    }

    fun deleteSession(sessionId: String) {
        val updated = _uiState.value.sessions.filter { it.id != sessionId }
        val newActive = if (_uiState.value.activeSessionId == sessionId) {
            updated.firstOrNull()?.id
        } else {
            _uiState.value.activeSessionId
        }

        _uiState.value = _uiState.value.copy(
            sessions = updated,
            activeSessionId = newActive
        )

        if (updated.isEmpty()) {
            createNewSession()
        } else {
            persistSessions()
        }
    }

    private fun updateActiveSession(transform: (ChatSession) -> ChatSession) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == _uiState.value.activeSessionId }
        if (idx >= 0) {
            sessions[idx] = transform(sessions[idx])
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessions()
        }
    }

    // ═══════════════════════════════════════
    //  CHAT
    // ═══════════════════════════════════════

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage(text: String? = null) {
        val content = text ?: _uiState.value.inputText.trim()
        if (content.isBlank() && _uiState.value.pendingAttachment == null) return

        // Voice-mode mute handling (existing logic preserved)
        if (_uiState.value.voiceMode) {
            speechManager.stopListening()
            speechManager.stopSpeaking()
        }

        // ✅ Voice command handler: try to handle commands before AI processing
        if (_uiState.value.voiceMode) {
            val commandResult = voiceCommandsHandler.tryHandleCommand(
                context = getApplication(),
                cmd = content,
                lang = _uiState.value.language,
                messages = _uiState.value.messages,
                onClearChat = { clearChat() },
                onExportPdf = { /* Export handled by UI */ },
                onStopVoiceMode = { stopVoiceMode() },
                onSetLanguage = { setLanguage(it) },
                onSetVoiceType = { setVoiceType(it) },
                onCreateSession = { createNewSession() },
                onRepeatLast = {
                    val lastAssistant = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) speak(lastAssistant.content)
                },
                onSpeakLast = {
                    val lastAssistant = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) speak(lastAssistant.content)
                },
                onSpeak = { speak(it) },
                onStopSpeaking = { speechManager.stopSpeaking() },
                onShowHelp = { _uiState.update { it.copy(showVoiceCommandsHelp = true) } },
                onReadLast = {
                    val lastAssistant = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) speak(lastAssistant.content)
                },
                onSetThemeMode = { setThemeMode(it) },
                onOpenSettings = { /* Settings handled by UI */ },
                onSendMessage = { sendMessage(it) },
                onShareLast = { /* Share handled by UI */ },
                onCameraCapture = { /* Camera handled by UI */ },
                onGenerateVideo = { prompt, style ->
                    viewModelScope.launch {
                        try { videoGenerator.generateVideo(VideoGenerator.VideoRequest(prompt = prompt, style = style)) } catch (_: Exception) {}
                    }
                }
            )
            when (commandResult) {
                is VoiceCommandsHandler.VoiceCommandResult.Handled -> {
                    if (commandResult.spokenResponse.isNotBlank()) {
                        speak(commandResult.spokenResponse)
                    }
                    // Process voice input through conversation engine for context tracking
                    voiceUseCase.processVoiceInput(content)
                    return
                }
                is VoiceCommandsHandler.VoiceCommandResult.NotRecognized -> {
                    // Not a command, proceed with normal AI processing
                }
            }

            // Also handle async IoT commands
            if (voiceCommandsHandler.isIoTCommand(content)) {
                viewModelScope.launch {
                    try {
                        val result = iotManager.processVoiceCommand(content)
                        speak(result)
                    } catch (_: Exception) {}
                }
                voiceUseCase.processVoiceInput(content)
                return
            }

            // Handle routines (buenos días, buenas noches)
            voiceCommandsHandler.getRoutineId(content)?.let { routineId ->
                viewModelScope.launch {
                    try {
                        val result = iotManager.executeRoutine(routineId)
                        speak(result)
                    } catch (_: Exception) {}
                }
                voiceUseCase.processVoiceInput(content)
                return
            }
        }

        // --- VOICE COMMAND DETECTION ---
        if (_uiState.value.voiceMode) {
            val cmd = content.lowercase().trim()
            val lang = _uiState.value.language
            
            // ✅ HANDS-FREE VISION COMMANDS
            if (cmd.contains("activa visión manos libres") || cmd.contains("activar visión manos libres") || cmd.contains("enable hands free vision")) {
                _uiState.update { it.copy(isHandsFreeVisionActive = true) }
                speak(if (lang == AppLanguage.SPANISH) "Modo visión manos libres activado. Ahora puedes decir mira esto o qué ves." else "Hands-free vision mode enabled. You can now say look at this or what do you see.")
                return
            }
            if (cmd.contains("mira esto") || cmd.contains("qué ves") || cmd.contains("describe lo que ves") || 
                cmd.contains("look at this") || cmd.contains("what do you see") || cmd.contains("describe what you see")) {
                
                speak(if (lang == AppLanguage.SPANISH) "Entendido, estoy mirando. Por favor, apunta la cámara y toma la foto." else "Understood, I'm looking. Please point the camera and take the photo.")
                // Request camera capture via UI
                _uiState.update { it.copy(requestCameraCapture = true) }
                return
            }
            
            if (cmd.contains("limpiar chat") || cmd.contains("borra el chat") || cmd.contains("clear chat")) {
                clearChat()
                return
            }
        }

        // Build final user message (with attachment prefix)
        val fullContent = if (_uiState.value.pendingAttachment != null) {
            "📎 ${_uiState.value.pendingAttachment}\n$content"
        } else content

        // Update UI state immediately (add user message, clear input)
        val userMsg = Message(
            role = "user",
            content = fullContent,
            attachmentName = _uiState.value.pendingAttachment
        )
        val assistantId = "a-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
        updateActiveSession { session ->
            session.copy(
                messages = session.messages + userMsg,
                updatedAt = System.currentTimeMillis()
            )
        }
        _uiState.value = _uiState.value.copy(
            inputText = "",
            isThinking = true,
            error = null,
            pendingAttachment = null
        )

        // ✅ Delegate core AI interaction to ChatUseCase
        viewModelScope.launch {
            var webContext: String? = null
            
            // Smart Web Search Trigger: check for news, prices, weather, etc.
            val needsWeb = Regex("(?i)(precio|vuelo|noticia|clima|weather|price|news|flight|cotización|dólar|bitcoin|ethereum|hoy|ahora|live|actual)").containsMatchIn(content)
            
            if (needsWeb) {
                try {
                    val searchResult = performWebSearch(content)
                    if (searchResult.summary.isNotBlank()) {
                        webContext = searchResult.summary
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NexaVM", "Web search failed: ${e.message}")
                }
            }

            chatUseCase.apply {
                baseUrl = BuildConfig.API_BASE_URL
                systemPrompt = buildSystemPrompt()
                groqApiKey = _uiState.value.groqApiKey
                maxTokens = _uiState.value.maxTokens
                useLocalLLM = _uiState.value.useLocalLLM
            }
            chatUseCase.sendMessage(fullContent, viewModelScope, extraContext = webContext)
        }
    }

    fun onError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun regenerateResponse() {
        speechManager.stopSpeaking()
        
        // Remove the last assistant message from the active session
        updateActiveSession { session ->
            if (session.messages.isNotEmpty() && session.messages.last().role == "assistant") {
                session.copy(messages = session.messages.dropLast(1))
            } else session
        }
        
        _uiState.update { it.copy(isThinking = true, error = null) }
        chatUseCase.regenerateResponse(viewModelScope)
    }

    fun toggleAutoSpeak() {
        _uiState.value = _uiState.value.copy(autoSpeak = !_uiState.value.autoSpeak)
        if (!_uiState.value.autoSpeak) speechManager.stopSpeaking()
    }

    fun toggleVoiceMode() {
        val activating = !_uiState.value.voiceMode
        _uiState.value = _uiState.value.copy(voiceMode = activating, voiceVolumeLevel = 0f)
        if (activating) {
            // Enable auto-speak so AI responses are spoken aloud
            _uiState.value = _uiState.value.copy(autoSpeak = true)
            // Start SpeechManager audio session for proper hands-free audio routing
            speechManager.startVoiceAudioSession()
            startSpeechService()
            // Start voice session tracking via VoiceUseCase
            voiceUseCase.startVoiceSession()

            // CRITICAL FIX: Start listening immediately after activating voice mode!
            // Without this, the mic never turns on and voice input doesn't work.
            // The old code only started listening after TTS finished speaking,
            // but if the AI hasn't spoken yet, the mic would never activate.
            viewModelScope.launch {
                kotlinx.coroutines.delay(500) // Wait for audio session to fully initialize
                if (_uiState.value.voiceMode && !_uiState.value.isListening &&
                    !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                    speechManager.startListening()
                    android.util.Log.d("NexaVM", "Voice mode activated — starting initial listening")
                }
            }
        } else {
            // Stop SpeechManager audio session and release audio routing
            speechManager.stopVoiceAudioSession()
            stopSpeechService()
            // Reset voice session state
            voiceUseCase.stopVoiceSession()
        }
    }

    fun stopVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = false, isHandsFreeVisionActive = false)
        speechManager.stopVoiceAudioSession()
        stopSpeechService()
        voiceUseCase.stopVoiceSession()
    }

    fun interruptVoice() {
        speechManager.stopBargeInMonitor()
        speechManager.stopSpeaking()
        // stopSpeaking triggers onSpeakingStateChanged(false) which starts listening
    }

    fun dismissVoiceCommandsHelp() {
        _uiState.update { it.copy(showVoiceCommandsHelp = false) }
    }

    // ═══════════════════════════════════════
    //  LOCATION
    // ═══════════════════════════════════════

    fun requestLocation() {
        // v4.0: Check if location permissions are granted before requesting
        if (!locationStore.hasLocationPermission()) {
            android.util.Log.w("NexaVM", "Location permission not granted, skipping request")
            _uiState.update { it.copy(isLocating = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            try {
                val location = locationStore.getCurrentLocation()
                _uiState.update { it.copy(locationData = location, isLocating = false) }
                if (location.isAvailable) {
                    android.util.Log.d("NexaVM", "Location obtained: ${location.city}, ${location.country} (${location.latitude}, ${location.longitude})")
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Location error: ${e.message}", e)
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    /** v4.0: Check if location services are enabled on the device. */
    fun isLocationEnabled(): Boolean = locationStore.isLocationEnabled()

    /** v4.0: Check if location permissions are granted. */
    fun hasLocationPermission(): Boolean = locationStore.hasLocationPermission()

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !_uiState.value.notificationsEnabled) }
    }

    // ═══════════════════════════════════════
    //  VOLUME & SPEECH RATE
    // ═══════════════════════════════════════

    fun toggleVolumeBoost() {
        val enabled = !_uiState.value.volumeBoostEnabled
        _uiState.update { it.copy(volumeBoostEnabled = enabled) }
        speechManager.setVolumeBoost(enabled)
    }

    fun setSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        speechManager.setSpeechRate(rate)
    }

    // ═══════════════════════════════════════
    //  CAMERA VISION
    // ═══════════════════════════════════════

    fun setCameraImage(base64: String?) {
        _uiState.update { it.copy(cameraImageUri = base64, requestCameraCapture = false) }
    }

    fun clearCameraRequest() {
        _uiState.update { it.copy(requestCameraCapture = false) }
    }

    fun sendVisionRequest(base64Image: String, mimeType: String = "image/jpeg") {
        val lang = _uiState.value.language
        val question = if (lang == AppLanguage.SPANISH)
            "Analiza esta imagen y describe lo que ves en detalle. Incluye objetos, personas, texto, colores, escena y cualquier información relevante."
        else
            "Analyze this image and describe what you see in detail. Include objects, people, text, colors, scene, and any relevant information."

        _uiState.update { it.copy(cameraImageUri = null, requestCameraCapture = false) }

        // Create user message with vision indicator
        val userMsg = Message(
            role = "user",
            content = if (lang == AppLanguage.SPANISH) "[Imagen analizada]" else "[Image analyzed]",
        )
        val assistantId = "a-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"

        updateActiveSession { s ->
            s.copy(
                messages = s.messages + userMsg,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.value = _uiState.value.copy(isThinking = true, error = null)
        
        // Audible feedback for Hands-Free
        if (_uiState.value.voiceMode) {
            speak(if (lang == AppLanguage.SPANISH) "Imagen recibida. Analizando..." else "Image received. Analyzing...")
        }

        viewModelScope.launch {
            try {
                var result = ""

                // Smart routing: try on-device first if offline, otherwise use cloud
                val smartRouter = SmartRoutingManager(getApplication())
                val visionDecision = smartRouter.routeVision()

                if (visionDecision.useOnDevice) {
                    // On-device vision via Nexa SDK
                    val onDevice = smartRouter.getOnDeviceManager()
                    result = onDevice.analyzeImage(base64Image, question)
                        ?: visionDecision.fallbackMessage
                        ?: "No se pudo analizar la imagen offline."
                } else {
                    // Cloud vision via /api/vision (GLM-4.6V)
                    result = repository.sendVisionRequest(
                        baseUrl = BuildConfig.API_BASE_URL,
                        base64Image = base64Image,
                        mimeType = mimeType,
                        question = question
                    ) ?: visionDecision.fallbackMessage ?: if (lang == AppLanguage.SPANISH) "No se recibió una descripción clara del servidor." else "No description received from server."
                }

                if (result.isNotBlank()) {
                    val assistantMsg = Message(id = assistantId, role = "assistant", content = result)
                    updateActiveSession { s ->
                        s.copy(messages = s.messages + assistantMsg, updatedAt = System.currentTimeMillis())
                    }

                    if (_uiState.value.voiceMode && _uiState.value.autoSpeak) {
                        speak(result, assistantId)
                    }
                } else {
                    _uiState.update { it.copy(error = if (lang == AppLanguage.SPANISH) "No se pudo analizar la imagen" else "Could not analyze image") }
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Vision error: ${e.message}")
                _uiState.update {
                    it.copy(
                        error = if (lang == AppLanguage.SPANISH) "Error al analizar imagen: ${e.message}" else "Vision error: ${e.message}",
                        isThinking = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    // ═══════════════════════════════════════
    //  PREVIEW
    // ═══════════════════════════════════════

    fun showPreview(content: String) {
        _uiState.update { it.copy(previewContent = content, showPreview = true) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(showPreview = false, previewContent = null) }
    }

    fun clearChat() {
        chatUseCase.clearChat()
        speechManager.stopSpeaking()
        updateActiveSession { it.copy(messages = emptyList()) }
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ═══════════════════════════════════════
    //  DRAWER
    // ═══════════════════════════════════════

    fun toggleDrawer() {
        _uiState.value = _uiState.value.copy(drawerOpen = !_uiState.value.drawerOpen)
    }

    fun closeDrawer() {
        _uiState.value = _uiState.value.copy(drawerOpen = false)
    }

    fun setDrawerView(view: Int) {
        _uiState.value = _uiState.value.copy(drawerView = view)
    }

    // ═══════════════════════════════════════
    //  SETTINGS
    // ═══════════════════════════════════════

    fun toggleSettings() {
        val current = _uiState.value.currentScreen
        if (current == Screen.SETTINGS) {
            _uiState.value = _uiState.value.copy(currentScreen = Screen.CHAT, drawerOpen = false)
        } else {
            _uiState.value = _uiState.value.copy(currentScreen = Screen.SETTINGS, drawerOpen = false)
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _uiState.value = _uiState.value.copy(language = lang)
        speechManager.setLanguage(lang)
        viewModelScope.launch { settingsStore.setLanguage(lang) }
    }

    fun setVoiceType(type: VoiceType) {
        _uiState.value = _uiState.value.copy(voiceType = type)
        speechManager.setVoiceType(type)
        viewModelScope.launch { settingsStore.setVoiceType(type) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setAccentColor(color: androidx.compose.ui.graphics.Color) {
        _uiState.update { it.copy(accentColor = color.value.toLong()) }
        viewModelScope.launch { settingsStore.setAccentColor(color.value.toLong()) }
    }

    fun setGroqApiKey(key: String) {
        _uiState.update { it.copy(groqApiKey = key.trim()) }
        viewModelScope.launch { settingsStore.setGroqApiKey(key.trim()) }
    }

    fun toggleLocalLLM(enabled: Boolean) {
        _uiState.update { it.copy(useLocalLLM = enabled) }
        viewModelScope.launch { settingsStore.setUseLocalLLM(enabled) }
    }

    fun toggleSync(enabled: Boolean) {
        _uiState.update { it.copy(allowSync = enabled) }
        viewModelScope.launch { settingsStore.setAllowSync(enabled) }
    }

    fun setMaxTokens(tokens: Int) {
        _uiState.update { it.copy(maxTokens = tokens) }
        viewModelScope.launch { settingsStore.setMaxTokens(tokens) }
    }

    fun downloadModel() {
        _uiState.update { it.copy(isDownloadingModel = true, modelDownloadProgress = 0f) }
        viewModelScope.launch {
            // Simulated animated model download in background
            for (i in 1..10) {
                kotlinx.coroutines.delay(200)
                _uiState.update { it.copy(modelDownloadProgress = i * 0.1f) }
            }
            // Actually call loading now that weights are present in assets
            val loadSuccess = localLLMManager.loadModelFromAssets()
            _uiState.update { 
                it.copy(
                    isDownloadingModel = false, 
                    modelDownloadProgress = 1.0f,
                    error = if (!loadSuccess) "No se pudo cargar el modelo offline GGUF" else null
                )
            }
        }
    }

    fun deleteGroqApiKey() {
        _uiState.update { it.copy(groqApiKey = "") }
        viewModelScope.launch { settingsStore.deleteGroqApiKey() }
    }

    fun previewVoice() {
        val lang = _uiState.value.language
        val text = if (lang == AppLanguage.SPANISH) "Hola, esta es una vista previa de mi voz." else "Hello, this is a preview of my voice."
        speak(text)
    }

    fun exportSettings() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val settings = mapOf(
                    "theme" to _uiState.value.themeMode.name,
                    "language" to _uiState.value.language.name,
                    "voice" to _uiState.value.voiceType.name,
                    "autoSpeak" to _uiState.value.autoSpeak.toString(),
                    "volumeBoost" to _uiState.value.volumeBoostEnabled.toString(),
                    "speechRate" to _uiState.value.speechRate.toString(),
                    "notifications" to _uiState.value.notificationsEnabled.toString(),
                    "accentColor" to _uiState.value.accentColor.toString()
                )
                val json = com.google.gson.Gson().toJson(settings)
                val file = java.io.File(context.getExternalFilesDir(null), "nexa_settings_backup.json")
                file.writeText(json)
                shareText("NEXA Settings Backup:\n$json")
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Export settings error", e)
            }
        }
    }

    fun importSettings() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "nexa_settings_backup.json")
                if (file.exists()) {
                    val json = file.readText()
                    val settings = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, String>
                    settings["theme"]?.let { try { setThemeMode(ThemeMode.valueOf(it)) } catch (_: Exception) {} }
                    settings["language"]?.let { try { setLanguage(AppLanguage.valueOf(it)) } catch (_: Exception) {} }
                    settings["voice"]?.let { try { setVoiceType(VoiceType.valueOf(it)) } catch (_: Exception) {} }
                    settings["autoSpeak"]?.let { if (it == "false") { if (_uiState.value.autoSpeak) toggleAutoSpeak() } else { if (!_uiState.value.autoSpeak) toggleAutoSpeak() } }
                    settings["volumeBoost"]?.let { if (it != _uiState.value.volumeBoostEnabled.toString()) toggleVolumeBoost() }
                    settings["speechRate"]?.let { try { setSpeechRate(it.toFloat()) } catch (_: Exception) {} }
                    settings["accentColor"]?.let { try { _uiState.update { s -> s.copy(accentColor = it.toLong()) } } catch (_: Exception) {} }
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Import settings error", e)
            }
        }
    }

    fun cycleTheme() {
        val next = when (_uiState.value.themeMode) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        _uiState.value = _uiState.value.copy(themeMode = next)
        viewModelScope.launch { settingsStore.setThemeMode(next) }
    }

    fun copyToClipboard(text: String) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("NEXA PRO", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, NexaStrings.get("copied", _uiState.value.language), android.widget.Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
        val context = getApplication<Application>()
        val intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = android.content.Intent.createChooser(intent, NexaStrings.get("share", _uiState.value.language)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun exportToPdf(message: Message) {
        val context = getApplication<Application>()

        try {
            val content = message.content.trim()
            if (content.isEmpty()) {
                android.widget.Toast.makeText(context, NexaStrings.get("nothing_to_export", _uiState.value.language), android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val pdfDocument = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint().apply { isAntiAlias = true }
            val pageWidth = 595
            val pageHeight = 842
            val marginLeft = 50f
            val maxTextWidth = 495f
            val maxY = 790f
            val lineHeight = 18f
            val paragraphGap = 4f

            var pageNum = 0
            var page: android.graphics.pdf.PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y: Float

            fun newPage(startY: Float = 50f): Float {
                if (pageNum > 0) pdfDocument.finishPage(page!!)
                pageNum++
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = pdfDocument.startPage(info)
                canvas = page!!.canvas
                paint.textSize = 12f
                paint.isFakeBoldText = false
                paint.color = android.graphics.Color.BLACK
                return startY
            }

            fun ensureSpace(currentY: Float, needed: Float = lineHeight): Float {
                return if (currentY + needed > maxY) newPage() else currentY
            }

            // First page — header
            y = newPage(95f)

            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.parseColor("#00E5A0")
            canvas!!.drawText("NEXA PRO", marginLeft, 45f, paint)

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.GRAY
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            canvas!!.drawText(dateStr, marginLeft, 62f, paint)

            paint.color = android.graphics.Color.parseColor("#00E5A0")
            paint.strokeWidth = 1f
            canvas!!.drawLine(marginLeft, 72f, 545f, 72f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.BLACK

            // Content
            for (line in content.split("\n")) {
                y = ensureSpace(y)
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > maxTextWidth) {
                        y = ensureSpace(y)
                        canvas!!.drawText(currentLine, marginLeft, y, paint)
                        y += lineHeight
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    y = ensureSpace(y)
                    canvas!!.drawText(currentLine, marginLeft, y, paint)
                    y += lineHeight
                }
                y += paragraphGap
            }

            // Footer
            paint.textSize = 8f
            paint.color = android.graphics.Color.LTGRAY
            canvas!!.drawText(NexaStrings.get("generated_by", _uiState.value.language), marginLeft, 820f, paint)

            pdfDocument.finishPage(page!!)

            val fileName = "nexa_export_${System.currentTimeMillis()}.pdf"
            val file = java.io.File(context.cacheDir, fileName)
            java.io.FileOutputStream(file).use { fos -> pdfDocument.writeTo(fos) }
            pdfDocument.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val shareIntent = android.content.Intent.createChooser(intent, NexaStrings.get("export_pdf_title", _uiState.value.language))
            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)

        } catch (e: Exception) {
            android.util.Log.e("NEXA", "PDF Error: ${e.message}", e)
            val lang = _uiState.value.language
            val errorMsg = if (lang == AppLanguage.SPANISH) "Error al generar PDF: ${e.localizedMessage}" else "Error generating PDF: ${e.localizedMessage}"
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(speechReceiver)
        } catch (_: Exception) {}
        stopSpeechService()
        speechManager.destroy()
        voiceEnhancer.shutdown()
        sensorManager.stopListening()
        webSearchManager.clearCache()
    }

    // ═══════════════════════════════════════
    //  NEW: WEB SEARCH INTEGRATION
    // ═══════════════════════════════════════

    /**
     * Perform a web search and return processed results.
     * Called automatically when the AI needs real-time information.
     */
    suspend fun performWebSearch(query: String): com.nexa.ai.web.ProcessedResult {
        return webResultProcessor.searchAndProcess(
            query = query,
            language = _uiState.value.language.code
        )
    }

    /**
     * Search the web for current news on a topic.
     */
    suspend fun searchNews(query: String): List<com.nexa.ai.web.NewsResult> {
        return webSearchManager.searchNews(query)
    }

    /**
     * Fact-check a claim using web search.
     */
    suspend fun factCheck(claim: String): Triple<Float, String, List<com.nexa.ai.web.SearchResult>> {
        return webSearchManager.factCheck(claim)
    }

    // ═══════════════════════════════════════
    //  NEW: MEMORY & PROFILE HELPERS
    // ═══════════════════════════════════════

    /** Enable episodic memory (requires user consent). */
    fun enableMemory() {
        episodicMemoryManager.setConsent(true)
    }

    /** Disable episodic memory. */
    fun disableMemory() {
        episodicMemoryManager.setConsent(false)
    }

    /** Check if memory is enabled. */
    fun isMemoryEnabled(): Boolean = episodicMemoryManager.hasConsent()

    /** Get memory statistics. */
    fun getMemoryStats(): com.nexa.ai.memory.MemoryStats = episodicMemoryManager.getStats()

    /** Clear all episodic memories. */
    fun clearAllMemories() {
        episodicMemoryManager.clearAllMemories()
    }

    /** Extract a simple topic from a user message for profiling. */
    private fun extractTopic(message: String): String {
        val keywords = message.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 4 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        return keywords.joinToString(" ")
    }
}
