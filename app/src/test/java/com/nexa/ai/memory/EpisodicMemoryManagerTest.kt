package com.nexa.ai.memory

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EpisodicMemoryManager — tests storage, querying, and eviction.
 */
class EpisodicMemoryManagerTest {

    private lateinit var manager: EpisodicMemoryManager

    @Before
    fun setUp() {
        manager = EpisodicMemoryManager()
    }

    // ─── Consent Tests ───────────────────────────────────────

    @Test
    fun `Memory disabled by default`() {
        assertFalse(manager.hasConsent())
    }

    @Test
    fun `Enable memory sets consent`() {
        manager.setConsent(true)
        assertTrue(manager.hasConsent())
    }

    @Test
    fun `Disable memory revokes consent`() {
        manager.setConsent(true)
        manager.setConsent(false)
        assertFalse(manager.hasConsent())
    }

    // ─── Storage Tests ───────────────────────────────────────

    @Test
    fun `Store memory without consent returns entry but does not store`() {
        val entry = manager.storeMemory(
            sessionId = "s1",
            type = MemoryType.FACT,
            content = "User likes pizza",
            summary = "Likes pizza"
        )
        assertNotNull(entry)
        assertEquals("s1", entry.sessionId)
        assertEquals(0, manager.getStats().totalMemories)
    }

    @Test
    fun `Store memory with consent stores successfully`() {
        manager.setConsent(true)
        val entry = manager.storeMemory(
            sessionId = "s1",
            type = MemoryType.FACT,
            content = "User likes pizza",
            summary = "Likes pizza",
            importance = 0.8f
        )
        assertNotNull(entry)
        assertEquals(1, manager.getStats().totalMemories)
    }

    @Test
    fun `Store multiple memories`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Lives in Madrid", "Lives in Madrid", importance = 0.9f)
        manager.storeMemory("s1", MemoryType.PREFERENCE, "Prefers dark mode", "Dark mode preference")
        manager.storeMemory("s2", MemoryType.EVENT, "Discussed AI", "AI discussion")

