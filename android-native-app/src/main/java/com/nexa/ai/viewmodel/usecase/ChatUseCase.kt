package com.nexa.ai.viewmodel.usecase

import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.StreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChatUseCase — Handles AI chat interaction logic, extracted from NexaViewModel.
 *
 * Manages:
 * - Sending messages to the AI API via NexaRepository
 * - Tracking loading state and errors
 * - Accumulating streaming responses
 * - Exposing chat state as a reactive StateFlow
 *
 * The ViewModel observes [state] and syncs with its own UI state.
 */
@Singleton
class ChatUseCase @Inject constructor(
    private val repository: NexaRepository
) {

    data class ChatMessageEntry(
        val user: String,
        val ai: String
    )

    data class ChatState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val messages: List<ChatMessageEntry> = emptyList(),
        val currentStreamingText: String = ""
    )

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Configuration — set by ViewModel before sending
    var baseUrl: String = "https://nexa-ai-server.vercel.app"
    var systemPrompt: String = ""
    var groqApiKey: String? = null
    var useLocalLLM: Boolean = false
    var maxTokens: Int = 4096

    // Message history for API context
    private val messageHistory = mutableListOf<ChatMessage>()

    /**
     * Send a user message to the AI and process the streaming response.
     *
     * @param text The user's message content
     * @param viewModelScope The ViewModel's coroutine scope for collecting the flow
     */
    fun sendMessage(text: String, viewModelScope: CoroutineScope) {
        // Add user message to history
        messageHistory.add(ChatMessage("user", text))

        _state.update { it.copy(isLoading = true, error = null, currentStreamingText = "") }

        viewModelScope.launch {
            try {
                val messages = buildMessageList()
                val url = determineBaseUrl()

                repository.sendMessage(
                    messages = messages,
                    baseUrl = url,
                    provider = if (groqApiKey.isNullOrBlank()) null else "groq",
                    language = null,
                    systemPrompt = systemPrompt.ifBlank { null }
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Text -> {
                            _state.update { current ->
                                current.copy(currentStreamingText = current.currentStreamingText + event.text)
                            }
                        }
                        is StreamEvent.Done -> {
                            val finalText = _state.value.currentStreamingText
                            if (finalText.isNotBlank()) {
                                messageHistory.add(ChatMessage("assistant", finalText))
                                _state.update { current ->
                                    current.copy(
                                        isLoading = false,
                                        currentStreamingText = "",
                                        messages = current.messages + ChatMessageEntry(
                                            user = text,
                                            ai = finalText
                                        )
                                    )
                                }
                            } else {
                                _state.update { it.copy(isLoading = false, currentStreamingText = "") }
                            }
                        }
                        is StreamEvent.Error -> {
                            _state.update { it.copy(isLoading = false, error = event.message) }
                        }
                        is StreamEvent.AuthExpired -> {
                            _state.update { it.copy(isLoading = false, error = "auth_expired") }
                        }
                        is StreamEvent.Provider -> {
                            // Provider info, can be used for debugging
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
                android.util.Log.e("ChatUseCase", "Error sending message: ${e.message}", e)
            }
        }
    }

    /**
     * Clear chat history and reset state.
     */
    fun clearChat() {
        messageHistory.clear()
        _state.update { ChatState() }
    }

    /**
     * Build the message list for the API request, including system prompt.
     */
    private fun buildMessageList(): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        // System prompt is handled by NexaRepository via the systemPrompt parameter
        messages.addAll(messageHistory)
        return messages
    }

    /**
     * Determine the base URL for the API request.
     * Uses Groq API if an API key is set, otherwise the default server.
     */
    private fun determineBaseUrl(): String {
        return if (!groqApiKey.isNullOrBlank()) {
            "https://api.groq.com/openai/v1"
        } else {
            baseUrl
        }
    }

    /**
     * Add a message to the history without triggering an API call.
     * Used for restoring state or injecting system messages.
     */
    fun addMessageToHistory(role: String, content: String) {
        messageHistory.add(ChatMessage(role, content))
    }

    /**
     * Get the current message history size.
     */
    fun historySize(): Int = messageHistory.size
}
