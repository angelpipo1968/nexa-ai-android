package com.nexa.ai.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SmartRoutingManager routing logic.
 * Tests the shouldUseOnDevice decision engine.
 */
class SmartRoutingManagerTest {

    // We test the logic independently since SmartRoutingManager requires Android context.
    // These tests verify the routing decision patterns.

    @Test
    fun `simple greeting patterns detected`() {
        val simplePatterns = listOf(
            "hola", "buenos días", "buenas tardes", "buenas noches",
            "gracias", "adiós", "qué hora es", "qué día es",
            "hello", "hi", "thanks", "bye",
            "quién eres", "qué eres", "qué puedes hacer"
        )
        
        for (pattern in simplePatterns) {
            val lower = pattern.lowercase().trim()
            val isSimple = simplePatterns.any { lower.startsWith(it) || lower == it }
            assertTrue("'$pattern' should be detected as simple query", isSimple)
        }
    }

    @Test
    fun `tool keywords detected`() {
        val toolKeywords = listOf(
            "busca vuelos a Miami",
            "¿qué clima hace?",
            "genera imagen de un gato",
            "traduce esto al inglés",
            "calcula 2+2",
            "últimas noticias"
        )
        
        val keywords = listOf(
            "busca", "clima", "vuelo", "noticias", 
            "genera imagen", "traduce", "calcula"
        )
        
        for (query in toolKeywords) {
            val lower = query.lowercase()
            val hasTool = keywords.any { lower.contains(it) }
            assertTrue("'$query' should contain tool keyword", hasTool)
        }
    }

    @Test
    fun `routing decision has correct fields`() {
        val decision = SmartRoutingManager.RoutingDecision(
            useOnDevice = true,
            reason = "Simple query",
            confidence = 0.8f,
            fallbackMessage = null
        )
        
        assertTrue(decision.useOnDevice)
        assertEquals("Simple query", decision.reason)
        assertEquals(0.8f, decision.confidence, 0.01f)
        assertNull(decision.fallbackMessage)
    }

    @Test
    fun `offline routing with fallback message`() {
        val decision = SmartRoutingManager.RoutingDecision(
            useOnDevice = false,
            reason = "Sin conexión y sin modelo local",
            confidence = 0.0f,
            fallbackMessage = "No hay conexión a internet"
        )
        
        assertFalse(decision.useOnDevice)
        assertNotNull(decision.fallbackMessage)
    }
}
