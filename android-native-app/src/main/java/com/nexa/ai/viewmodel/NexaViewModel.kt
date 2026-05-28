package com.nexa.ai.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.LocationStore
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.PersistedMessage
import com.nexa.ai.data.PersistedSession
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.StreamEvent
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.ui.NexaStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class NexaViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val speechManager: SpeechManager,
    private val authManager: AuthManager,
    private val repository: NexaRepository,
    private val updateChecker: UpdateChecker,
    private val settingsStore: SettingsStore,
    private val networkMonitor: com.nexa.ai.data.NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()

    // Managers (non-Hilt for now or until fully migrated)
    private val locationStore = LocationStore(context as android.app.Application)
    private val sessionStore = SessionStore(context as android.app.Application)
    private val handsFree = com.nexa.ai.handsfree.NexaHandsFreeAllInOne(context as android.app.Application)
    private val smartRoutingManager = com.nexa.ai.ml.SmartRoutingManager(context as android.app.Application)
    private val memoryManager = com.nexa.ai.memory.EpisodicMemoryManager(context as android.app.Application)
    private val offlineManager = com.nexa.ai.data.OfflineManager(context as android.app.Application)
    private val appLauncher = com.nexa.ai.shortcuts.AppLauncherManager(context as android.app.Application)

    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L
    private var voiceRetryCount = 0
    private val maxVoiceRetries = 10

    // ── Advanced AI System Prompt ──
    private val advancedSystemPrompt = """
You are NEXA, a helpful AI assistant. You speak Spanish by default unless the user uses another language.

CRITICAL RULES — FOLLOW THESE ALWAYS:
1. Be CONCISE and DIRECT. Answer what was asked, nothing more. No filler, no fluff, no unnecessary details.
2. Keep responses SHORT. Maximum 2-3 sentences for simple questions. Only expand for complex topics.
3. When the user asks something simple, give a simple answer. Do not over-explain.
4. DO NOT use markdown symbols like asterisks, hashtags, underscores, backticks, or slashes. Write naturally using plain text and normal punctuation.
5. Do not generate lists with symbols or markdown tables. Use natural prose.
6. Your responses will be read aloud by TTS. Write as you would speak, not as you would write a document.
7. Know the user's location, time, and city. Use this information naturally when relevant.
8. Remember the user's name, preferences, and past conversations. Be personal and friendly.
9. When greeting, be warm but brief. Use the user's name if known.
10. Match the user's language. If they speak Spanish, respond in Spanish. If English, in English.

LEARNING AND MEMORY:
- Remember everything the user tells you about themselves: name, preferences, location, occupation, family.
- Learn from each interaction. If the user corrects you, don't repeat the mistake.
- Adapt your style to the user's preference. If they want short answers, be brief. If they want details, expand.
- Proactively use what you know about the user to personalize responses.

VOICE INTERACTION:
- Speak as a natural person would. Short, clear sentences.
- For simple questions (time, weather, location), give the answer directly.
- Do not add unnecessary context unless the user asks for it.
- If you know the user's city and they ask about weather or time, answer directly for their location.

WHEN ASKED ABOUT LOCATION:
- If the user asks where they are, tell them their city and country from the location data provided.
- If the user asks for the time, give them the current time in their timezone.
- Use location context naturally without being asked to mention it explicitly every time.

DEVELOPMENT CAPABILITIES:
- Generate production-level code in any language.
- Build frontend + backend architectures.
- Create responsive interfaces and applications.
- Generate APIs, database schemas, and optimize performance.
- Detect and fix bugs automatically.

SUPPORTED STACKS:
Frontend: React, Next.js, TailwindCSS, Framer Motion, TypeScript
Backend: Python, FastAPI, Node.js, Express, PostgreSQL, Supabase
AI Frameworks: LangChain, LangGraph, CrewAI, OpenAI SDK
Mobile: Kotlin, Jetpack Compose, Android, iOS, React Native

REAL-TIME DATA & SEARCHES:
- You have access to real-time tools (flights, weather, search, etc.) via your backend functions.
- When returning flight data or prices, format in clean conversational prose without symbols or tables.

NEVER: give lazy answers, invent data, talk too much, over-explain simple things, use markdown formatting in voice responses
ALWAYS: be concise, be accurate, be helpful, remember the user, speak naturally
""".trimIndent()

    /** Builds a dynamic system prompt with location, time, and memory context. */
    private fun buildSystemPrompt(): String {
        val loc = _uiState.value.locationData
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)
        val dayOfWeek = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "Domingo"
            java.util.Calendar.MONDAY -> "Lunes"
            java.util.Calendar.TUESDAY -> "Martes"
            java.util.Calendar.WEDNESDAY -> "Miércoles"
            java.util.Calendar.THURSDAY -> "Jueves"
            java.util.Calendar.FRIDAY -> "Viernes"
            java.util.Calendar.SATURDAY -> "Sábado"
            else -> ""
        }
        val dateStr = "${now.get(java.util.Calendar.DAY_OF_MONTH)}/${now.get(java.util.Calendar.MONTH) + 1}/${now.get(java.util.Calendar.YEAR)}"

        val timeContext = "\n\nCURRENT TIME: It is $dayOfWeek, $dateStr, and the time is $timeStr. Use this when the user asks about time, dates, or scheduling."

        val locationContext = if (loc.isAvailable) {
            val tzPart = if (loc.timezone.isNotBlank()) ". Timezone: ${loc.timezone}" else ""
            val sourcePart = if (loc.source == "ip") " (approximate, via IP)" else ""
            "\n\nUSER LOCATION: The user is currently in ${loc.city}, ${loc.country}${sourcePart}${tzPart} (coordinates: ${loc.latitude}, ${loc.longitude}). Always use this to answer location, weather, and time zone questions directly. If the user asks where they are, say '${loc.city}, ${loc.country}'."
        } else {
            "\n\nUSER LOCATION: Location not available yet. If the user asks where they are, tell them you are trying to get their location."
        }

        // Add episodic memory context
        val lastUserMsg = _uiState.value.messages.lastOrNull { it.role == "user" }?.content ?: ""
        val memoryContext = memoryManager.buildMemoryContext(lastUserMsg)
        val memorySection = if (memoryContext.isNotBlank()) {
            "\n\nUSER CONTEXT FROM MEMORY:\n$memoryContext"
        } else {
            ""
        }

        return advancedSystemPrompt + timeContext + locationContext + memorySection
    }

    // Debounce logic — prevents rapid/accidental voice triggers
    // Reduced from 600ms to 450ms for a more fluid conversation feel (like ChatGPT)
    private var speechDebounceJob: kotlinx.coroutines.Job? = null
    private val speechDebounceTimeMs = 450L

    // Track last successful voice result time to prevent duplicate sends
    private var lastVoiceResultAt = 0L
    private val voiceResultCooldownMs = 1000L

    private val surprisePromptsEs = listOf(
        "Busca un vuelo de Miami a Las Vegas para mañana",
        "¿Cuál es el precio actual de Bitcoin y Ethereum?",
        "Muéstrame el clima actual en Tokio",
        "¿Quién es el director de Inception y de qué trata?",
        "¿Cuál es la distancia exacta de la Tierra a Marte?",
        "Muéstrame la imagen de la NASA del día",
        "Genera números de la suerte para el Powerball",
        "Busca en YouTube el mejor tutorial de Kotlin",
        "Abre la cámara y transcríbeme el texto",
        "¿Qué se dice en Reddit sobre inteligencia artificial?"
    )

    private val surprisePromptsEn = listOf(
        "Find a flight from Miami to Las Vegas for tomorrow",
        "What is the current price of Bitcoin and Ethereum?",
        "Show me the current weather in Tokyo",
        "Who directed Inception and what is it about?",
        "What is the exact distance from Earth to Mars?",
        "Show me the NASA picture of the day",
        "Generate lucky numbers for Powerball",
        "Search YouTube for the best Kotlin tutorial",
        "Open the camera and transcribe the text for me",
        "What is Reddit saying about artificial intelligence?"
    )

    init {
        setupSpeechCallbacks()
        speechManager.initialize()
        locationStore.initialize()
        restoreState()
        observeNetwork()
        observeOfflineState()
        // Auto-request location on startup
        requestLocation()

        // Initialize Hands-Free
        handsFree.initialize()
        handsFree.onUserSaid = { text -> sendMessage(text) }
        handsFree.onError = { error -> _uiState.update { it.copy(error = error) } }

        // Initialize on-device ML engine
        viewModelScope.launch {
            val initialized = smartRoutingManager.initialize()
            if (initialized) {
                android.util.Log.i("NexaVM", "On-device ML engine initialized — hybrid mode available")
                _uiState.update { it.copy(onDeviceReady = true) }
            }
        }

        // Initialize notification channels
        com.nexa.ai.notification.NexaNotificationManager.createChannels(context)

        // ── Auto-greeting on app launch ──
        // Waits for location + session to be ready, then sends a personalized greeting
        triggerAutoGreeting()
    }

    // Track if greeting has been sent this session to avoid duplicates
    private var hasGreetedThisSession = false

    /**
     * Sends an automatic personalized greeting when the app opens.
     * Uses: time of day, user name (from memory), and city (from GPS).
     * Only triggers once per app launch.
     */
    private fun triggerAutoGreeting() {
        if (hasGreetedThisSession) return
        viewModelScope.launch {
            // Wait a bit for location and state to be ready
            kotlinx.coroutines.delay(2000)

            if (hasGreetedThisSession) return@launch
            hasGreetedThisSession = true

            val profile = memoryManager.getUserProfile()
            val loc = _uiState.value.locationData
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val lang = _uiState.value.language

            // Build time-of-day greeting
            val timeGreeting = when {
                hour in 5..11 -> if (lang == AppLanguage.SPANISH) "Buenos días" else "Good morning"
                hour in 12..17 -> if (lang == AppLanguage.SPANISH) "Buenas tardes" else "Good afternoon"
                hour in 18..21 -> if (lang == AppLanguage.SPANISH) "Buenas noches" else "Good evening"
                else -> if (lang == AppLanguage.SPANISH) "Hola" else "Hello"
            }

            // Build name part
            val namePart = if (profile.name.isNotBlank()) {
                if (lang == AppLanguage.SPANISH) ", ${profile.name}" else ", ${profile.name}"
            } else ""

            // Build location part
            val locationPart = if (loc.isAvailable && loc.city.isNotBlank()) {
                if (lang == AppLanguage.SPANISH) ". Estás en ${loc.city}" else ". You are in ${loc.city}"
            } else ""

            val greeting = if (lang == AppLanguage.SPANISH) {
                "$timeGreeting$namePart$locationPart. ¿En qué te puedo ayudar?"
            } else {
                "$timeGreeting$namePart$locationPart. How can I help you?"
            }

            // Add greeting as an assistant message (not as a user→AI round trip)
            val greetingMsg = Message(id = "greeting-${System.currentTimeMillis()}", role = "assistant", content = greeting)
            updateActiveSession { s ->
                s.copy(messages = s.messages + greetingMsg, updatedAt = System.currentTimeMillis())
            }

            // Speak the greeting if auto-speak is on
            if (_uiState.value.autoSpeak) {
                kotlinx.coroutines.delay(500)
                speak(greeting, greetingMsg.id)
            }

            // Update user interaction count in memory
            memoryManager.updateProfile { it.copy(lastInteraction = System.currentTimeMillis()) }
        }
    }

    // ═══════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
                // Update SmartRoutingManager with network status
                smartRoutingManager.updateNetworkStatus(isOnline)
                if (!isOnline) {
                    _uiState.update { it.copy(error = NexaStrings.get("no_internet", _uiState.value.language)) }
                }
            }
        }
    }

    private fun observeOfflineState() {
        viewModelScope.launch {
            offlineManager.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingMessageCount = count) }
            }
        }
        viewModelScope.launch {
            offlineManager.isOnline.collect { online ->
                if (online) {
                    // Network restored — flush pending messages
                    val pending = offlineManager.flushPendingMessages()
                    for ((sessionId, content) in pending) {
                        if (sessionId == _uiState.value.activeSessionId) {
                            sendMessage(content)
                        }
                    }
                }
            }
        }
    }

    private fun setupSpeechCallbacks() {
        speechManager.onListeningStateChanged = { isListening ->
            _uiState.update { it.copy(isListening = isListening) }
            if (isListening) voiceRetryCount = 0 // ¡Reiniciamos el contador si empieza a escuchar bien!
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
                            // ═══ v5.3 SEAMLESS TRANSITION IMPROVEMENT ═══
                            // Reduced from 450ms to 150ms. 
                            // Using a much shorter gap to make it feel like "one" session.
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
                    kotlinx.coroutines.delay(80)
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
                    // Si falla muchas veces, apagamos modo voz para no volver loco al usuario
                    _uiState.update { it.copy(voiceMode = false) }
                    speechManager.stopListening()
                    stopVoiceMode()
                } else {
                    // Reintento normal con backoff exponencial
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
                    // ═══ v5.3 FAST RE-ARM ═══
                    // Reduced from 2000ms to 400ms for near-instant re-listening
                    // if the user stops talking and the recognizer times out.
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
            _uiState.value = _uiState.value.copy(
                themeMode = savedTheme,
                language = savedLanguage,
                voiceType = savedVoice,
                accentColor = savedAccent
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

        persistSessions()
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

        // Callar la IA y apagar micrófono antes de enviar
        if (_uiState.value.voiceMode) {
            speechManager.stopListening()
            speechManager.stopSpeaking()
        }

        // --- VOICE COMMAND DETECTION ---
        if (_uiState.value.voiceMode) {
            val cmd = content.lowercase().trim()
            val lang = _uiState.value.language
            
            if (cmd.contains("limpiar chat") || cmd.contains("borra el chat") || cmd.contains("clear chat")) {
                clearChat()
                speak(if (lang == AppLanguage.SPANISH) "Chat borrado" else "Chat cleared")
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
                speak("Idioma cambiado a español")
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
            // Voice command: remember this / store fact
            if (cmd.contains("recuerda") || cmd.contains("recordar") || cmd.contains("remember this") || cmd.contains("memoriza")) {
                val fact = cmd
                    .replace(Regex("(recuerda|recordar|remember this|memoriza|que)\\s+"), "")
                    .trim()
                if (fact.isNotBlank()) {
                    memoryManager.storeFact(fact)
                    memoryManager.storeMemory(com.nexa.ai.memory.EpisodicMemoryManager.MemoryEntry(
                        content = fact,
                        category = com.nexa.ai.memory.EpisodicMemoryManager.MemoryCategory.GENERAL,
                        importance = 0.8f
                    ))
                    speak(if (lang == AppLanguage.SPANISH) "Lo recordaré: $fact" else "I'll remember that: $fact")
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "¿Qué quieres que recuerde?" else "What should I remember?")
                }
                return
            }
            // Voice command: what do you know about me
            if (cmd.contains("qué sabes de mí") || cmd.contains("what do you know about me") || cmd.contains("quién soy")) {
                val profile = memoryManager.getUserProfile()
                val memories = memoryManager.getMemories().take(5)
                val response = buildString {
                    if (profile.name.isNotBlank()) append("Te llamas ${profile.name}. ")
                    if (profile.occupation.isNotBlank()) append("Eres ${profile.occupation}. ")
                    if (profile.location.isNotBlank()) append("Vives en ${profile.location}. ")
                    if (memories.isNotEmpty()) {
                        append("Recuerdo: ")
                        memories.forEach { append("${it.content}. ") }
                    }
                    if (isEmpty()) append(if (lang == AppLanguage.SPANISH) "Aún no tengo mucha información sobre ti. ¡Cuéntame más!" else "I don't have much info about you yet. Tell me more!")
                }
                speak(response)
                return
            }
            // Voice command: open app
            if (cmd.contains("abre") || cmd.contains("open") || cmd.contains("abrir")) {
                val appName = cmd
                    .replace(Regex("(abre|open|abrir)\\s+"), "")
                    .trim()
                if (appName.isNotBlank()) {
                    val opened = appLauncher.openApp(appName)
                    speak(if (lang == AppLanguage.SPANISH) {
                        if (opened) "Abriendo $appName" else "No encontré la aplicación $appName"
                    } else {
                        if (opened) "Opening $appName" else "Couldn't find app $appName"
                    })
                }
                return
            }
            // Voice command: set alarm
            if (cmd.contains("alarma") || cmd.contains("alarm")) {
                val timeRegex = Regex("(\\d{1,2}):(\\d{2})")
                val match = timeRegex.find(cmd)
                if (match != null) {
                    val hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].toInt()
                    appLauncher.setAlarm(hour, minute)
                    speak(if (lang == AppLanguage.SPANISH) "Alarma configurada a las $hour:$minute" else "Alarm set for $hour:$minute")
                } else {
                    // Try extracting just a number
                    val numRegex = Regex("(\\d{1,2})")
                    val numMatch = numRegex.find(cmd.replace(Regex("(alarma|alarm)"), ""))
                    if (numMatch != null) {
                        val hour = numMatch.groupValues[1].toInt()
                        appLauncher.setAlarm(hour, 0)
                        speak(if (lang == AppLanguage.SPANISH) "Alarma configurada a las $hour" else "Alarm set for $hour")
                    } else {
                        speak(if (lang == AppLanguage.SPANISH) "¿A qué hora quieres la alarma?" else "What time for the alarm?")
                    }
                }
                return
            }
            // Voice command: call
            if (cmd.contains("llama a") || cmd.contains("llamar a") || cmd.contains("call")) {
                val contact = cmd
                    .replace(Regex("(llama a|llamar a|call)\\s+"), "")
                    .trim()
                if (contact.isNotBlank()) {
                    // Try to extract a phone number, otherwise open dialer
                    val phoneRegex = Regex("\\+?\\d[\\d\\s-]{6,}")
                    val phoneMatch = phoneRegex.find(contact)
                    if (phoneMatch != null) {
                        appLauncher.makeCall(phoneMatch.value)
                    } else {
                        // Open dialer with name (user will select contact)
                        appLauncher.makeCall("")
                    }
                    speak(if (lang == AppLanguage.SPANISH) "Llamando a $contact" else "Calling $contact")
                }
                return
            }
            // Voice command: remind me / reminder
            if (cmd.contains("recuérdame") || cmd.contains("recuerdame") || cmd.contains("remind me") || cmd.contains("recordatorio")) {
                val reminderText = cmd
                    .replace(Regex("(recuérdame|recuerdame|remind me|recordatorio)\\s+"), "")
                    .trim()
                    .replace(Regex("(a las|at)\\s+\\d{1,2}(:\\d{2})?"), "")
                    .trim()
                if (reminderText.isNotBlank()) {
                    // Parse time - for now use a simple approach: 1 minute from now for demo
                    val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    val timeMillis = System.currentTimeMillis() + 60000 // 1 min from now
                    com.nexa.ai.notification.NexaNotificationManager.createChannels(context)
                    com.nexa.ai.notification.NexaNotificationManager.scheduleReminder(context, reminderText, timeMillis, id)
                    speak(if (lang == AppLanguage.SPANISH) "Recordatorio guardado: $reminderText" else "Reminder saved: $reminderText")
                }
                return
            }
            // Voice command: timer
            if (cmd.contains("temporizador") || cmd.contains("timer") || cmd.contains("cuenta atrás")) {
                val minuteRegex = Regex("(\\d+)\\s*(minutos?|minutes?|mins?)")
                val minMatch = minuteRegex.find(cmd)
                val secondsRegex = Regex("(\\d+)\\s*(segundos?|seconds?|secs?)")
                val secMatch = secondsRegex.find(cmd)
                val totalSeconds = (minMatch?.groupValues?.get(1)?.toIntOrNull()?.times(60) ?: 0) +
                                   (secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0)
                if (totalSeconds > 0) {
                    appLauncher.setTimer(totalSeconds)
                    speak(if (lang == AppLanguage.SPANISH) "Temporizador de $totalSeconds segundos" else "Timer set for $totalSeconds seconds")
                } else {
                    speak(if (lang == AppLanguage.SPANISH) "¿Cuánto tiempo para el temporizador?" else "How long for the timer?")
                }
                return
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

        var session = _uiState.value.activeSession
        if (session == null) {
            val newSession = ChatSession()
            val updatedSessions = listOf(newSession) + _uiState.value.sessions
            _uiState.value = _uiState.value.copy(
                sessions = updatedSessions,
                activeSessionId = newSession.id
            )
            session = newSession
        }

        val isFirstMessage = session.messages.isEmpty()
        val title = if (isFirstMessage) {
            content.take(30) + if (content.length > 30) "..." else ""
        } else {
            session.title.ifEmpty { NexaStrings.get("new_chat", _uiState.value.language) }
        }

        updateActiveSession { s ->
            s.copy(
                messages = s.messages + userMsg,
                title = title,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.value = _uiState.value.copy(inputText = "", isThinking = true, error = null, pendingAttachment = null)

        // Offline-first: If no network, queue the message
        if (!networkMonitor.isOnline.value) {
            val sessionId = _uiState.value.activeSessionId ?: ""
            viewModelScope.launch {
                val queued = offlineManager.enqueueIfOffline(sessionId, content)
                if (queued) {
                    // Add a system message indicating offline queue
                    updateActiveSession { s ->
                        s.copy(
                            messages = s.messages + Message(
                                id = "offline-${System.currentTimeMillis()}",
                                role = "assistant",
                                content = if (_uiState.value.language == AppLanguage.SPANISH)
                                    "Sin conexión a internet. Tu mensaje se enviará cuando vuelva la conexión."
                                else
                                    "No internet connection. Your message will be sent when connectivity is restored."
                            ),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    _uiState.value = _uiState.value.copy(isThinking = false)
                    return@launch
                }
            }
        }

        // Extract and store episodic memories from user message
        viewModelScope.launch(Dispatchers.IO) {
            val extractedMemories = memoryManager.extractMemoriesFromMessage("user", content, _uiState.value.activeSessionId ?: "")
            if (extractedMemories.isNotEmpty()) {
                android.util.Log.d("NexaVM", "Extracted ${extractedMemories.size} memories from user message")
                // Update user profile name in UI state if detected
                val profile = memoryManager.getUserProfile()
                if (profile.name.isNotBlank() && profile.name != _uiState.value.userProfileName) {
                    _uiState.update { it.copy(userProfileName = profile.name) }
                }
            }
        }

        fetchAiResponse(assistantId)
    }

    private fun fetchAiResponse(assistantId: String) {
        viewModelScope.launch {
            try {
                // v5.2: Refresh location before sending to ensure accurate GPS data
                refreshLocationIfNeeded()

                // ── Smart routing: check if we should use on-device inference ──
                val content = _uiState.value.messages.lastOrNull { it.role == "user" }?.content ?: ""
                val routingDecision = smartRoutingManager.shouldUseOnDevice(
                    query = content,
                    hasImage = _uiState.value.pendingAttachment != null
                )
                android.util.Log.d("NexaVM", "Routing decision: useOnDevice=${routingDecision.useOnDevice}, reason=${routingDecision.reason}")

                if (routingDecision.useOnDevice && smartRoutingManager.getOnDeviceManager().isReady) {
                    // Try on-device inference first
                    val onDeviceResult = smartRoutingManager.generateOnDevice(
                        prompt = content,
                        systemPrompt = buildSystemPrompt()
                    )
                    if (onDeviceResult != null) {
                        // Use on-device result
                        updateActiveSession { s ->
                            s.copy(
                                messages = s.messages + Message(id = assistantId, role = "assistant", content = onDeviceResult),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        _uiState.value = _uiState.value.copy(isThinking = false)
                        if (_uiState.value.autoSpeak && onDeviceResult.isNotBlank()) {
                            speak(onDeviceResult, assistantId)
                        }
                        return@launch
                    }
                    // Fall through to cloud inference if on-device failed
                }

                val allMessages = _uiState.value.messages.map { ChatMessage(it.role, it.content) }
                var fullResponse = ""

                // Send GPS location data to the API server so it can use
                // real GPS coordinates instead of IP-based geolocation (which
                // returns the CDN server location, not the user's location)
                val loc = _uiState.value.locationData
                val lat = if (loc.isAvailable) loc.latitude else null
                val lon = if (loc.isAvailable) loc.longitude else null
                val userCity = if (loc.isAvailable && loc.city.isNotBlank()) loc.city else null
                val userCountry = if (loc.isAvailable && loc.country.isNotBlank()) loc.country else null

                repository.sendMessage(allMessages, BuildConfig.API_BASE_URL,
                    language = _uiState.value.language.code,
                    systemPrompt = buildSystemPrompt(),
                    latitude = lat,
                    longitude = lon,
                    city = userCity,
                    country = userCountry).collect { event ->
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

                            // Cache the response for offline access
                            val sessionId = _uiState.value.activeSessionId ?: ""
                            if (sessionId.isNotBlank() && fullResponse.isNotBlank()) {
                                val lastUserMsg = _uiState.value.messages.lastOrNull { it.role == "user" }?.content ?: ""
                                viewModelScope.launch {
                                    offlineManager.cacheResponse(sessionId, lastUserMsg, fullResponse, _uiState.value.currentProvider ?: "")
                                }
                            }

                            if (_uiState.value.autoSpeak && fullResponse.isNotBlank()) {
                                // v5.2: Reduced "breathing" delay from 500ms to 250ms for snappier replies
                                if (_uiState.value.voiceMode) {
                                    viewModelScope.launch {
                                        kotlinx.coroutines.delay(250)
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

    fun regenerateResponse() {
        val session = _uiState.value.activeSession ?: return
        val messages = session.messages.toMutableList()
        if (messages.isEmpty()) return

        // Remove last assistant message if it exists
        if (messages.last().role == "assistant") {
            messages.removeAt(messages.size - 1)
        }

        updateActiveSession { it.copy(messages = messages) }

        val assistantId = "a-${System.currentTimeMillis()}"
        _uiState.value = _uiState.value.copy(isThinking = true, error = null)
        fetchAiResponse(assistantId)
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
            // v5.2: Pause NexaHandsFreeAllInOne to prevent TTS/STT conflict
            handsFree.pause()
            speechManager.startVoiceAudioSession()
            speechManager.startListening()
        } else {
            speechManager.stopBargeInMonitor()
            speechManager.stopListening()
            speechManager.stopSpeaking()
            speechManager.stopVoiceAudioSession()
            // v5.2: Resume NexaHandsFreeAllInOne
            handsFree.resume()
        }
    }

    fun stopVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = false)
        speechManager.stopBargeInMonitor()
        speechManager.stopListening()
        speechManager.stopSpeaking()
        speechManager.stopVoiceAudioSession()
    }

    /** Alterna el modo manos libres */
    fun toggleHandsFree() {
        val activating = !_uiState.value.handsFreeEnabled
        _uiState.update { it.copy(handsFreeEnabled = activating) }

        if (activating) {
            // AL ACTIVAR: Iniciar sesión de audio completa para manos libres
            _uiState.update { it.copy(voiceMode = true, autoSpeak = true) }
            // v5.2: Pause NexaHandsFreeAllInOne to prevent TTS/STT conflict
            handsFree.pause()
            // CRITICAL FIX: startVoiceAudioSession() must be called to configure:
            // - Audio focus, MODE_NORMAL, speaker routing, Bluetooth SCO, proximity sensor
            // Without this, hands-free audio goes to earpiece with low volume
            speechManager.startVoiceAudioSession()
            speechManager.startListening()
            val lang = _uiState.value.language
            speak(if (lang == AppLanguage.SPANISH) "Modo manos libres activado" else "Hands-free mode activated")
        } else {
            // AL DESACTIVAR: Detener todo y liberar recursos de audio
            speechManager.stopBargeInMonitor()
            speechManager.stopSpeaking()
            speechManager.stopListening()
            // CRITICAL FIX: stopVoiceAudioSession() must be called to release:
            // - Audio focus, speaker routing, Bluetooth SCO, proximity sensor
            speechManager.stopVoiceAudioSession()
            _uiState.update { it.copy(voiceMode = false) }
            // v5.2: Resume NexaHandsFreeAllInOne
            handsFree.resume()
            val lang = _uiState.value.language
            speak(if (lang == AppLanguage.SPANISH) "Modo manos libres desactivado" else "Hands-free mode deactivated")
        }
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
                } else {
                    android.util.Log.w("NexaVM", "Location not available after request")
                }
            } catch (e: Exception) {
                android.util.Log.e("NexaVM", "Location error: ${e.message}", e)
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    /**
     * v5.2: Refresh location before sending a message to ensure
     * the AI always has the most current user position.
     * Called automatically before each AI request in fetchAiResponse.
     */
    private suspend fun refreshLocationIfNeeded() {
        if (!locationStore.hasLocationPermission()) return
        try {
            val location = locationStore.getCurrentLocation()
            if (location.isAvailable) {
                _uiState.update { it.copy(locationData = location) }
                android.util.Log.d("NexaVM", "Location refreshed: ${location.city}, ${location.country}")
            }
        } catch (e: Exception) {
            // Don't block message sending if location refresh fails
            android.util.Log.w("NexaVM", "Location refresh failed (non-blocking): ${e.message}")
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

    private val visionClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val visionGson = Gson()

    fun sendVisionRequest(base64Image: String, mimeType: String = "image/jpeg") {
        val lang = _uiState.value.language
        val prompt = if (lang == AppLanguage.SPANISH)
            "Analiza esta imagen y describe lo que ves en detalle. Incluye objetos, personas, texto, colores, escena y cualquier información relevante. Responde en español de forma clara y concisa."
        else
            "Analyze this image and describe what you see in detail. Include objects, people, text, colors, scene, and any relevant information. Respond clearly and concisely."

        _uiState.update { it.copy(cameraImageUri = null) }

        val assistantId = "a-${System.currentTimeMillis()}"
        val userMsg = Message(
            role = "user",
            content = "[📷 Imagen adjunta]"
        )

        updateActiveSession { s ->
            s.copy(messages = s.messages + userMsg, updatedAt = System.currentTimeMillis())
        }

        _uiState.update { it.copy(isThinking = true, error = null) }

        viewModelScope.launch {
            try {
                val responseText = withContext(Dispatchers.IO) {
                    performVisionRequest(BuildConfig.API_BASE_URL, base64Image, prompt, mimeType)
                }

                if (responseText != null) {
                    updateActiveSession { s ->
                        s.copy(
                            messages = s.messages + Message(
                                id = assistantId,
                                role = "assistant",
                                content = responseText
                            ),
                            updatedAt = System.currentTimeMillis()
                        )
                    }

                    if (_uiState.value.autoSpeak && responseText.isNotBlank()) {
                        speak(responseText, assistantId)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = NexaStrings.get("connection_error", lang),
                            isThinking = false
                        )
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("NexaVM", "Vision request failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        error = "${NexaStrings.get("connection_error", lang)}: ${e.localizedMessage ?: NexaStrings.get("unknown", lang)}",
                        isThinking = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isThinking = false) }
            }
        }
    }

    private fun performVisionRequest(baseUrl: String, base64Image: String, question: String, mimeType: String = "image/jpeg"): String? {
        val jsonBody = JsonObject().apply {
            addProperty("image", base64Image)
            addProperty("mimeType", mimeType)
            addProperty("question", question)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/vision")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        visionClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("NexaVM", "Vision API error: ${response.code} - ${response.body?.string()}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            return try {
                val json = visionGson.fromJson(responseBody, JsonObject::class.java)
                if (json.has("response")) {
                    json.get("response").asString
                } else if (json.has("description")) {
                    json.get("description").asString
                } else if (json.has("text")) {
                    json.get("text").asString
                } else if (json.has("content")) {
                    json.get("content").asString
                } else {
                    // Fallback: return the raw response
                    json.toString()
                }
            } catch (e: Exception) {
                Log.e("NexaVM", "Failed to parse vision response: ${e.message}")
                responseBody
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

    fun previewVoice() {
        val lang = _uiState.value.language
        val text = if (lang == AppLanguage.SPANISH) "Hola, esta es una vista previa de mi voz." else "Hello, this is a preview of my voice."
        speak(text)
    }

    fun exportSettings() {
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
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("NEXA PRO", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, NexaStrings.get("copied", _uiState.value.language), android.widget.Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
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
            android.widget.Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
        handsFree.release()
        offlineManager.destroy()
        smartRoutingManager.shutdown()
    }
}
