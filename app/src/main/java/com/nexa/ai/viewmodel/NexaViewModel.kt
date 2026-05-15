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
    private val sendCooldownMs = 800L

    // Debounce persist to avoid DB rewrite on every streaming chunk
    private var persistJob: kotlinx.coroutines.Job? = null
    private val persistDebounceMs = 500L

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
        }
        speechManager.onSpeakingStateChanged = { isSpeaking, messageId ->
            _uiState.value = _uiState.value.copy(isSpeaking = isSpeaking, speakingMessageId = messageId)
            // Voice mode: when AI finishes speaking, restart listening
            if (!isSpeaking && _uiState.value.voiceMode) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(400)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening && !_uiState.value.isThinking) {
                        speechManager.startListening()
                    }
                }
            }
        }
        speechManager.onSpeechResult = { text ->
            sendMessage(text)
        }
        speechManager.onSpeechPartial = { text ->
            _uiState.value = _uiState.value.copy(inputText = text)
        }
        speechManager.onError = { errorKey ->
            if (_uiState.value.voiceMode) {
                // In voice mode, silently retry after a short delay
                viewModelScope.launch {
                    kotlinx.coroutines.delay(800)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening && !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
                        speechManager.startListening()
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
                    kotlinx.coroutines.delay(500)
                    if (_uiState.value.voiceMode && !_uiState.value.isListening && !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
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
        // Cancel previous pending persist and debounce
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(persistDebounceMs)
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

    /** Immediate persist — used for structural changes (delete, create, switch). */
    private fun persistSessionsImmediate() {
        persistJob?.cancel()
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
        // Clear state first to avoid race with createNewSession
        _uiState.value = _uiState.value.copy(
            user = UserData(),
            sessions = emptyList(),
            activeSessionId = null,
            currentScreen = Screen.CHAT,
            drawerOpen = false
        )
        // Then clear persisted data and create fresh session
        viewModelScope.launch {
            authManager.logout()
            sessionStore.clear()
            // Create new session after DB is cleared
            val session = ChatSession()
            _uiState.value = _uiState.value.copy(
                sessions = listOf(session),
                activeSessionId = session.id
            )
            persistSessionsImmediate()
        }
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
        persistSessionsImmediate()
    }

    fun switchSession(sessionId: String) {
        speechManager.stopSpeaking()
        _uiState.value = _uiState.value.copy(
            activeSessionId = sessionId,
            drawerOpen = false,
            error = null
        )
        persistSessionsImmediate()
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
            persistSessionsImmediate()
        }
    }

    fun renameSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        // Set the first user message as title if session has messages
        val newTitle = session.messages.firstOrNull { it.role == "user" }?.content?.take(30)?.let {
            it + if (it.length > 30) "..." else ""
        } ?: NexaStrings.get("new_chat", _uiState.value.language)
        updateSession(sessionId) { it.copy(title = newTitle) }
    }

    fun cloneSession(sessionId: String) {
        val source = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val clone = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${source.title} (copia)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(
            sessions = listOf(clone) + _uiState.value.sessions,
            activeSessionId = clone.id,
            drawerOpen = false
        )
        persistSessionsImmediate()
    }

    fun archiveSession(sessionId: String) {
        // Archive = move to end of list (visual de-prioritization)
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            val session = sessions.removeAt(idx)
            sessions.add(session) // move to end
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessionsImmediate()
        }
    }

    fun shareSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val context = getApplication<Application>()
        val text = buildString {
            appendLine("=== ${session.title} ===")
            appendLine()
            session.messages.forEach { msg ->
                val prefix = if (msg.role == "user") "👤" else "⚡"
                appendLine("$prefix ${msg.content}")
                appendLine()
            }
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, session.title)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = android.content.Intent.createChooser(intent, NexaStrings.get("share_chat", _uiState.value.language))
        shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun downloadSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        val context = getApplication<Application>()
        val text = buildString {
            appendLine("=== ${session.title} ===")
            appendLine("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))}")
            appendLine()
            session.messages.forEach { msg ->
                val role = if (msg.role == "user") "Tú" else "NEXA"
                appendLine("[$role]: ${msg.content}")
                appendLine()
            }
        }
        try {
            val fileName = "nexa_chat_${session.id.take(8)}.txt"
            val file = java.io.File(context.cacheDir, fileName)
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = android.content.Intent.createChooser(intent, NexaStrings.get("download_chat", _uiState.value.language))
            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Error: ${e.localizedMessage}")
        }
    }

    private fun updateSession(sessionId: String, transform: (ChatSession) -> ChatSession) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            sessions[idx] = transform(sessions[idx])
            _uiState.value = _uiState.value.copy(sessions = sessions)
            persistSessionsImmediate()
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
                                speak(fullResponse, assistantId)
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

    fun clearChat() {
        speechManager.stopSpeaking()
        updateActiveSession { it.copy(messages = emptyList()) }
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun regenerate() {
        val session = _uiState.value.activeSession ?: return
        val messages = session.messages
        if (messages.isEmpty()) return

        // Find last assistant message and remove it + the user message before it
        val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIdx < 0) return

        // Remove last assistant message
        val trimmed = messages.toMutableList()
        trimmed.removeAt(lastAssistantIdx)

        // Find the user message that prompted it
        val lastUserIdx = trimmed.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return

        val userContent = trimmed[lastUserIdx].content
        trimmed.removeAt(lastUserIdx)

        // Update session with trimmed messages
        updateActiveSession { it.copy(messages = trimmed) }

        // Re-send the user message
        lastSendTimestamp = 0L // reset cooldown
        sendMessage(userContent)
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
        com.nexa.ai.util.PdfExporter.exportToPdf(context, message, _uiState.value.language)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}
