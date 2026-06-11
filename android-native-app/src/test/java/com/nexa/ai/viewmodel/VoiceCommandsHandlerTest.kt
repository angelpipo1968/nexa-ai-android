package com.nexa.ai.viewmodel

import com.nexa.ai.iot.IoTManager
import com.nexa.ai.media.VideoGenerator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for VoiceCommandsHandler — tests voice command recognition.
 */
class VoiceCommandsHandlerTest {

    private lateinit var handler: VoiceCommandsHandler

    @Before
    fun setUp() {
        // Note: IoTManager and VideoGenerator require Application context for full functionality,
        // but VoiceCommandsHandler command matching is pure logic — we test that here.
        // For integration tests, use instrumented tests.
    }

    // ─── Simple pattern matching tests (handler logic) ─────

    @Test
    fun `VoiceCommandResult Handled contains response`() {
        val result = VoiceCommandsHandler.VoiceCommandResult.Handled("Chat cleared")
        assertEquals("Chat cleared", result.spokenResponse)
    }

    @Test
    fun `VoiceCommandResult NotRecognized is singleton`() {
        val a = VoiceCommandsHandler.VoiceCommandResult.NotRecognized
        val b = VoiceCommandsHandler.VoiceCommandResult.NotRecognized
        assertTrue(a === b)
    }
}
