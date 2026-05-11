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

enum class VoiceType { MALE, FEMALE }
enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English")
}

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
    val voiceType: VoiceType = VoiceType.FEMALE,
    val isDarkTheme: Boolean = true,
    val drawerOpen: Boolean = false,
    val showSettings: Boolean = false
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
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        initTTS()
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

        // Try to pick male/female voice
        val voices = tts?.voices ?: return
        val targetGender = if (_uiState.value.voiceType == VoiceType.MALE) "male" else "female"
        val localeStr = locale.language

        val bestVoice = voices.find { voice ->
            voice.locale.language == localeStr &&
            voice.name.lowercase().contains(targetGender) &&
            !voice.isNetworkConnectionRequired
        } ?: voices.find { voice ->
            voice.locale.language == localeStr && !voice.isNetworkConnectionRequired
        }

        bestVoice?.let { tts?.voice = it }
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

        // Update session title from first user message
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
        // Update speech recognizer language
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.code)
        }
    }

    fun setVoiceType(type: VoiceType) {
        _uiState.value = _uiState.value.copy(voiceType = type)
        applyVoiceSettings()
    }

    fun toggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
