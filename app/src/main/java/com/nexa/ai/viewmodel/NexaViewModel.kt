package com.nexa.ai.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

// ═══════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Nuevo chat",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class VoiceType { MALE_1, MALE_2, FEMALE_1, FEMALE_2 }
enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English")
}

data class UserData(
    val email: String = "",
    val displayName: String = "",
    val isLoggedIn: Boolean = false
)

enum class Screen { CHAT, LOGIN, REGISTER }

data class NexaUiState(
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val inputText: String = "",
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val currentProvider: String? = null,
    val error: String? = null,
    val autoSpeak: Boolean = true,
    val language: AppLanguage = AppLanguage.SPANISH,
    val voiceType: VoiceType = VoiceType.FEMALE_1,
    val isDarkTheme: Boolean = true,
    val drawerOpen: Boolean = false,
    val showSettings: Boolean = false,
    // Login
    val currentScreen: Screen = Screen.CHAT,
    val user: UserData = UserData(),
    val loginEmail: String = "",
    val loginPassword: String = "",
    val loginError: String? = null,
    val isLoggingIn: Boolean = false,
    // Register
    val registerName: String = "",
    val registerEmail: String = "",
    val registerPassword: String = "",
    val registerConfirmPassword: String = "",
    val registerError: String? = null,
    val isRegistering: Boolean = false,
    // Update
    val updateInfo: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false
) {
    val activeSession: ChatSession?
        get() = sessions.find { it.id == activeSessionId }

    val messages: List<Message>
        get() = activeSession?.messages ?: emptyList()
}

class NexaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()

    private val repository = NexaRepository()
    private val updateChecker = UpdateChecker()
    private val sessionStore = SessionStore(application)
    private val userStore = UserStore(application)
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Rate limiting
    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L // 1.5 seconds between sends

    init {
        initTTS()
        restoreState()
    }

    // ═══════════════════════════════════════
    //  STATE PERSISTENCE
    // ═══════════════════════════════════════

    private fun restoreState() {
        viewModelScope.launch {
            // Restore user session
            val savedUser = userStore.currentUser.first()
            if (savedUser != null) {
                _uiState.value = _uiState.value.copy(
                    user = UserData(
                        email = savedUser.email,
                        displayName = savedUser.displayName,
                        isLoggedIn = true
                    )
                )
            }

            // Restore chat sessions
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
                val info = updateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
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
        val url = _uiState.value.updateInfo?.downloadUrl
            ?: "https://github.com/angelpipo1968/nexa-ai-android/releases/latest"
        updateChecker.openDownloadPage(getApplication(), url)
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)
    }

    // ═══════════════════════════════════════
    //  LOGIN / REGISTER (PERSISTENT)
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

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(loginError = "Completa todos los campos")
            return
        }

        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(loginError = "Email no válido")
            return
        }

        _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)

            try {
                val displayName = userStore.loginOrAutoRegister(email, password)
                val user = UserData(email = email, displayName = displayName, isLoggedIn = true)
                userStore.saveUser(PersistedUser(email, displayName))

                _uiState.value = _uiState.value.copy(
                    user = user,
                    currentScreen = Screen.CHAT,
                    isLoggingIn = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loginError = "Error: ${e.message}",
                    isLoggingIn = false
                )
            }
        }
    }

    fun register() {
        val name = _uiState.value.registerName.trim()
        val email = _uiState.value.registerEmail.trim()
        val password = _uiState.value.registerPassword
        val confirm = _uiState.value.registerConfirmPassword

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(registerError = "Completa todos los campos")
            return
        }

        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(registerError = "Email no válido")
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(registerError = "Mínimo 6 caracteres")
            return
        }

        if (password != confirm) {
            _uiState.value = _uiState.value.copy(registerError = "Las contraseñas no coinciden")
            return
        }

        _uiState.value = _uiState.value.copy(isRegistering = true, registerError = null)

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)

            try {
                val success = userStore.register(name, email, password)
                if (success) {
                    val user = UserData(email = email, displayName = name, isLoggedIn = true)
                    userStore.saveUser(PersistedUser(email, name))

                    _uiState.value = _uiState.value.copy(
                        user = user,
                        currentScreen = Screen.CHAT,
                        isRegistering = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        registerError = "Este email ya está registrado",
                        isRegistering = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    registerError = "Error: ${e.message}",
                    isRegistering = false
                )
            }
        }
    }

    fun logout() {
        stopSpeaking()
        viewModelScope.launch {
            userStore.clearUser()
        }
        _uiState.value = _uiState.value.copy(
            user = UserData(),
            currentScreen = Screen.CHAT,
            drawerOpen = false
        )
    }

    // ═══════════════════════════════════════
    //  TTS — Text to Speech
    // ═══════════════════════════════════════

    private fun initTTS() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyVoiceSettings()
                tts?.setSpeechRate(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = true, speakingMessageId = utteranceId)
                    }
                    override fun onDone(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null)
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null)
                    }
                })
            }
        }
    }

    private fun applyVoiceSettings() {
        if (!ttsReady) return
        val locale = when (_uiState.value.language) {
            AppLanguage.SPANISH -> Locale("es", "ES")
            AppLanguage.ENGLISH -> Locale.US
        }
        tts?.language = locale

        val voices = tts?.voices?.filter { it.locale.language == locale.language } ?: return
        if (voices.isEmpty()) return

        val voiceType = _uiState.value.voiceType
        val isMale = voiceType == VoiceType.MALE_1 || voiceType == VoiceType.MALE_2
        val isSecond = voiceType == VoiceType.MALE_2 || voiceType == VoiceType.FEMALE_2

        val genderTag = if (isMale) "male" else "female"
        val genderVoices = voices.filter { it.name.lowercase().contains(genderTag) }
        val candidates = if (genderVoices.isNotEmpty()) genderVoices else voices

        val selectedVoice = if (isSecond && candidates.size > 1) {
            candidates[1]
        } else {
            candidates[0]
        }

        tts?.voice = selectedVoice
    }

    fun speak(text: String, messageId: String? = null) {
        if (!ttsReady) return

        if (messageId != null && _uiState.value.speakingMessageId == messageId) {
            stopSpeaking()
            return
        }

        stopSpeaking()
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, messageId ?: "msg")
    }

    fun stopSpeaking() {
        tts?.stop()
        _uiState.value = _uiState.value.copy(isSpeaking = false, speakingMessageId = null)
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*{1,3}(.+?)\\*{1,3}"), "$1")
            .replace(Regex("_{1,3}(.+?)_{1,3}"), "$1")
            .replace(Regex("```[\\s\\S]*?```"), "código")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), ". ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    // ═══════════════════════════════════════
    //  SPEECH RECOGNITION
    // ═══════════════════════════════════════

    fun startListening() {
        val context = getApplication<Application>()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.value = _uiState.value.copy(error = "Reconocimiento de voz no disponible")
            return
        }

        stopSpeaking()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.value = _uiState.value.copy(isListening = true, error = null)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _uiState.value = _uiState.value.copy(isListening = false)
                }
                override fun onError(error: Int) {
                    _uiState.value = _uiState.value.copy(isListening = false)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> {}
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {}
                        else -> _uiState.value = _uiState.value.copy(error = "Error de voz: $error")
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    _uiState.value = _uiState.value.copy(inputText = text, isListening = false)
                    sendMessage(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    _uiState.value = _uiState.value.copy(inputText = text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val langCode = when (_uiState.value.language) {
            AppLanguage.SPANISH -> "es-ES"
            AppLanguage.ENGLISH -> "en-US"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    // ═══════════════════════════════════════
    //  SESSION MANAGEMENT (PERSISTENT)
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
        stopSpeaking()
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
        if (content.isBlank()) return

        // Rate limiting
        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp < sendCooldownMs) return
        lastSendTimestamp = now

        val userMsg = Message(role = "user", content = content)
        val assistantId = "a-${System.currentTimeMillis()}"

        val session = _uiState.value.activeSession
        val isFirstMessage = session?.messages?.isEmpty() == true
        val title = if (isFirstMessage) {
            content.take(30) + if (content.length > 30) "..." else ""
        } else {
            session?.title ?: "Nuevo chat"
        }

        updateActiveSession { s ->
            s.copy(
                messages = s.messages + userMsg,
                title = title,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.value = _uiState.value.copy(inputText = "", isThinking = true, error = null)

        viewModelScope.launch {
            val allMessages = _uiState.value.messages.map { ChatMessage(it.role, it.content) }
            var fullResponse = ""

            repository.sendMessage(allMessages, BuildConfig.API_BASE_URL).collect { event ->
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
                        _uiState.value = _uiState.value.copy(error = event.message, isThinking = false)
                    }
                    is StreamEvent.AuthExpired -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Sesión expirada. Inicia sesión de nuevo.",
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
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun toggleAutoSpeak() {
        _uiState.value = _uiState.value.copy(autoSpeak = !_uiState.value.autoSpeak)
        if (!_uiState.value.autoSpeak) stopSpeaking()
    }

    fun clearChat() {
        stopSpeaking()
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

    // ═══════════════════════════════════════
    //  SETTINGS
    // ═══════════════════════════════════════

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun setLanguage(lang: AppLanguage) {
        _uiState.value = _uiState.value.copy(language = lang)
        applyVoiceSettings()
    }

    fun setVoiceType(type: VoiceType) {
        _uiState.value = _uiState.value.copy(voiceType = type)
        applyVoiceSettings()
    }

    fun toggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }

    fun copyToClipboard(text: String) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("NEXA PRO", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Copiado ✓", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun exportToPdf(message: Message) {
        val context = getApplication<Application>()

        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()

            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("NEXA PRO — Chat Export", 50f, 50f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false

            val lines = message.content.split("\n")
            var y = 100f
            for (line in lines) {
                // Simple text wrapping
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > 495f) {
                        if (y > 800f) break
                        canvas.drawText(currentLine, 50f, y, paint)
                        y += 18f
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty() && y <= 800f) {
                    canvas.drawText(currentLine, 50f, y, paint)
                    y += 18f
                }
                y += 4f // line spacing
            }

            pdfDocument.finishPage(page)

            val file = java.io.File(context.cacheDir, "nexa_export_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(java.io.FileOutputStream(file))
            pdfDocument.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val shareIntent = Intent.createChooser(intent, "Exportar PDF")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)

        } catch (e: Exception) {
            android.util.Log.e("NEXA", "PDF Error: ${e.message}")
            android.widget.Toast.makeText(context, "Error al generar PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
