package com.nexa.ai.ui

import com.nexa.ai.viewmodel.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class NexaStringsTest {

    @Test
    fun `Spanish translations exist for all keys`() {
        val keys = listOf(
            "new_chat", "settings", "language", "voice", "theme", "login", "logout",
            "register", "email", "password", "thinking", "input_hint", "listening",
            "send", "back", "menu", "online", "copied", "fill_all", "invalid_email",
            "min_chars", "passwords_no_match", "email_taken", "session_expired",
            "voice_unavailable", "voice_error", "connection_error", "server_error",
            "rate_limit", "update_available", "update_now", "later", "export_pdf",
            "read_aloud", "clear_chat", "surprise_me"
        )
        for (key in keys) {
            val value = NexaStrings.get(key, AppLanguage.SPANISH)
            assertNotEquals("Key '$key' returned itself for Spanish", key, value)
            assertTrue("Key '$key' should not be blank", value.isNotBlank())
        }
    }

    @Test
    fun `English translations exist for all keys`() {
        val keys = listOf(
            "new_chat", "settings", "language", "voice", "theme", "login", "logout",
            "register", "email", "password", "thinking", "input_hint", "listening",
            "send", "back", "menu", "online", "copied", "fill_all", "invalid_email",
            "min_chars", "passwords_no_match", "email_taken", "session_expired",
            "voice_unavailable", "voice_error", "connection_error", "server_error",
            "rate_limit", "update_available", "update_now", "later", "export_pdf",
            "read_aloud", "clear_chat", "surprise_me"
        )
        for (key in keys) {
            val value = NexaStrings.get(key, AppLanguage.ENGLISH)
            assertNotEquals("Key '$key' returned itself for English", key, value)
            assertTrue("Key '$key' should not be blank", value.isNotBlank())
        }
    }

    @Test
    fun `Unknown key returns itself`() {
        val result = NexaStrings.get("nonexistent_key_xyz", AppLanguage.SPANISH)
        assertEquals("nonexistent_key_xyz", result)
    }

    @Test
    fun `Spanish and English have different values`() {
        val keys = listOf("new_chat", "settings", "login", "email", "password", "thinking")
        for (key in keys) {
            val es = NexaStrings.get(key, AppLanguage.SPANISH)
            val en = NexaStrings.get(key, AppLanguage.ENGLISH)
            assertNotEquals("Key '$key' should differ between languages", es, en)
        }
    }
}
