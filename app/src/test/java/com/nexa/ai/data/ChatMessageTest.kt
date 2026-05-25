package com.nexa.ai.data

import org.junit.Assert.*
import org.junit.Test

class ChatMessageTest {

    @Test
    fun `ChatMessage creation`() {
        val msg = ChatMessage(role = "user", content = "hello")
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun `ChatRequest creation`() {
        val messages = listOf(
            ChatMessage(role = "user", content = "hi"),
            ChatMessage(role = "assistant", content = "hello!")
        )
        val request = ChatRequest(messages = messages)
        assertEquals(2, request.messages.size)
        assertNull(request.provider)
    }

    @Test
    fun `ChatRequest with provider`() {
        val messages = listOf(ChatMessage(role = "user", content = "hi"))
        val request = ChatRequest(messages = messages, provider = "openai")
        assertEquals("openai", request.provider)
    }

    @Test
    fun `StreamEvent Text`() {
        val event = StreamEvent.Text("hello")
        assertEquals("hello", event.text)
    }

    @Test
    fun `StreamEvent Provider`() {
        val event = StreamEvent.Provider("OpenAI")
        assertEquals("OpenAI", event.name)
    }

    @Test
    fun `StreamEvent Error`() {
        val event = StreamEvent.Error("something went wrong")
        assertEquals("something went wrong", event.message)
    }

    @Test
    fun `StreamEvent Done is singleton`() {
        val event1 = StreamEvent.Done
        val event2 = StreamEvent.Done
        assertSame(event1, event2)
    }

    @Test
    fun `StreamEvent AuthExpired is singleton`() {
        val event1 = StreamEvent.AuthExpired
        val event2 = StreamEvent.AuthExpired
        assertSame(event1, event2)
    }

    @Test
    fun `PersistedSession creation`() {
        val session = PersistedSession(
            id = "s1",
            title = "Test",
            messages = listOf(
                PersistedMessage(id = "m1", role = "user", content = "hi"),
                PersistedMessage(id = "m2", role = "assistant", content = "hello")
            ),
            createdAt = 1000L,
            updatedAt = 2000L
        )
        assertEquals("s1", session.id)
        assertEquals("Test", session.title)
        assertEquals(2, session.messages.size)
        assertEquals(1000L, session.createdAt)
        assertEquals(2000L, session.updatedAt)
    }

    @Test
    fun `PersistedMessage creation`() {
        val msg = PersistedMessage(id = "m1", role = "user", content = "hello")
        assertEquals("m1", msg.id)
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun `UpdateInfo creation`() {
        val info = UpdateInfo(
            versionCode = 2,
            versionName = "1.1",
            downloadUrl = "https://example.com/app.apk",
            changelog = "Bug fixes",
            forceUpdate = false
        )
        assertEquals(2, info.versionCode)
        assertEquals("1.1", info.versionName)
        assertEquals("https://example.com/app.apk", info.downloadUrl)
        assertEquals("Bug fixes", info.changelog)
        assertFalse(info.forceUpdate)
    }

    @Test
    fun `UpdateInfo with force update`() {
        val info = UpdateInfo(
            versionCode = 3,
            versionName = "2.0",
            downloadUrl = "https://example.com/app.apk",
            changelog = "Major update",
            forceUpdate = true
        )
        assertTrue(info.forceUpdate)
    }

    @Test
    fun `PersistedUser creation`() {
        val user = PersistedUser(email = "test@test.com", displayName = "Test")
        assertEquals("test@test.com", user.email)
        assertEquals("Test", user.displayName)
    }

    @Test
    fun `PersistedCredential creation with salt`() {
        val cred = PersistedCredential(
            email = "test@test.com",
            name = "Test",
            passwordHash = "abc123",
            salt = "randomsalt"
        )
        assertEquals("test@test.com", cred.email)
        assertEquals("Test", cred.name)
        assertEquals("abc123", cred.passwordHash)
        assertEquals("randomsalt", cred.salt)
    }

    @Test
    fun `PersistedCredential default salt is empty for backward compat`() {
        val cred = PersistedCredential(
            email = "test@test.com",
            name = "Test",
            passwordHash = "abc123"
        )
        assertEquals("", cred.salt)
    }
}
