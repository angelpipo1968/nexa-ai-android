package com.nexa.ai.memory

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for EpisodicMemoryManager.
 * Tests memory storage, retrieval, search, auto-extraction, and user profile management.
 */
class EpisodicMemoryManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPrefs: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var memoryManager: EpisodicMemoryManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.getSharedPreferences("nexa_episodic_memory", Context.MODE_PRIVATE))
            .thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockPrefs.getString(anyString(), isNull())).thenReturn(null)
        
        memoryManager = EpisodicMemoryManager(mockContext)
    }

    @Test
    fun `store and retrieve memory`() {
        val entry = EpisodicMemoryManager.MemoryEntry(
            content = "El usuario se llama Juan",
            category = EpisodicMemoryManager.MemoryCategory.PERSONAL,
            importance = 0.9f
        )
        memoryManager.storeMemory(entry)
        
        // Verify the memory was stored (SharedPreferences.put was called)
        verify(mockEditor).putString(eq("episodic_memories"), anyString())
    }

    @Test
    fun `extract name from Spanish message`() {
        val memories = memoryManager.extractMemoriesFromMessage(
            role = "user",
            content = "Me llamo Carlos",
            sessionId = "test-session"
        )
        
        assertTrue("Should extract name memory", memories.any { 
            it.category == EpisodicMemoryManager.MemoryCategory.PERSONAL &&
            it.content.contains("Carlos")
        })
    }

    @Test
    fun `extract name from English message`() {
        val memories = memoryManager.extractMemoriesFromMessage(
            role = "user",
            content = "My name is Alice",
            sessionId = "test-session"
        )
        
        assertTrue("Should extract name memory", memories.any { 
            it.category == EpisodicMemoryManager.MemoryCategory.PERSONAL &&
            it.content.contains("Alice")
        })
    }

    @Test
    fun `extract preference from Spanish message`() {
        val memories = memoryManager.extractMemoriesFromMessage(
            role = "user",
            content = "Me gusta la música rock",
            sessionId = "test-session"
        )
        
        assertTrue("Should extract preference memory", memories.any { 
            it.category == EpisodicMemoryManager.MemoryCategory.PREFERENCE
        })
    }

    @Test
    fun `extract location from message`() {
        val memories = memoryManager.extractMemoriesFromMessage(
            role = "user",
            content = "Vivo en Madrid",
            sessionId = "test-session"
        )
        
        assertTrue("Should extract location memory", memories.any { 
            it.category == EpisodicMemoryManager.MemoryCategory.LOCATION &&
            it.content.contains("Madrid")
        })
    }

    @Test
    fun `no extraction from assistant messages`() {
        val memories = memoryManager.extractMemoriesFromMessage(
            role = "assistant",
            content = "Me llamo AI Assistant",
            sessionId = "test-session"
        )
        
        assertTrue("Should not extract from assistant messages", memories.isEmpty())
    }

    @Test
    fun `user profile starts empty`() {
        val profile = memoryManager.getUserProfile()
        assertEquals("Name should be empty initially", "", profile.name)
        assertEquals("Location should be empty initially", "", profile.location)
    }

    @Test
    fun `store and retrieve facts`() {
        memoryManager.storeFact("El usuario tiene un perro")
        val facts = memoryManager.getFacts()
        assertTrue("Should contain stored fact", facts.contains("El usuario tiene un perro"))
    }

    @Test
    fun `duplicate facts not stored`() {
        memoryManager.storeFact("El usuario tiene un perro")
        memoryManager.storeFact("El usuario tiene un perro")
        val facts = memoryManager.getFacts()
        assertEquals("Should not duplicate facts", 1, facts.count { it == "El usuario tiene un perro" })
    }
}
