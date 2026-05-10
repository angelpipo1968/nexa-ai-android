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

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false
)

data class NexaUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val currentProvider: String? = null,
    val error: String? = null,
    val autoSpeak: Boolean = true,
    val language: String = "es"
)

class NexaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NexaUiState())
    val uiState: StateFlow<NexaUiState> = _uiState.asStateFlow()

    private val repository = NexaRepository()
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        initTTS()
    }

    // ═══════════════════════════════════════
    //  TTS — Text to Speech
    // ═══════════════════════════════════════

    private fun initTTS() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val lang = if (_uiState.value.language == "es") Locale("es", "ES") else Locale.US
                tts?.language = lang
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

    fun speak(text: String, messageId: String? = null) {
        if (!ttsReady) return

        // If same message is already speaking → stop
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
                    // Auto-send
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (_uiState.value.language == "es") "es-ES" else "en-US")
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

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isThinking = true,
            error = null
        )

        viewModelScope.launch {
            val allMessages = _uiState.value.messages.map { ChatMessage(it.role, it.content) }
            var fullResponse = ""

            repository.sendMessage(allMessages, BuildConfig.API_BASE_URL).collect { event ->
                when (event) {
                    is StreamEvent.Text -> {
                        fullResponse += event.text
                        val updated = _uiState.value.messages.toMutableList()
                        val idx = updated.indexOfFirst { it.id == assistantId }
                        if (idx >= 0) {
                            updated[idx] = updated[idx].copy(content = fullResponse, isStreaming = true)
                        } else {
                            updated.add(Message(id = assistantId, role = "assistant", content = fullResponse, isStreaming = true))
                        }
                        _uiState.value = _uiState.value.copy(messages = updated, isThinking = false)
                    }
                    is StreamEvent.Provider -> {
                        _uiState.value = _uiState.value.copy(currentProvider = event.name)
                    }
                    is StreamEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = event.message,
                            isThinking = false
                        )
                    }
                    is StreamEvent.Done -> {
                        val updated = _uiState.value.messages.toMutableList()
                        val idx = updated.indexOfFirst { it.id == assistantId }
                        if (idx >= 0) {
                            updated[idx] = updated[idx].copy(isStreaming = false)
                        }
                        _uiState.value = _uiState.value.copy(messages = updated, isThinking = false)

                        // Auto-speak the response
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
        _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
