package com.nexa.ai.usecase

import com.nexa.ai.data.ChatMessage
import com.nexa.ai.data.LocationStore
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.Message

/**
 * ChatUseCase — Handles chat message creation, session management, and message formatting.
 * Extracted from NexaViewModel to reduce complexity and improve testability.
 *
 * This use case encapsulates the business logic for creating and formatting
 * chat messages, generating session metadata, and validating message content.
 * By extracting these operations, the ViewModel becomes thinner and these
 * pure functions become trivially unit-testable.
 */
class ChatUseCase {

    /**
     * Create a user message with optional attachment.
     *
     * @param content The text content of the message
     * @param attachmentName Optional filename of an attached file
     * @return A new Message with role "user"
     */
    fun createUserMessage(content: String, attachmentName: String? = null): Message {
        val fullContent = if (attachmentName != null) {
            "📎 $attachmentName\n$content"
        } else {
            content
        }
        return Message(role = "user", content = fullContent, attachmentName = attachmentName)
    }

    /**
     * Create an assistant message placeholder for streaming.
     * Used when starting an AI response that will be populated incrementally.
     *
     * @param id Unique identifier for the assistant message
     * @return A new Message with role "assistant", empty content, and isStreaming = true
     */
    fun createAssistantPlaceholder(id: String): Message {
        return Message(id = id, role = "assistant", content = "", isStreaming = true)
    }

    /**
     * Generate a unique assistant message ID.
     * Uses current timestamp to ensure uniqueness.
     *
     * @return A unique string ID prefixed with "a-"
     */
    fun generateAssistantId(): String = "a-${System.currentTimeMillis()}"

    /**
     * Generate a session title from the first message.
     * Truncates to 30 characters with ellipsis if longer.
     *
     * @param firstMessage The first message content in the session
     * @return A truncated title string
     */
    fun generateTitle(firstMessage: String): String {
        return firstMessage.take(30) + if (firstMessage.length > 30) "..." else ""
    }

    /**
     * Format messages for API request.
     * Converts from UI Message model to API ChatMessage model,
     * stripping UI-specific fields like isStreaming and attachmentName.
     *
     * @param messages List of UI Message objects
     * @return List of API-ready ChatMessage objects
     */
    fun formatForApi(messages: List<Message>): List<ChatMessage> {
        return messages.map { ChatMessage(it.role, it.content) }
    }

    /**
     * Build location parameters for API request.
     * Only includes coordinates and location metadata when location is available.
     *
     * @param location The current location data
     * @return LocationParams with available data, or empty params if location unavailable
     */
    fun buildLocationParams(location: LocationStore.LocationData): LocationParams {
        return if (location.isAvailable) {
            LocationParams(
                latitude = location.latitude,
                longitude = location.longitude,
                city = location.city.ifBlank { null },
                country = location.country.ifBlank { null }
            )
        } else {
            LocationParams()
        }
    }

    /**
     * Check if message content is valid for sending.
     * A message is valid if it has non-blank text content or an attachment.
     *
     * @param content The text content to validate
     * @param hasAttachment Whether an attachment is present
     * @return true if the message can be sent
     */
    fun isValidMessage(content: String, hasAttachment: Boolean): Boolean {
        return content.isNotBlank() || hasAttachment
    }

    /**
     * Check if send cooldown has passed.
     * Prevents rapid/accidental message sends by enforcing a minimum interval.
     *
     * @param lastTimestamp Timestamp of the last sent message (epoch millis)
     * @param cooldownMs Minimum interval between sends in milliseconds
     * @return true if enough time has passed since the last send
     */
    fun isSendCooldownPassed(lastTimestamp: Long, cooldownMs: Long = 1500L): Boolean {
        return System.currentTimeMillis() - lastTimestamp >= cooldownMs
    }

    /**
     * Location parameters for API requests.
     * Only includes non-null values when location data is available.
     */
    data class LocationParams(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val city: String? = null,
        val country: String? = null
    )
}
