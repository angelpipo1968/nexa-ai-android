package com.nexa.ai.tutorial.data

data class Message(
    val role: String, // "user" o "assistant"
    val content: String
)

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>
)

data class Choice(
    val message: Message,
    val finish_reason: String
)