        assertEquals(3, manager.getStats().totalMemories)
    }

    // ─── Query Tests ─────────────────────────────────────────

    @Test
    fun `Query memories by keyword`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "User loves Android development", "Android dev lover", importance = 0.8f)
        manager.storeMemory("s1", MemoryType.FACT, "User has a cat named Luna", "Cat named Luna", importance = 0.6f)

        val results = manager.queryMemories(MemoryQuery(keywords = listOf("android")))
        assertEquals(1, results.size)
        assertEquals("Android dev lover", results[0].summary)
    }

    @Test
    fun `Query memories by type`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Name is Juan", "Name: Juan", importance = 0.9f)
        manager.storeMemory("s1", MemoryType.PREFERENCE, "Likes Spanish", "Likes Spanish")
        manager.storeMemory("s1", MemoryType.FACT, "Works at Google", "Works at Google", importance = 0.8f)

        val facts = manager.queryMemories(MemoryQuery(type = MemoryType.FACT))
        assertEquals(2, facts.size)
    }

    @Test
    fun `Query memories by session`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.EVENT, "Event 1", "E1", importance = 0.5f)
        manager.storeMemory("s2", MemoryType.EVENT, "Event 2", "E2", importance = 0.5f)

        val session1 = manager.queryMemories(MemoryQuery(sessionId = "s1"))
        assertEquals(1, session1.size)
        assertEquals("s1", session1[0].sessionId)
    }

    @Test
    fun `Query memories by minimum importance`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Important", "High importance", importance = 0.9f)
        manager.storeMemory("s1", MemoryType.FACT, "Not important", "Low importance", importance = 0.2f)

        val important = manager.queryMemories(MemoryQuery(minImportance = 0.5f))
        assertEquals(1, important.size)
        assertEquals("High importance", important[0].summary)
    }

    @Test
    fun `Find relevant memories for text`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.PREFERENCE, "User prefers Python programming", "Python preference", importance = 0.7f)
        manager.storeMemory("s1", MemoryType.FACT, "User has 2 dogs", "Has 2 dogs", importance = 0.5f)

        val relevant = manager.findRelevantMemories("I want to learn Python")
        assertTrue(relevant.any { it.summary.contains("Python") })
    }

    // ─── Context Generation Tests ────────────────────────────

    @Test
    fun `Get context for session returns formatted string`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Likes pizza", "Likes pizza", importance = 0.7f)
        manager.storeMemory("s1", MemoryType.PREFERENCE, "Prefers Spanish", "Spanish preference", importance = 0.6f)

        val context = manager.getContextForSession("s1", "en")
        assertTrue(context.contains("Session Memory"))
        assertTrue(context.contains("Likes pizza"))
    }

    @Test
    fun `Get context for non-existent session returns empty`() {
        manager.setConsent(true)
        val context = manager.getContextForSession("nonexistent", "en")
        assertEquals("", context)
    }

    @Test
    fun `Get persistent memories returns cross-session facts`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Name is Ana", "Name: Ana", importance = 0.9f)
        manager.storeMemory("s2", MemoryType.PREFERENCE, "Likes tea", "Tea lover", importance = 0.7f)

        val persistent = manager.getPersistentMemories("en")
        assertTrue(persistent.contains("Name: Ana"))
    }

    // ─── Delete Tests ────────────────────────────────────────

    @Test
    fun `Delete specific memory`() {
        manager.setConsent(true)
        val entry = manager.storeMemory("s1", MemoryType.FACT, "Test", "Test memory", importance = 0.5f)
        assertEquals(1, manager.getStats().totalMemories)

        manager.deleteMemory(entry.id)
        assertEquals(0, manager.getStats().totalMemories)
    }

    @Test
    fun `Delete session memories`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "Session 1 fact", "S1 fact")
        manager.storeMemory("s2", MemoryType.FACT, "Session 2 fact", "S2 fact")

        manager.deleteSessionMemories("s1")
        assertEquals(1, manager.getStats().totalMemories)
    }

    @Test
    fun `Clear all memories`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "A", "A")
        manager.storeMemory("s2", MemoryType.FACT, "B", "B")

        manager.clearAllMemories()
        assertEquals(0, manager.getStats().totalMemories)
    }

    // ─── Stats Tests ─────────────────────────────────────────

    @Test
    fun `Stats reflect stored memories`() {
        manager.setConsent(true)
        manager.storeMemory("s1", MemoryType.FACT, "A", "A", importance = 0.9f)
        manager.storeMemory("s1", MemoryType.PREFERENCE, "B", "B", importance = 0.5f)

        val stats = manager.getStats()
        assertEquals(2, stats.totalMemories)
        assertEquals(1, stats.byType[MemoryType.FACT])
        assertEquals(1, stats.byType[MemoryType.PREFERENCE])
        assertEquals(1, stats.totalSessions)
        assertTrue(stats.newestMemory > 0)
        assertTrue(stats.oldestMemory > 0)
    }

    @Test
    fun `Empty stats for no memories`() {
        val stats = manager.getStats()
        assertEquals(0, stats.totalMemories)
        assertTrue(stats.byType.isEmpty())
    }

    // ─── Keyword Extraction Tests ────────────────────────────

    @Test
    fun `Keywords extracted from bilingual content`() {
        manager.setConsent(true)
        // The keyword extraction should work for both English and Spanish
        manager.storeMemory("s1", MemoryType.CONTEXT, "The user enjoys programming in Kotlin", "Kotlin programming")
        manager.storeMemory("s1", MemoryType.CONTEXT, "Al usuario le gusta programar en Android", "Android programming")

        val relevant = manager.findRelevantMemories("Kotlin Android programming")
        assertTrue(relevant.size >= 1)
    }

    // ─── Memory Type Enum Tests ──────────────────────────────

    @Test
    fun `MemoryType has all expected values`() {
        val types = MemoryType.entries
        assertEquals(8, types.size)
        assertTrue(types.contains(MemoryType.FACT))
        assertTrue(types.contains(MemoryType.PREFERENCE))
        assertTrue(types.contains(MemoryType.EVENT))
        assertTrue(types.contains(MemoryType.CONTEXT))
        assertTrue(types.contains(MemoryType.DECISION))
        assertTrue(types.contains(MemoryType.REMINDER))
        assertTrue(types.contains(MemoryType.PERSONAL))
        assertTrue(types.contains(MemoryType.SKILL_LEARNED))
    }
}
