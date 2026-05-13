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
import com.nexa.ai.ui.NexaStrings
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
    val isStreaming: Boolean = false,
    val attachmentName: String? = null
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class VoiceType { MALE_1, MALE_2, MALE_3, FEMALE_1, FEMALE_2, FEMALE_3 }
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
    val showUpdateDialog: Boolean = false,
    // Attachment
    val pendingAttachment: String? = null,
    // Drawer view (0=history, 1=settings)
    val drawerView: Int = 0
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

    private var lastSendTimestamp = 0L
    private val sendCooldownMs = 1500L

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
        initTTS()
        restoreState()
    }

    // ═══════════════════════════════════════
    //  STATE PERSISTENCE
    // ═══════════════════════════════════════

    private fun restoreState() {
        viewModelScope.launch {
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

        _uiState.value = _uiState.value.copy(
            updateInfo = info.copy(changelog = info.changelog),
            showUpdateDialog = false
        )

        // Open GitHub releases page in browser
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
            _uiState.value = _uiState.value.copy(loginError = NexaStrings.get("fill_all", _uiState.value.language))
            return
        }

        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(loginError = NexaStrings.get("invalid_email", _uiState.value.language))
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
            _uiState.value = _uiState.value.copy(registerError = NexaStrings.get("fill_all", _uiState.value.language))
            return
        }

        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(registerError = NexaStrings.get("invalid_email", _uiState.value.language))
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(registerError = NexaStrings.get("min_chars", _uiState.value.language))
            return
        }

        if (password != confirm) {
            _uiState.value = _uiState.value.copy(registerError = NexaStrings.get("passwords_no_match", _uiState.value.language))
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
                        registerError = NexaStrings.get("email_taken", _uiState.value.language),
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

        val allVoices = tts?.voices?.filter { it.locale.language == locale.language } ?: return
        if (allVoices.isEmpty()) return

        val voiceType = _uiState.value.voiceType
        val isSpanish = _uiState.value.language == AppLanguage.SPANISH
        
        // Voice names per locale
        val voiceName = if (isSpanish) {
            when (voiceType) {
                VoiceType.FEMALE_1 -> "es-es-x-eea-local"
                VoiceType.FEMALE_2 -> "es-es-x-eec-local"
                VoiceType.FEMALE_3 -> "es-us-x-esc-local"
                VoiceType.MALE_1   -> "es-es-x-eed-local"
                VoiceType.MALE_2   -> "es-es-x-eee-local"
                VoiceType.MALE_3   -> "es-us-x-esd-local"
            }
        } else {
            when (voiceType) {
                VoiceType.FEMALE_1 -> "en-us-x-tpf-local"
                VoiceType.FEMALE_2 -> "en-us-x-tpd-local"
                VoiceType.FEMALE_3 -> "en-gb-x-gba-local"
                VoiceType.MALE_1   -> "en-us-x-tpc-local"
                VoiceType.MALE_2   -> "en-us-x-tpa-local"
                VoiceType.MALE_3   -> "en-gb-x-gbb-local"
            }
        }

        // Strategy: exact match → gender keyword match → first available
        val isMale = voiceType == VoiceType.MALE_1 || voiceType == VoiceType.MALE_2 || voiceType == VoiceType.MALE_3
        val selectedVoice = allVoices.find { it.name == voiceName }
            ?: run {
                val genderKeywords = if (isMale) listOf("male", "man", "hom") else listOf("female", "woman", "fem")
                allVoices.find { v -> genderKeywords.any { v.name.lowercase().contains(it) } }
            }
            ?: allVoices.first()

        tts?.voice = selectedVoice
        
        val pitch = when (voiceType) {
            VoiceType.FEMALE_1 -> 1.1f
            VoiceType.FEMALE_2 -> 1.0f
            VoiceType.FEMALE_3 -> 0.9f
            VoiceType.MALE_1   -> 0.8f
            VoiceType.MALE_2   -> 1.0f
            VoiceType.MALE_3   -> 1.2f
        }
        tts?.setPitch(pitch)
        tts?.setSpeechRate(1.0f)
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
        var cleaned = text
            // URLs
            .replace(Regex("https?://\\S+"), "")
            // Markdown: headers, bold, italic
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\*{1,3}(.+?)\\*{1,3}"), "$1")
            .replace(Regex("_{1,3}(.+?)_{1,3}"), "$1")
            // Stray asterisks (after markdown cleanup)
            .replace(Regex("\\*+"), "")
            // Code blocks → just the word "código"
            .replace(Regex("```[\\s\\S]*?```"), "código")
            .replace(Regex("`([^`]+)`"), "$1")
            // Links: [text](url) → text
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("!\\[[^]]*]\\([^)]+\\)"), "")
            // Newlines → pause
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), ". ")

        // Remove ALL symbols, keep only: letters (incl. accented), numbers, spaces, and basic punctuation (. , ; : ! ? ¿ ¡)
        cleaned = cleaned.replace(Regex("[^\\p{L}\\p{N}\\s.,;:!?¿¡]"), "")

        // Clean up extra spaces
        cleaned = cleaned
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s*([.,;:!?])\\s*"), "$1 ")
            .trim()

        return cleaned
    }

    // ═══════════════════════════════════════
    //  SPEECH RECOGNITION
    // ═══════════════════════════════════════

    fun startListening() {
        val context = getApplication<Application>()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.value = _uiState.value.copy(error = NexaStrings.get("voice_unavailable", _uiState.value.language))
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
                        else -> _uiState.value = _uiState.value.copy(error = "${NexaStrings.get("voice_error", _uiState.value.language)}: $error")
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
    //  SURPRISE ME
    // ═══════════════════════════════════════

    fun surpriseMe() {
        val prompts = when (_uiState.value.language) {
            AppLanguage.SPANISH -> surprisePromptsEs
            AppLanguage.ENGLISH -> surprisePromptsEn
        }
        val prompt = prompts.random()
        sendMessage(prompt)
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

    fun setDrawerView(view: Int) {
        _uiState.value = _uiState.value.copy(drawerView = view)
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
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
            }
            var pageNum = 1
            var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Header
            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.parseColor("#00E5A0")
            canvas.drawText("NEXA PRO", 50f, 45f, paint)

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.GRAY
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            canvas.drawText(dateStr, 50f, 62f, paint)

            // Divider line
            paint.color = android.graphics.Color.parseColor("#00E5A0")
            paint.strokeWidth = 1f
            canvas.drawLine(50f, 72f, 545f, 72f, paint)

            // Content
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.BLACK

            val lines = content.split("\n")
            var y = 95f
            for (line in lines) {
                if (y > 790f) {
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    paint.textSize = 12f
                    paint.isFakeBoldText = false
                    paint.color = android.graphics.Color.BLACK
                    y = 50f
                }
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > 495f) {
                        if (y > 790f) {
                            pdfDocument.finishPage(page)
                            pageNum++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            paint.textSize = 12f
                            paint.isFakeBoldText = false
                            paint.color = android.graphics.Color.BLACK
                            y = 50f
                        }
                        canvas.drawText(currentLine, 50f, y, paint)
                        y += 18f
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    if (y > 790f) {
                        pdfDocument.finishPage(page)
                        pageNum++
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        paint.textSize = 12f
                        paint.isFakeBoldText = false
                        paint.color = android.graphics.Color.BLACK
                        y = 50f
                    }
                    canvas.drawText(currentLine, 50f, y, paint)
                    y += 18f
                }
                y += 4f
            }

            // Footer
            paint.textSize = 8f
            paint.color = android.graphics.Color.LTGRAY
            canvas.drawText(NexaStrings.get("generated_by", _uiState.value.language), 50f, 820f, paint)

            pdfDocument.finishPage(page)

            val fileName = "nexa_export_${System.currentTimeMillis()}.pdf"
            val file = java.io.File(context.cacheDir, fileName)
            java.io.FileOutputStream(file).use { fos ->
                pdfDocument.writeTo(fos)
            }
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

            val shareIntent = Intent.createChooser(intent, NexaStrings.get("export_pdf_title", _uiState.value.language))
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)

        } catch (e: Exception) {
            android.util.Log.e("NEXA", "PDF Error: ${e.message}", e)
            android.widget.Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
