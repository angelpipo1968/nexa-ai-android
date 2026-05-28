package com.nexa.ai.viewmodel

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ModelsTest {

    @Test
    fun `Message default values are correct`() {
        val msg = Message(role = "user", content = "hello")
        assertNotNull(msg.id)
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
        assertFalse(msg.isStreaming)
        assertNull(msg.attachmentName)
    }

    @Test
    fun `ChatSession default values are correct`() {
        val session = ChatSession()
        assertNotNull(session.id)
        assertEquals("", session.title)
        assertTrue(session.messages.isEmpty())
        assertTrue(session.createdAt > 0)
        assertTrue(session.updatedAt > 0)
    }

    @Test
    fun `ChatSession with messages`() {
        val messages = listOf(
            Message(role = "user", content = "hi"),
            Message(role = "assistant", content = "hello!")
        )
        val session = ChatSession(title = "Test", messages = messages)
        assertEquals(2, session.messages.size)
        assertEquals("Test", session.title)
    }

    @Test
    fun `NexaUiState activeSession returns correct session`() {
        val session1 = ChatSession(id = "s1", title = "Session 1")
        val session2 = ChatSession(id = "s2", title = "Session 2")
        val state = NexaUiState(
            sessions = listOf(session1, session2),
            activeSessionId = "s2"
        )
        assertEquals(session2, state.activeSession)
        assertEquals("Session 2", state.activeSession?.title)
    }

    @Test
    fun `NexaUiState activeSession returns null when no active`() {
        val state = NexaUiState(sessions = emptyList(), activeSessionId = null)
        assertNull(state.activeSession)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `NexaUiState messages delegates to activeSession`() {
        val messages = listOf(Message(role = "user", content = "test"))
        val session = ChatSession(id = "s1", messages = messages)
        val state = NexaUiState(sessions = listOf(session), activeSessionId = "s1")
        assertEquals(1, state.messages.size)
        assertEquals("test", state.messages[0].content)
    }

    @Test
    fun `AppLanguage codes are correct`() {
        assertEquals("es", AppLanguage.SPANISH.code)
        assertEquals("en", AppLanguage.ENGLISH.code)
    }

    @Test
    fun `VoiceType has all 6 variants`() {
        assertEquals(6, VoiceType.entries.size)
    }

    @Test
    fun `Screen enum has all values`() {
        assertEquals(6, Screen.entries.size)
        assertTrue(Screen.entries.contains(Screen.CHAT))
        assertTrue(Screen.entries.contains(Screen.LOGIN))
        assertTrue(Screen.entries.contains(Screen.REGISTER))
        assertTrue(Screen.entries.contains(Screen.LOTTERY))
        assertTrue(Screen.entries.contains(Screen.SETTINGS))
        assertTrue(Screen.entries.contains(Screen.TRANSLATOR))
    }

    @Test
    fun `UserData defaults`() {
        val user = UserData()
        assertEquals("", user.email)
        assertEquals("", user.displayName)
        assertFalse(user.isLoggedIn)
    }

    @Test
    fun `UserData with values`() {
        val user = UserData(email = "test@test.com", displayName = "Test", isLoggedIn = true)
        assertEquals("test@test.com", user.email)
        assertEquals("Test", user.displayName)
        assertTrue(user.isLoggedIn)
    }

    @Test
    fun `NexaUiState defaults are correct`() {
        val state = NexaUiState()
        assertTrue(state.sessions.isEmpty())
        assertNull(state.activeSessionId)
        assertEquals("", state.inputText)
        assertFalse(state.isListening)
        assertFalse(state.isThinking)
        assertFalse(state.isSpeaking)
        assertNull(state.speakingMessageId)
        assertNull(state.currentProvider)
        assertNull(state.error)
        assertTrue(state.autoSpeak)
        assertEquals(AppLanguage.SPANISH, state.language)
        assertEquals(VoiceType.FEMALE_1, state.voiceType)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertTrue(state.isDark(isSystemDark = false))
        assertFalse(state.drawerOpen)
        assertEquals(Screen.CHAT, state.currentScreen)
        assertFalse(state.user.isLoggedIn)
    }

    @Test
    fun `NexaUiState copy works correctly`() {
        val state = NexaUiState()
        val updated = state.copy(
            inputText = "hello",
            isThinking = true,
            error = "test error"
        )
        assertEquals("hello", updated.inputText)
        assertTrue(updated.isThinking)
        assertEquals("test error", updated.error)
        // Original unchanged
        assertEquals("", state.inputText)
    }
}
