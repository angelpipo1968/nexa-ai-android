package com.nexa.ai.domain.usecase

import com.nexa.ai.data.NexaRepository
import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.StreamEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatUseCase @Inject constructor(
    private val repository: NexaRepository
) {
    fun sendMessage(messages: List<ChatMessage>, baseUrl: String, language: String): Flow<StreamEvent> {
        return repository.sendMessage(messages, baseUrl, language)
    }
}
