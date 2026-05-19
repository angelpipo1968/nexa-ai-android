package com.nexa.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.PersistedMessage
import com.nexa.ai.data.PersistedSession
import com.nexa.ai.data.SessionStore
import com.nexa.ai.data.SettingsStore
import com.nexa.ai.data.StreamEvent
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.ui.NexaStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NexaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()

    // Managers
    private val speechManager = SpeechManager(application)
    private val authManager = AuthManager(application)
    private val repository = NexaRepository()
    private val updateChecker = UpdateChecker()
    private val sessionStore = SessionStore(application)
    private val settingsStore = SettingsStore(application)

    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L
    private var voiceRetryCount = 0 
    private val maxVoiceRetries = 10 
    
    // --- CHAT GPT RECOMMENDATION: DEBOUNCE LOGIC ---
    private var speechDebounceJob: kotlinx.coroutines.Job? = null
    private val speechDebounceTimeMs = 800L // Time to wait for "real" silence before processing

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
        restoreState()
    }

    // ═══════════════════════════════════════
    //  INITIALIZATION
    // ═══════════════════════════════════════

    private fun setupSpeechCallbacks() {
        speechManager.onListeningStateChanged = { isListening ->
            _uiState.value = _uiState.value.copy(isListening = isListening)
            if (isListening) voiceRetryCount = 0 // ¡Reiniciamos el contador si empieza a escuchar bien!
        }
        
        speechManager.onSpeakingStateChanged = { isSpeaking, messageId ->
            _uiState.value = _uiState.value.copy(isSpeaking = isSpeaking, speakingMessageId = messageId)
            
            if (_uiState.value.voiceMode) {
                if (isSpeaking) {
                    // Barge-in: start listening WHILE AI is speaking
                    // so the user can interrupt just by talking
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(400) // Let TTS settle before opening mic
                        if (_uiState.value.voiceMode && _uiState.value.isSpeaking && !_uiState.value.isListening) {
                            speechManager.startListening()
                        }
                    }
                }
                // When AI finishes speaking naturally (not interrupted),
                // the barge-in listener is already running → no need to restart
                // But if it's not listening (e.g. error), restart
                if (!isSpeaking && !_uiState.value.isListening && !_uiState.value.isThinking) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(300)
                        if (_uiState.value.voiceMode && !_uiState.value.isListening &&
                            !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                            speechManager.startListening()
                        }
                    }
                }
            }
        }
        
        // Barge-in: user started talking while AI was speaking → shut up and listen
        speechManager.onBargeInDetected = {
            if (_uiState.value.voiceMode && _uiState.value.isSpeaking) {
                speechManager.stopSpeaking()
            }
        }
        
        speechManager.onSpeechResult = { text ->
            if (_uiState.value.voiceMode) {
                // Barge-in: if AI was speaking, it's already stopped by onBargeInDetected
                // but double-check in case onBeginningOfSpeech didn't fire
                if (_uiState.value.isSpeaking) {
                    speechManager.stopSpeaking()
                }
                
                // Apply "Debounce" logic to avoid rapid/accidental triggers
                speechDebounceJob?.cancel()
                speechDebounceJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(speechDebounceTimeMs)
                    
                    speechManager.stopListening() // Force stop before processing
                    
                    if (text.trim().length >= 2) {
                        kotlinx.coroutines.delay(300) 
                        sendMessage(text)
                    } else {
                        // Accidental noise, restart listening with delay
                        kotlinx.coroutines.delay(1000)
                        if (_uiState.value.voiceMode && !_uiState.value.isListening) {
                            speechManager.startListening()
                        }
                    }
                }
            } else {
                sendMessage(text)
            }
        }
        
        speechManager.onSpeechPartial = { text ->
            _uiState.value = _uiState.value.copy(inputText = text)
        }
        
        speechManager.onError = { errorKey ->
            if (_uiState.value.voiceMode) {
                voiceRetryCount++ // ¡Sumamos un error!
                
                if (voiceRetryCount >= maxVoiceRetries) {
                    // Si falla muchas veces, apagamos modo voz para no volver loco al usuario
                    _uiState.value = _uiState.value.copy(voiceMode = false)
                    speechManager.stopListening()
                } else {
                    // Reintento normal
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000) 
                        if (_uiState.value.voiceMode && !_uiState.value.isListening && !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                            speechManager.startListening()
                        }
                    }
                }
            } else {
                val lang = _uiState.value.language
                _uiState.value = _uiState.value.copy(error = NexaStrings.get(errorKey, lang))
            }
        }
        
        speechManager.onInputTextChanged = { text ->
            _uiState.value = _uiState.value.copy(inputText = text)
        }
        
        // Voice mode: retry on recognition ended without match
        speechManager.onRecognitionEnded = {
            if (_uiState.value.voiceMode) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening && 
                        !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                        speechManager.startListening()
                    }
                }
            }
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
            _uiState.value = _uiState.value.copy(
                themeMode = savedTheme,
                language = savedLanguage,
                voiceType = savedVoice
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
            if (cmd.contains("detener manos libres") || cmd.contains("stop hands free")) {
                stopVoiceMode()
                speak(if (lang == AppLanguage.SPANISH) "Modo manos libres desactivado" else "Hands free mode off")
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

                repository.sendMessage(allMessages, BuildConfig.API_BASE_URL,
                    language = _uiState.value.language.code).collect { event ->
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
        _uiState.value = _uiState.value.copy(voiceMode = activating)
        if (activating) {
            // Enable auto-speak so AI responses are spoken aloud
            _uiState.value = _uiState.value.copy(autoSpeak = true)
            speechManager.startListening()
        } else {
            speechManager.stopListening()
            speechManager.stopSpeaking()
        }
    }

    fun stopVoiceMode() {
        _uiState.value = _uiState.value.copy(voiceMode = false)
        speechManager.stopListening()
        speechManager.stopSpeaking()
    }

    fun interruptVoice() {
        speechManager.stopSpeaking()
        // stopSpeaking triggers onSpeakingStateChanged(false) which starts listening
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
            android.widget.Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}
