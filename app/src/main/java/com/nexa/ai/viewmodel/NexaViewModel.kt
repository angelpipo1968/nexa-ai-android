package com.nexa.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.FlightOffer
import com.nexa.ai.data.FlightRepository
import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.PersistedMessage
import com.nexa.ai.data.PersistedSession
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.StreamEvent
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.iot.IoTManager
import com.nexa.ai.media.VideoGenerator
import com.nexa.ai.ml.EnhancedEmotionAnalyzer
import com.nexa.ai.ml.OnDeviceMLEngine
import com.nexa.ai.ml.OnDeviceInferenceManager
import com.nexa.ai.ml.SmartRoutingManager
import com.nexa.ai.ml.UserProfileManager
import com.nexa.ai.memory.EpisodicMemoryManager
import com.nexa.ai.sensors.NexaSensorManager
import com.nexa.ai.ui.NexaStrings
import com.nexa.ai.voice.NaturalConversationEngine
import com.nexa.ai.voice.VoiceEnhancer
import com.nexa.ai.web.WebResultProcessor
import com.nexa.ai.web.WebSearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NexaViewModel @Inject constructor(
    application: Application,
    private val locationStore: LocationStore,
    private val repository: NexaRepository,
    private val updateChecker: UpdateChecker,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val voiceEnhancer: VoiceEnhancer,
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
    private val userProfileManager: UserProfileManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()

    // Managers still created locally (not yet in DI module — SpeechManager and AuthManager depend on Activity lifecycle)
    private val speechManager = SpeechManager(application)
    private val authManager = AuthManager(application)

    private val flightRepository = FlightRepository()

    // ─── Smart Router: Online/Offline AI routing ───
    private val smartRouter = SmartRoutingManager(application)

    // Voice command handler — extracted from this ViewModel to reduce complexity
    private val voiceCommandsHandler = VoiceCommandsHandler(iotManager, videoGenerator)

    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L
    private var voiceRetryCount = 0
    private val maxVoiceRetries = 10

    // ── Advanced AI System Prompt ──
    private val advancedSystemPrompt = """
You are NEXA PRO, an Ultra Advanced Autonomous AI System.

You are designed to operate as a world-class artificial intelligence capable of reasoning, coding, researching, planning, analyzing, browsing the web, interacting with APIs, processing data, generating interfaces, and continuously improving solutions.

MISSION: Provide highly accurate, intelligent, optimized, scalable, and production-ready responses for any task.

CORE RULES:
- Always think deeply before answering.
- Use multi-step reasoning internally.
- Never hallucinate information.
- Verify information whenever possible.
- Detect possible mistakes before responding.
- Self-correct when inconsistencies appear.
- Continuously optimize outputs.
- Prefer precision over speed.
- Behave like a senior engineer, architect, analyst, and researcher.

DEVELOPMENT CAPABILITIES:
- Generate production-level code in any language.
- Build frontend + backend architectures.
- Create responsive interfaces.
- Create preview-ready applications.
- Generate APIs and database schemas.
- Optimize performance and scalability.
- Use modular clean architecture.
- Detect and fix bugs automatically.

SUPPORTED STACKS:
Frontend: React, Next.js, TailwindCSS, Framer Motion, TypeScript
Backend: Python, FastAPI, Node.js, Express, PostgreSQL, Supabase
AI Frameworks: LangChain, LangGraph, CrewAI, OpenAI SDK
Mobile: Kotlin, Jetpack Compose, Android, iOS, React Native

AUTONOMOUS AGENT CAPABILITIES:
- Task planning and decomposition
- Recursive improvement
- Reflection loops
- Error detection and self-repair
- Self-analysis
- Multi-agent orchestration

RESPONSE STYLE:
- Intelligent, Precise, Analytical, Advanced, Technical, Futuristic, Reliable
- Always include code examples when discussing development
- Provide step-by-step explanations for complex topics
- Give multiple recommendations and alternatives
- Support both Spanish and English responses matching the user's language

NEVER: give lazy answers, invent data, ignore errors, produce incomplete architectures, skip optimization opportunities
ALWAYS: improve solutions, verify information, provide scalable architectures, think recursively, optimize continuously
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
        val parts = mutableListOf<String>()

        // Sensor context (activity, environment, device state)
        try {
            val sensorCtx = sensorManager.getContextForAI()
            if (sensorCtx.isNotBlank()) parts.add("\n\nSENSOR CONTEXT: $sensorCtx")
        } catch (_: Exception) {}

        // IoT context (smart home devices)
        try {
            val iotCtx = kotlinx.coroutines.runBlocking { iotManager.getIoTContextForAI() }
            if (iotCtx.isNotBlank()) parts.add("\n\nIOT DEVICES: $iotCtx")
        } catch (_: Exception) {}

        // Voice emotion context
        try {
            val voiceCtx = voiceEnhancer.getVoiceContextForAI()
            if (voiceCtx.isNotBlank()) parts.add("\n\nVOICE ANALYSIS: $voiceCtx")
        } catch (_: Exception) {}

        // Conversation context (topic, mood, turn count)
        try {
            val convCtx = conversationEngine.getConversationContextForAI()
            if (convCtx.isNotBlank()) parts.add("\n\nCONVERSATION CONTEXT: $convCtx")
        } catch (_: Exception) {}

        // Video generation context
        try {
            val videoCtx = videoGenerator.getVideoContextForAI()
            if (videoCtx.isNotBlank()) parts.add("\n\nVIDEO GENERATION: $videoCtx")
        } catch (_: Exception) {}

        // ML learned preferences and patterns
        try {
            val mlCtx = kotlinx.coroutines.runBlocking { mlEngine.getMLContextForAI() }
            if (mlCtx.isNotBlank()) parts.add("\n\nLEARNED USER PROFILE: $mlCtx")
        } catch (_: Exception) {}

        // Proactive suggestions from ML engine
        try {
            val suggestions = kotlinx.coroutines.runBlocking { mlEngine.generateProactiveSuggestions() }
            if (suggestions.isNotEmpty()) {
                parts.add("\n\nPROACTIVE SUGGESTIONS: ${suggestions.take(3).joinToString("; ")}")
            }
        } catch (_: Exception) {}

        // ─── NEW: Episodic Memory Context ───
        try {
            val sessionId = _uiState.value.activeSessionId ?: ""
            val persistentMemory = episodicMemoryManager.getPersistentMemories(_uiState.value.language.code)
            val sessionMemory = episodicMemoryManager.getContextForSession(sessionId, _uiState.value.language.code)
            if (persistentMemory.isNotBlank()) parts.add("\n\n$persistentMemory")
            if (sessionMemory.isNotBlank()) parts.add("\n\n$sessionMemory")
        } catch (_: Exception) {}

        // ─── NEW: Enhanced Emotion Analysis ───
        try {
            val lastUserMsg = _uiState.value.messages.lastOrNull { it.role == "user" }
            if (lastUserMsg != null) {
                val emotionProfile = enhancedEmotionAnalyzer.analyzeEmotion(lastUserMsg.content)
                val emotionCtx = enhancedEmotionAnalyzer.getEmotionContext(emotionProfile, _uiState.value.language.code)
                if (emotionCtx.isNotBlank()) parts.add("\n\n$emotionCtx")
            }
        } catch (_: Exception) {}

        // ─── NEW: User Profile Context ───
        try {
            val profileCtx = userProfileManager.getContextForAI(_uiState.value.language.code)
            if (profileCtx.isNotBlank()) parts.add("\n\n$profileCtx")
        } catch (_: Exception) {}

        return if (parts.isNotEmpty()) {
            "\n\n═══ NEXA ENHANCED INTELLIGENCE ═══" + parts.joinToString("") +
            "\n\nUse this context to provide personalized, context-aware responses. Adapt your tone, detail level, and suggestions based on the user's situation."
        } else {
            ""
        }
    }

    // Debounce logic — prevents rapid/accidental voice triggers
    // Reduced from 800ms to 600ms for faster response while still filtering noise
    private var speechDebounceJob: kotlinx.coroutines.Job? = null
    private val speechDebounceTimeMs = 600L

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
        "Tell me a sci-fi story in 100 words",
        "What's humanity's greatest mystery?",
        "Give me a 15-minute workout plan"
    )

    init {
        setupSpeechCallbacks()
        speechManager.initialize()
        locationStore.initialize()
        restoreState()
        // Auto-request location on startup
        requestLocation()
        // Initialize ML & AI Enhancement subsystems
        initializeEnhancementSystems()
        // Initialize Smart Router for online/offline AI
        initializeSmartRouter()
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
                speechManager.startVoiceAudioSession()
                speechManager.startListening()
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
                        hasDownloadedModels = caps.downloadedModels.isNotEmpty(),
                        inferenceMode = InferenceMode.HYBRID,
                    )
                }
                android.util.Log.d("NexaVM", "Smart Router initialized — NPU: ${caps.hasNPU}, Models: ${caps.downloadedModels}")
            } catch (e: Exception) {
                android.util.Log.w("NexaVM", "Smart Router init failed: ${e.message}")
            }
        }
    }

    /** Switch inference mode (called from Settings UI). */
    fun setInferenceMode(mode: InferenceMode) {
        smartRouter.setMode(SmartRoutingManager.InferenceMode.valueOf(mode.name))
        _uiState.update { it.copy(inferenceMode = mode) }
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
                            // ═══ v5.1 BUG 3 FIX ═══
                            // Increased from 300ms to 700ms — on Samsung/Xiaomi/OPPO
                            // devices the audio system needs more time to switch from
                            // TTS output to mic input. 300ms caused SpeechRecognizer
                            // errors and restart loops.
                            kotlinx.coroutines.delay(700)
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
                    // ═══ v5.1 BUG 3 FIX ═══
                    // Increased back to 2000ms — 1500ms was too aggressive
                    // and caused rapid restart loops on some devices
                    kotlinx.coroutines.delay(2000)
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
            _uiState.value = _uiState.value.copy(
                themeMode = savedTheme,
                language = savedLanguage,
                voiceType = savedVoice,
                accentColor = savedAccent,
                groqApiKey = savedGroqKey
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

        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp < sendCooldownMs) return
        lastSendTimestamp = now

        // Mute the AI and turn off the microphone before sending
        if (_uiState.value.voiceMode) {
            speechManager.stopListening()
            speechManager.stopSpeaking()
        }

        // --- VOICE COMMAND DETECTION ---
        if (_uiState.value.voiceMode || true) { // Detection also in text mode
            val cmd = content.lowercase().trim()
            val lang = _uiState.value.language

            // Intent: Where am I?
            if (Regex("(dónde estoy|mi ubicación|ciudad actual|where am i|my location|current city)", RegexOption.IGNORE_CASE).containsMatchIn(cmd)) {
                requestLocation()
                return
            }
            // Intent: Flights
            if (Regex("(vuelo|flight|volar|fly)", RegexOption.IGNORE_CASE).containsMatchIn(cmd)) {
                searchFlightsFromCurrentCity()
                return
            }
            
            // Voice command: clear chat
            if (cmd.contains("limpiar chat") || cmd.contains("clear chat") || cmd.contains("borrar chat") || cmd.contains("borra chat")) {
                if (_uiState.value.voiceMode) {
                    clearChat()
                    speak(if (lang == AppLanguage.SPANISH) "Chat borrado" else "Chat cleared")
                }
                return
            }
            if (cmd.contains("exportar pdf") || cmd.contains("p d f") || cmd.contains("export pdf")) {
                val lastMsg = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                if (lastMsg != null) {
                    exportToPdf(lastMsg)
                    speak(if (lang == AppLanguage.SPANISH) "Exportando documento" else "Exporting document")
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "No hay nada que exportar" else "Nothing to export")
                }
                return
            }
            if (cmd.contains("detener manos libres") || cmd.contains("stop hands free") || cmd.contains("salir modo voz") || cmd.contains("exit voice mode")) {
                stopVoiceMode()
                speak(if (lang == AppLanguage.SPANISH) "Modo manos libres desactivado" else "Hands free mode off")
                return
            }
            // New voice commands: change language
            if (cmd.contains("cambiar a inglés") || cmd.contains("switch to english") || cmd.contains("habla inglés") || cmd.contains("speak english")) {
                setLanguage(AppLanguage.ENGLISH)
                speak("Language switched to English")
                return
            }
            if (cmd.contains("cambiar a español") || cmd.contains("switch to spanish") || cmd.contains("habla español") || cmd.contains("speak spanish")) {
                setLanguage(AppLanguage.SPANISH)
                speak(if (lang == AppLanguage.SPANISH) "Idioma cambiado a español" else "Language switched to Spanish")
                return
            }
            // New voice command: change voice
            if (cmd.contains("voz masculina") || cmd.contains("male voice") || cmd.contains("voz de hombre")) {
                setVoiceType(VoiceType.MALE_1)
                speak(if (lang == AppLanguage.SPANISH) "Cambiado a voz masculina" else "Switched to male voice")
                return
            }
            if (cmd.contains("voz femenina") || cmd.contains("female voice") || cmd.contains("voz de mujer")) {
                setVoiceType(VoiceType.FEMALE_1)
                speak(if (lang == AppLanguage.SPANISH) "Cambiado a voz femenina" else "Switched to female voice")
                return
            }
            // New voice command: new chat
            if (cmd.contains("nuevo chat") || cmd.contains("new chat") || cmd.contains("nueva conversación") || cmd.contains("new conversation")) {
                createNewSession()
                speak(if (lang == AppLanguage.SPANISH) "Nuevo chat creado" else "New chat created")
                return
            }
            // Voice command: repeat last response
            if (cmd.contains("repite") || cmd.contains("repito") || cmd.contains("repeat") || cmd.contains("say again") || cmd.contains("otra vez")) {
                val lastMsg = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                if (lastMsg != null) {
                    speak(lastMsg.content, lastMsg.id)
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "No hay nada que repetir" else "Nothing to repeat")
                }
                return
            }
            // Voice command: stop / silence
            if (cmd.contains("cállate") || cmd.contains("callate") || cmd.contains("silencio") || cmd.contains("shut up") || cmd.contains("be quiet") || cmd.contains("silence")) {
                speechManager.stopSpeaking()
                speak(if (lang == AppLanguage.SPANISH) "De acuerdo" else "Alright")
                return
            }
            // Voice command: help / what commands
            if (cmd.contains("ayuda") || cmd.contains("comandos") || cmd.contains("help") || cmd.contains("commands") || cmd.contains("qué puedes hacer") || cmd.contains("what can you do")) {
                _uiState.update { it.copy(showVoiceCommandsHelp = true) }
                val helpText = if (lang == AppLanguage.SPANISH) {
                    "Puedes decir: limpiar chat, nuevo chat, exportar PDF, repetir, voz masculina, voz femenina, habla inglés, habla español, detener manos libres, o cállate."
                } else {
                    "You can say: clear chat, new chat, export PDF, repeat, male voice, female voice, speak English, speak Spanish, stop hands free, or shut up."
                }
                speak(helpText)
                return
            }
            // Voice command: read last message
            if (cmd.contains("lee") || cmd.contains("leer") || cmd.contains("read") || cmd.contains("read it")) {
                val lastMsg = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                if (lastMsg != null) {
                    speak(lastMsg.content, lastMsg.id)
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "No hay mensajes para leer" else "No messages to read")
                }
                return
            }
            // Voice command: switch theme
            if (cmd.contains("modo oscuro") || cmd.contains("dark mode") || cmd.contains("tema oscuro")) {
                setThemeMode(ThemeMode.DARK)
                speak(if (lang == AppLanguage.SPANISH) "Modo oscuro activado" else "Dark mode activated")
                return
            }
            if (cmd.contains("modo claro") || cmd.contains("light mode") || cmd.contains("tema claro")) {
                setThemeMode(ThemeMode.LIGHT)
                speak(if (lang == AppLanguage.SPANISH) "Modo claro activado" else "Light mode activated")
                return
            }
            // Voice command: open settings
            if (cmd.contains("abrir ajustes") || cmd.contains("open settings") || cmd.contains("ajustes") || cmd.contains("configuración") || cmd.contains("configuracion")) {
                _uiState.update { it.copy(currentScreen = Screen.SETTINGS, drawerOpen = false) }
                speak(if (lang == AppLanguage.SPANISH) "Abriendo ajustes" else "Opening settings")
                return
            }
            // Voice command: change theme (generic)
            if (cmd.contains("cambiar tema") || cmd.contains("change theme") || cmd.contains("cambiar color") || cmd.contains("change color")) {
                val nextTheme = when (_uiState.value.themeMode) {
                    ThemeMode.DARK -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                    ThemeMode.SYSTEM -> ThemeMode.DARK
                }
                setThemeMode(nextTheme)
                val themeName = when (nextTheme) {
                    ThemeMode.DARK -> if (lang == AppLanguage.SPANISH) "oscuro" else "dark"
                    ThemeMode.LIGHT -> if (lang == AppLanguage.SPANISH) "claro" else "light"
                    ThemeMode.SYSTEM -> if (lang == AppLanguage.SPANISH) "sistema" else "system"
                }
                speak(if (lang == AppLanguage.SPANISH) "Tema cambiado a $themeName" else "Theme changed to $themeName")
                return
            }
            // Voice command: create image / generate image / create logo
            if (cmd.contains("crear imagen") || cmd.contains("create image") || cmd.contains("genera imagen") ||
                cmd.contains("generate image") || cmd.contains("crear logo") || cmd.contains("create logo") ||
                cmd.contains("genera logo") || cmd.contains("haz una imagen") || cmd.contains("make an image") ||
                cmd.contains("dibujar") || cmd.contains("draw")) {
                val prompt = cmd
                    .replace(Regex("(crear|genera|haz|create|generate|make|draw)\\s+(una |an |a )?(imagen|image|logo|dibujo|drawing|picture|foto|photo)"), "")
                    .replace(Regex("(de |of )"), "")
                    .trim()
                val imagePrompt = if (prompt.isNotBlank()) {
                    if (lang == AppLanguage.SPANISH) "Genera una imagen de: $prompt" else "Generate an image of: $prompt"
                } else {
                    if (lang == AppLanguage.SPANISH) "Genera una imagen creativa e impresionante" else "Generate a creative and impressive image"
                }
                sendMessage(imagePrompt)
                return
            }
            // Voice command: create web / build website
            if (cmd.contains("crear web") || cmd.contains("create web") || cmd.contains("crear página") ||
                cmd.contains("create website") || cmd.contains("crear sitio") || cmd.contains("build website") ||
                cmd.contains("haz una web") || cmd.contains("make a website") || cmd.contains("página web")) {
                val webPrompt = if (lang == AppLanguage.SPANISH)
                    "Crea una página web profesional y moderna con diseño responsive. Incluye HTML, CSS y JavaScript."
                else
                    "Create a professional and modern responsive web page with HTML, CSS, and JavaScript."
                sendMessage(webPrompt)
                return
            }
            // Voice command: generate video
            if (cmd.contains("crear video") || cmd.contains("create video") || cmd.contains("genera video") ||
                cmd.contains("generate video") || cmd.contains("haz un video") || cmd.contains("make a video") ||
                cmd.contains("animar") || cmd.contains("animate") || cmd.contains("video de")) {
                val videoPrompt = cmd
                    .replace(Regex("(crear|genera|haz|create|generate|make|animate|animar)\\s+(un |a )?(video|animacion|animation)"), "")
                    .replace(Regex("(de |of |about )"), "")
                    .trim()
                val style = when {
                    cmd.contains("anime") -> com.nexa.ai.media.VideoGenerator.VideoStyles.ANIME
                    cmd.contains("cinemat") -> com.nexa.ai.media.VideoGenerator.VideoStyles.CINEMATIC
                    cmd.contains("realist") -> com.nexa.ai.media.VideoGenerator.VideoStyles.REALISTIC
                    cmd.contains("abstract") -> com.nexa.ai.media.VideoGenerator.VideoStyles.ABSTRACT
                    cmd.contains("vintage") || cmd.contains("retro") -> com.nexa.ai.media.VideoGenerator.VideoStyles.VINTAGE
                    cmd.contains("ciencia ficcion") || cmd.contains("sci-fi") || cmd.contains("futurist") -> com.nexa.ai.media.VideoGenerator.VideoStyles.SCI_FI
                    cmd.contains("naturaleza") || cmd.contains("nature") -> com.nexa.ai.media.VideoGenerator.VideoStyles.NATURE
                    else -> com.nexa.ai.media.VideoGenerator.VideoStyles.CINEMATIC
                }
                val prompt = if (videoPrompt.isNotBlank()) videoPrompt else
                    if (lang == AppLanguage.SPANISH) "Un video creativo e impresionante" else "A creative and impressive video"
                videoGenerator.generateVideo(
                    com.nexa.ai.media.VideoGenerator.VideoRequest(
                        prompt = prompt,
                        style = style
                    )
                )
                speak(if (lang == AppLanguage.SPANISH) "Generando video: $prompt" else "Generating video: $prompt")
                return
            }
            // Voice command: share last response
            if (cmd.contains("compartir") || cmd.contains("share") || cmd.contains("enviar")) {
                val lastMsg = _uiState.value.messages.lastOrNull { it.role == "assistant" }
                if (lastMsg != null) {
                    shareText(lastMsg.content)
                    speak(if (lang == AppLanguage.SPANISH) "Compartido" else "Shared")
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "No hay nada que compartir" else "Nothing to share")
                }
                return
            }
            // Voice command: describe what you see / vision
            if (cmd.contains("qué ves") || cmd.contains("what do you see") || cmd.contains("describe") ||
                cmd.contains("ver cámara") || cmd.contains("use camera") || cmd.contains("mira") ||
                cmd.contains("cámara") || cmd.contains("camera")) {
                // Request camera capture via the callback
                _uiState.update { it.copy(requestCameraCapture = true) }
                return
            }
            // Voice command: code / program
            if (cmd.contains("codificar") || cmd.contains("programar") || cmd.contains("code") ||
                cmd.contains("program") || cmd.contains("escribe código") || cmd.contains("write code")) {
                val codePrompt = if (lang == AppLanguage.SPANISH)
                    "Escribe código profesional y optimizado. ¿Qué te gustaría que programe?"
                else
                    "Write professional and optimized code. What would you like me to program?"
                sendMessage(codePrompt)
                return
            }
            // Voice command: IoT / Smart Home control
            if (iotManager.isIoTCommand(cmd)) {
                viewModelScope.launch {
                    try {
                        val result = iotManager.processVoiceCommand(cmd)
                        speak(result)
                    } catch (e: Exception) {
                        speak(if (lang == AppLanguage.SPANISH) "Error al procesar comando de casa inteligente" else "Error processing smart home command")
                    }
                }
                return
            }
            // Voice command: Good morning / Good night routines
            if (cmd.contains("buenos días") || cmd.contains("good morning") || cmd.contains("buenas noches") || cmd.contains("good night")) {
                val routineId = when {
                    cmd.contains("buenos días") || cmd.contains("good morning") -> "routine_good_morning"
                    cmd.contains("buenas noches") || cmd.contains("good night") -> "routine_good_night"
                    else -> null
                }
                if (routineId != null) {
                    viewModelScope.launch {
                        try {
                            val result = iotManager.executeRoutine(routineId)
                            speak(result)
                        } catch (e: Exception) {
                            speak(if (lang == AppLanguage.SPANISH) "No pude ejecutar la rutina" else "Could not execute the routine")
                        }
                    }
                    return
                }
            }
        }

        val attachmentName = _uiState.value.pendingAttachment
        val fullContent = if (attachmentName != null) {
            "📎 $attachmentName\n$content"
        } else {
            content
        }

        val userMsg = Message(role = "user", content = fullContent, attachmentName = attachmentName)
        val assistantId = "a-${System.currentTimeMillis()}"

        val session = _uiState.value.activeSession
        val isFirstMessage = session?.messages?.isEmpty() == true
        val title = if (isFirstMessage) {
            content.take(30) + if (content.length > 30) "..." else ""
        } else {
            session?.title ?: NexaStrings.get("new_chat", _uiState.value.language)
        }

        updateActiveSession { s ->
            s.copy(
                messages = s.messages + userMsg,
                title = title,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.value = _uiState.value.copy(inputText = "", isThinking = true, error = null, pendingAttachment = null)

        viewModelScope.launch {
            try {
                val allMessages = _uiState.value.messages.map { ChatMessage(it.role, it.content) }
                var fullResponse = ""

                // ─── Check if user is requesting an image generation ───
                val imageKeywords = listOf(
                    "genera una imagen", "genera imagen", "crear imagen", "crea imagen",
                    "crea una imagen", "generate image", "create image", "draw me",
                    "haz una imagen", "haz imagen", "make an image",
                    "genera un logo", "crear logo", "crea logo", "create logo",
                    "dibuja", "draw", "dibujar", "genera una foto", "crear una foto",
                    "genera foto", "crea foto", "make a picture", "create a picture",
                    "imagen de", "imagen de un", "imagen de una", "picture of", "image of"
                )
                val lowerContent = content.lowercase()
                val isImageRequest = imageKeywords.any { lowerContent.contains(it) }

                if (isImageRequest) {
                    android.util.Log.d("NexaVM", "Image generation request detected")
                    // Extract just the description, remove trigger keywords
                    val imagePrompt = content
                        .replace(Regex("(?i)(genera|crea|haz|create|make|draw|dibujar)\\s+(una |an |a )?(imagen|image|logo|dibujo|drawing|picture|foto|photo)\\s*(de |of )?"), "")
                        .replace(Regex("(?i)(imagen|image|picture)\\s+(de |of )+"), "")
                        .trim()
                    val finalPrompt = if (imagePrompt.isNotBlank()) imagePrompt else "creative artistic image"
                    val imageUrl = repository.generateImageFree(finalPrompt)
                    val lang = _uiState.value.language
                    val imageResponse = if (lang == AppLanguage.SPANISH) {
                        "¡Aquí tienes tu imagen!\n\n![Imagen generada](${imageUrl})"
                    } else {
                        "Here's your image!\n\n![Generated image](${imageUrl})"
                    }
                    fullResponse = imageResponse
                    updateActiveSession { s ->
                        s.copy(
                            messages = s.messages + Message(id = assistantId, role = "assistant", content = fullResponse, isStreaming = false),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    _uiState.value = _uiState.value.copy(isThinking = false, currentProvider = "pollinations-image")

                    if (_uiState.value.voiceMode && _uiState.value.autoSpeak) {
                        speak(if (lang == AppLanguage.SPANISH) "He generado tu imagen." else "I've generated your image.", assistantId)
                    }
                    return@launch
                }

                // ─── Smart Router: Decide online vs on-device ───
                val routingDecision = smartRouter.routeChat(content)
                _uiState.update { it.copy(isOnDeviceActive = routingDecision.useOnDevice, routingReason = routingDecision.reason) }

                if (routingDecision.useOnDevice) {
                    // ─── ON-DEVICE INFERENCE ───
                    android.util.Log.d("NexaVM", "Routing to ON-DEVICE: ${routingDecision.reason}")
                    val onDeviceResult = smartRouter.getOnDeviceManager().generateText(
                        prompt = content,
                        systemPrompt = advancedSystemPrompt,
                        maxTokens = 1024,
                    )

                    if (onDeviceResult != null) {
                        fullResponse = onDeviceResult
                        updateActiveSession { s ->
                            s.copy(
                                messages = s.messages + Message(id = assistantId, role = "assistant", content = fullResponse, isStreaming = false),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        _uiState.value = _uiState.value.copy(isThinking = false, currentProvider = "on-device")

                        if (_uiState.value.voiceMode && _uiState.value.autoSpeak) {
                            speak(fullResponse, assistantId)
                        }
                        return@launch
                    } else {
                        // On-device failed, fallback to online
                        android.util.Log.w("NexaVM", "On-device inference failed, falling back to cloud")
                        _uiState.update { it.copy(isOnDeviceActive = false, routingReason = "Fallback a Cloud (on-device falló)") }
                    }
                }

                // ─── ONLINE INFERENCE ───
                android.util.Log.d("NexaVM", "Routing to ONLINE: ${routingDecision.reason}")
                val groqKey = _uiState.value.groqApiKey
                val loc = _uiState.value.locationData
                val messageFlow = if (groqKey.isNotBlank()) {
                    repository.sendMessageDirect(allMessages, groqKey, language = _uiState.value.language.code)
                } else {
                    repository.sendMessageFree(allMessages, language = _uiState.value.language.code)
                }

                messageFlow.collect { event ->
                    when (event) {
                        is StreamEvent.Text -> {
                            fullResponse += event.text
                            updateActiveSession { s ->
                                val updated = s.messages.toMutableList()
                                val idx = updated.indexOfFirst { it.id == assistantId }
                                if (idx >= 0) {
                                    updated[idx] = updated[idx].copy(content = fullResponse, isStreaming = true)
                                } else {
                                    updated.add(Message(id = assistantId, role = "assistant", content = fullResponse, isStreaming = true))
                                }
                                s.copy(messages = updated)
                            }
                            _uiState.value = _uiState.value.copy(isThinking = false)
                        }
                        is StreamEvent.Provider -> {
                            _uiState.value = _uiState.value.copy(currentProvider = event.name)
                        }
                        is StreamEvent.Error -> {
                            val lang = _uiState.value.language
                            val translatedError = when {
                                event.message == "rate_limit" -> NexaStrings.get("rate_limit", lang)
                                event.message.startsWith("connection_error:") -> "${NexaStrings.get("connection_error", lang)}: ${event.message.removePrefix("connection_error:")}"
                                event.message.startsWith("server_error:") -> "${NexaStrings.get("server_error", lang)} (${event.message.removePrefix("server_error:")})"
                                else -> event.message
                            }
                            _uiState.value = _uiState.value.copy(error = translatedError, isThinking = false)
                        }
                        is StreamEvent.AuthExpired -> {
                            _uiState.value = _uiState.value.copy(
                                error = NexaStrings.get("session_expired", _uiState.value.language),
                                isThinking = false
                            )
                            logout()
                            navigateToLogin()
                        }
                        is StreamEvent.Done -> {
                            updateActiveSession { s ->
                                val updated = s.messages.toMutableList()
                                val idx = updated.indexOfFirst { it.id == assistantId }
                                if (idx >= 0) {
                                    updated[idx] = updated[idx].copy(isStreaming = false)
                                }
                                s.copy(messages = updated, updatedAt = System.currentTimeMillis())
                            }
                            _uiState.value = _uiState.value.copy(isThinking = false)

                            // ML Learning: learn from this interaction
                            if (fullResponse.isNotBlank()) {
                                viewModelScope.launch {
                                    try {
                                        val voiceEmotion = voiceEnhancer.voiceState.value.voiceEmotion
                                        mlEngine.learnFromInteraction(
                                            userMessage = content,
                                            aiResponse = fullResponse,
                                            emotionDetected = voiceEmotion
                                        )
                                        // Update conversation context
                                        conversationEngine.updateContext(
                                            userMessage = content,
                                            aiResponse = fullResponse,
                                            emotion = voiceEmotion
                                        )

                                        // ─── NEW: Store episodic memory ───
                                        try {
                                            val sessionId = _uiState.value.activeSessionId ?: ""
                                            val emotionProfile = enhancedEmotionAnalyzer.analyzeEmotion(content)
                                            episodicMemoryManager.storeMemory(
                                                sessionId = sessionId,
                                                type = com.nexa.ai.memory.MemoryType.CONTEXT,
                                                content = "User: $content\nAI: ${fullResponse.take(200)}",
                                                summary = "${content.take(80)} → ${fullResponse.take(80)}",
                                                importance = if (emotionProfile.confidence > 0.3f) 0.7f else 0.4f,
                                                emotion = emotionProfile.primaryEmotion.name.lowercase()
                                            )
                                        } catch (_: Exception) {}

                                        // ─── NEW: Update user profile ───
                                        try {
                                            userProfileManager.recordInteraction(
                                                message = content,
                                                topic = extractTopic(content),
                                                isVoiceMode = _uiState.value.voiceMode
                                            )
                                        } catch (_: Exception) {}
                                    } catch (_: Exception) {}
                                }
                            }

                            if (_uiState.value.autoSpeak && fullResponse.isNotBlank()) {
                                // Add a tiny "breathing" delay before speaking in voice mode
                                if (_uiState.value.voiceMode) {
                                    viewModelScope.launch {
                                        kotlinx.coroutines.delay(500)
                                        speak(fullResponse, assistantId)
                                    }
                                } else {
                                    speak(fullResponse, assistantId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "${NexaStrings.get("connection_error", _uiState.value.language)}: ${e.localizedMessage ?: NexaStrings.get("unknown", _uiState.value.language)}",
                    isThinking = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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
            speechManager.startVoiceAudioSession()
            speechManager.startListening()
        } else {
            speechManager.stopBargeInMonitor()
            speechManager.stopListening()
            speechManager.stopSpeaking()
            speechManager.stopVoiceAudioSession()
        }
    }

    fun stopVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = false)
        speechManager.stopBargeInMonitor()
        speechManager.stopListening()
        speechManager.stopSpeaking()
        speechManager.stopVoiceAudioSession()
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
            _uiState.update { it.copy(isLocating = true, isLoadingLocation = true) }
            try {
                val location = locationStore.getCurrentLocation()
                _uiState.update { it.copy(locationData = location, isLocating = false, isLoadingLocation = false) }
                if (location.isAvailable) {
                    android.util.Log.d("NexaVM", "Location obtained: ${location.city}, ${location.country} (${location.latitude}, ${location.longitude})")
                    addSystemMessage(if (_uiState.value.language == AppLanguage.SPANISH) 
                        "📍 Estás en ${location.city}, ${location.country}" 
                        else "📍 You are in ${location.city}, ${location.country}")
                } else {
                    addSystemMessage(if (_uiState.value.language == AppLanguage.SPANISH)
                        "⚠️ No pude obtener tu ubicación. Activa el GPS y permisos."
                        else "⚠️ Could not get your location. Enable GPS and permissions.")
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Location error: ${e.message}", e)
                _uiState.update { it.copy(isLocating = false, isLoadingLocation = false) }
                addSystemMessage("❌ Error: ${e.message}")
            }
        }
    }

    fun searchFlightsFromCurrentCity() {
        val city = _uiState.value.locationData.city
        if (city.isBlank()) {
            addSystemMessage(if (_uiState.value.language == AppLanguage.SPANISH)
                "Primero necesito saber tu ubicación. Voy a obtenerla..."
                else "First I need to know your location. I'm getting it...")
            requestLocation()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingFlights = true) }
            addSystemMessage(if (_uiState.value.language == AppLanguage.SPANISH)
                "🔍 Buscando vuelos desde $city..."
                else "🔍 Searching flights from $city...")
            try {
                val flights = flightRepository.searchFlightsFrom(city)
                if (flights.isEmpty()) {
                    addSystemMessage(if (_uiState.value.language == AppLanguage.SPANISH)
                        "No encontré vuelos desde $city."
                        else "No flights found from $city.")
                } else {
                    val list = flights.joinToString("\n") { "✈️ ${it.destination} - ${it.price} (${it.flightNumber})" }
                    addSystemMessage("${if (_uiState.value.language == AppLanguage.SPANISH) "Vuelos disponibles" else "Available flights"}:\n$list")
                }
            } catch (e: Exception) {
                addSystemMessage("Error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isSearchingFlights = false) }
            }
        }
    }

    private fun addSystemMessage(content: String) {
        val sysMsg = Message(role = "assistant", content = content)
        updateActiveSession { session ->
            session.copy(messages = session.messages + sysMsg, updatedAt = System.currentTimeMillis())
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

    fun sendVisionRequest(base64Image: String) {
        val lang = _uiState.value.language
        val question = if (lang == AppLanguage.SPANISH)
            "Analiza esta imagen y describe lo que ves en detalle. Incluye objetos, personas, texto, colores, escena y cualquier información relevante."
        else
            "Analyze this image and describe what you see in detail. Include objects, people, text, colors, scene, and any relevant information."

        _uiState.update { it.copy(cameraImageUri = null) }

        // Create user message with vision indicator
        val userMsg = Message(
            role = "user",
            content = if (lang == AppLanguage.SPANISH) "[Imagen analizada]" else "[Image analyzed]",
        )
        val assistantId = "a-${System.currentTimeMillis()}"

        updateActiveSession { s ->
            s.copy(
                messages = s.messages + userMsg,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.value = _uiState.value.copy(isThinking = true, error = null)

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
                    val visionRepo = com.nexa.ai.data.VisionRepository()
                    val baseUrl = BuildConfig.API_BASE_URL
                    val response = visionRepo.analyzeImage(
                        baseUrl = baseUrl,
                        image = base64Image,
                        mimeType = "image/jpeg",
                        question = question,
                    )
                    result = response.response
                    _uiState.update { it.copy(currentProvider = "vision:${response.provider}") }
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
                    @Suppress("UNCHECKED_CAST") val settings = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, String>
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

    // ═══════════════════════════════════════
    //  SESSION MANAGEMENT — Extended Features
    // ═══════════════════════════════════════

    /** Pin a chat session to the top of the list. */
    fun pinSession(sessionId: String) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val session = sessions[idx].copy(isPinned = true)
            sessions[idx] = session
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessions()
        }
    }

    /** Unpin a chat session. */
    fun unpinSession(sessionId: String) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val session = sessions[idx].copy(isPinned = false)
            sessions[idx] = session
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessions()
        }
    }

    /** Rename a chat session. */
    fun renameSession(sessionId: String, newTitle: String) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val session = sessions[idx].copy(title = newTitle.trim().takeIf { it.isNotBlank() } ?: sessions[idx].title)
            sessions[idx] = session
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessions()
        }
    }

    /** Clone a chat session. */
    fun cloneSession(sessionId: String) {
        val original = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val cloned = ChatSession(
            title = "${original.title} (${if (_uiState.value.language == AppLanguage.SPANISH) "copia" else "copy"})",
            messages = original.messages.map { it.copy(id = "m-${System.currentTimeMillis()}-${it.id.take(5)}") }
        )
        _uiState.value = _uiState.value.copy(
            sessions = listOf(cloned) + _uiState.value.sessions,
            activeSessionId = cloned.id
        )
        persistSessions()
    }

    /** Archive a chat session (mark as archived). */
    fun archiveSession(sessionId: String) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val session = sessions[idx].copy(isArchived = true)
            sessions[idx] = session
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessions()
        }
    }

    /** Share a chat session as text. */
    fun shareSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val text = buildString {
            appendLine("NEXA PRO — ${session.title}")
            appendLine("─".repeat(40))
            for (msg in session.messages) {
                val label = if (msg.role == "user") "👤" else "🤖"
                appendLine("$label ${msg.content}")
                appendLine()
            }
        }
        shareText(text)
    }

    /** Download/export a chat session as JSON. */
    fun downloadSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val json = com.google.gson.Gson().toJson(mapOf(
            "title" to session.title,
            "messages" to session.messages.map { mapOf("role" to it.role, "content" to it.content) },
            "createdAt" to session.createdAt,
            "updatedAt" to session.updatedAt
        ))
        val context = getApplication<Application>()
        try {
            val file = java.io.File(context.cacheDir, "nexa_session_${session.id.take(8)}.json")
            file.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Export Session").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback: share as text
            shareText(json)
        }
    }

    /** Regenerate the last AI response. */
    fun regenerateLast() {
        val session = _uiState.value.activeSession ?: return
        val messages = session.messages
        if (messages.isEmpty()) return

        // Find the last assistant message
        val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIdx < 0) return

        // Find the user message that preceded it
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return

        val userContent = messages[lastUserIdx].content

        // Remove the last assistant message from the session
        val updatedMessages = messages.filterIndexed { idx, _ -> idx != lastAssistantIdx }
        val updatedSession = session.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        val sessions = _uiState.value.sessions.toMutableList()
        val sessionIdx = sessions.indexOfFirst { it.id == _uiState.value.activeSessionId }
        if (sessionIdx >= 0) {
            sessions[sessionIdx] = updatedSession
            _uiState.value = _uiState.value.copy(sessions = sessions)
        }

        // Re-send the user message
        sendMessage(userContent)
    }

    /** Toggle auto-scroll behavior. */
    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScrollEnabled = !_uiState.value.autoScrollEnabled) }
    }

    override fun onCleared() {
        super.onCleared()
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
