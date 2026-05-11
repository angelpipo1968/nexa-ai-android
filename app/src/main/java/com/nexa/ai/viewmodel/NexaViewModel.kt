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
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.StreamEvent
import com.nexa.ai.data.UpdateChecker
import com.nexa.ai.data.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Simulated user storage (replace with real backend later)
    private val userDatabase = mutableMapOf<String, Pair<String, String>>() // email -> (name, password)

    init {
        initTTS()
        createNewSession()
        checkForUpdates()
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
            _uiState.value = _uiState.value.copy(loginError = "Completa todos los campos")
            return
        }

        if (!email.contains("@")) {
            _uiState.value = _uiState.value.copy(loginError = "Email no válido")
            return
        }

        _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)

        // Simulate network delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)

            val stored = userDatabase[email]
            if (stored != null && stored.second == password) {
                _uiState.value = _uiState.value.copy(
                    user = UserData(email = email, displayName = stored.first, isLoggedIn = true),
                    currentScreen = Screen.CHAT,
                    isLoggingIn = false
                )
            } else if (stored != null) {
                _uiState.value = _uiState.value.copy(
                    loginError = "Contraseña incorrecta",
                    isLoggingIn = false
                )
            } else {
                // Auto-register on first login attempt
                userDatabase[email] = Pair(email.substringBefore("@"), password)
                _uiState.value = _uiState.value.copy(
                    user = UserData(email = email, displayName = email.substringBefore("@"), isLoggedIn = true),
                    currentScreen = Screen.CHAT,
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
            kotlinx.coroutines.delay(800)

            if (userDatabase.containsKey(email)) {
                _uiState.value = _uiState.value.copy(
                    registerError = "Este email ya está registrado",
                    isRegistering = false
                )
            } else {
                userDatabase[email] = Pair(name, password)
                _uiState.value = _uiState.value.copy(
                    user = UserData(email = email, displayName = name, isLoggedIn = true),
                    currentScreen = Screen.CHAT,
                    isRegistering = false
                )
            }
        }
    }

    fun logout() {
        stopSpeaking()
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
        
        // Filter by gender if possible
        val genderVoices = voices.filter { it.name.lowercase().contains(genderTag) }
        
        val candidates = if (genderVoices.isNotEmpty()) genderVoices else voices
        
        // Pick the 1st or 2nd available candidate
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
    //  SESSION MANAGEMENT
    // ═══════════════════════════════════════

    fun createNewSession() {
        val session = ChatSession()
        _uiState.value = _uiState.value.copy(
            sessions = listOf(session) + _uiState.value.sessions,
            activeSessionId = session.id,
            drawerOpen = false
        )
    }

    fun switchSession(sessionId: String) {
        stopSpeaking()
        _uiState.value = _uiState.value.copy(
            activeSessionId = sessionId,
            drawerOpen = false,
            error = null
        )
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
        }
    }

    private fun updateActiveSession(transform: (ChatSession) -> ChatSession) {
        val sessions = _uiState.value.sessions.toMutableList()
        val idx = sessions.indexOfFirst { it.id == _uiState.value.activeSessionId }
        if (idx >= 0) {
            sessions[idx] = transform(sessions[idx])
            _uiState.value = _uiState.value.copy(sessions = sessions)
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
        val clip = android.content.ClipData.newPlainText("Nexa Message", text)
        clipboard.setPrimaryClip(clip)
        
        // Show confirmation toast
        android.widget.Toast.makeText(context, "Copiado al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun exportToPdf(message: Message) {
        val context = getApplication<Application>()
        
        try {
            // Create a PDF document
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
            
            // Draw title
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("NEXA PRO - Chat Export", 50f, 50f, paint)
            
            // Draw content
            paint.textSize = 12f
            paint.isFakeBoldText = false
            
            val lines = message.content.split("\n")
            var y = 100f
            for (line in lines) {
                // Basic text wrapping (simplified for this implementation)
                if (y > 800f) break // Stop if page is full
                canvas.drawText(line.take(80), 50f, y, paint)
                y += 20f
            }
            
            pdfDocument.finishPage(page)
            
            // Save to a temporary file and share
            val file = java.io.File(context.cacheDir, "nexa_message_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(java.io.FileOutputStream(file))
            pdfDocument.close()
            
            // Share the generated PDF
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
            
            val shareIntent = Intent.createChooser(intent, "Guardar PDF")
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
