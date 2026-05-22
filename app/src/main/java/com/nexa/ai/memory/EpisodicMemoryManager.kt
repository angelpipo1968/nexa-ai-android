package com.nexa.ai.memory

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * EpisodicMemoryManager — Stores and retrieves conversation memories across sessions.
 * Implements "episodic memory" to retain context between sessions with user consent.
 */

data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MemoryType,
    val content: String,
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val importance: Float = 0.5f,
    val emotion: String = "neutral",
    var accessCount: Int = 0,
    var lastAccessed: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L // 0 = never expires
)

enum class MemoryType {
    FACT,          // User stated a fact (name, preference, etc.)
    PREFERENCE,    // User expressed a preference
    EVENT,         // Something that happened in conversation
    CONTEXT,       // Conversation context (topic, mood, etc.)
    DECISION,      // A decision made during conversation
    REMINDER,      // User asked to remember something
    PERSONAL,      // Personal information shared
    SKILL_LEARNED  // Something the AI learned about the user
}

data class MemoryQuery(
    val keywords: List<String> = emptyList(),
    val type: MemoryType? = null,
    val sessionId: String? = null,
    val minImportance: Float = 0f,
    val limit: Int = 10,
    val emotion: String? = null,
    val sinceTimestamp: Long = 0L
)

data class MemoryStats(
    val totalMemories: Int,
    val byType: Map<MemoryType, Int>,
    val averageImportance: Float,
    val oldestMemory: Long,
    val newestMemory: Long,
    val totalSessions: Int
)

class EpisodicMemoryManager {

    companion object {
        private const val TAG = "EpisodicMemory"
        private const val MAX_MEMORIES = 500
        private const val AUTO_SUMMARY_THRESHOLD = 50
    }

    private val memories = mutableListOf<MemoryEntry>()
    private val sessionMemories = mutableMapOf<String, MutableList<String>>() // sessionId -> memoryIds
    private var isConsentGiven = false

    fun setConsent(consent: Boolean) {
        isConsentGiven = consent
        if (!consent) {
            Log.i(TAG, "Memory consent revoked. Memories preserved but not actively stored.")
        }
    }

    fun hasConsent(): Boolean = isConsentGiven

    /**
     * Store a new memory entry.
     */
    fun storeMemory(
        sessionId: String,
        type: MemoryType,
        content: String,
        summary: String = "",
        keywords: List<String> = emptyList(),
        importance: Float = 0.5f,
        emotion: String = "neutral",
        expiresAt: Long = 0L
    ): MemoryEntry {
        if (!isConsentGiven) {
            Log.d(TAG, "Memory storage skipped: no consent")
            return MemoryEntry(sessionId = sessionId, type = type, content = content)
        }

        // Extract keywords from content if not provided
        val extractedKeywords = if (keywords.isEmpty()) extractKeywords(content) else keywords
        
        val entry = MemoryEntry(
            sessionId = sessionId,
            type = type,
            content = content,
            summary = if (summary.isBlank()) content.take(100) else summary,
            keywords = extractedKeywords,
            importance = importance.coerceIn(0f, 1f),
            emotion = emotion,
            expiresAt = expiresAt
        )

        memories.add(entry)
        sessionMemories.getOrPut(sessionId) { mutableListOf() }.add(entry.id)

        // Evict old/low-importance memories if over limit
        if (memories.size > MAX_MEMORIES) {
            evictMemories()
        }

        // Auto-summarize when threshold reached
        if (memories.size % AUTO_SUMMARY_THRESHOLD == 0) {
            summarizeMemories()
        }

        Log.d(TAG, "Stored memory: [${type.name}] $summary (importance: $importance)")
        return entry
    }

    /**
     * Query memories based on various criteria.
     */
    fun queryMemories(query: MemoryQuery): List<MemoryEntry> {
        val now = System.currentTimeMillis()
        return memories.filter { memory ->
            // Check expiration
            if (memory.expiresAt in 1..now) return@filter false

            // Check type filter
            if (query.type != null && memory.type != query.type) return@filter false

            // Check session filter
            if (query.sessionId != null && memory.sessionId != query.sessionId) return@filter false

            // Check importance threshold
            if (memory.importance < query.minImportance) return@filter false

            // Check emotion filter
            if (query.emotion != null && memory.emotion != query.emotion) return@filter false

            // Check time filter
            if (query.sinceTimestamp > 0 && memory.timestamp < query.sinceTimestamp) return@filter false

            // Check keyword relevance
            if (query.keywords.isNotEmpty()) {
                val memoryText = (memory.content + " " + memory.summary + " " + memory.keywords.joinToString(" ")).lowercase()
                val matchCount = query.keywords.count { keyword -> keyword.lowercase() in memoryText }
                if (matchCount == 0) return@filter false
            }

            true
        }
        .sortedByDescending { it.importance }
        .take(query.limit)
        .onEach { it.accessCount++; it.lastAccessed = now }
    }

    /**
     * Find relevant memories for a given text/input.
     */
    fun findRelevantMemories(text: String, limit: Int = 5): List<MemoryEntry> {
        val keywords = extractKeywords(text)
        return queryMemories(MemoryQuery(
            keywords = keywords,
            minImportance = 0.3f,
            limit = limit
        ))
    }

    /**
     * Get context string for AI prompt based on current session.
     */
    fun getContextForSession(sessionId: String, language: String = "en"): String {
        val sessionEntries = queryMemories(MemoryQuery(
            sessionId = sessionId,
            minImportance = 0.4f,
            limit = 10
        ))

        if (sessionEntries.isEmpty()) return ""

        val isEs = language == "es"
        return buildString {
            append(if (isEs) "## Memoria de sesión:\n" else "## Session Memory:\n")
            sessionEntries.forEach { entry ->
                val typeLabel = when (entry.type) {
                    MemoryType.FACT -> if (isEs) "Dato" else "Fact"
                    MemoryType.PREFERENCE -> if (isEs) "Preferencia" else "Preference"
                    MemoryType.EVENT -> if (isEs) "Evento" else "Event"
                    MemoryType.CONTEXT -> if (isEs) "Contexto" else "Context"
                    MemoryType.DECISION -> if (isEs) "Decisión" else "Decision"
                    MemoryType.REMINDER -> if (isEs) "Recordatorio" else "Reminder"
                    MemoryType.PERSONAL -> if (isEs) "Personal" else "Personal"
                    MemoryType.SKILL_LEARNED -> if (isEs) "Aprendido" else "Learned"
                }
                append("• [$typeLabel] ${entry.summary}\n")
            }
        }
    }

    /**
     * Get all persistent facts and preferences (cross-session).
     */
    fun getPersistentMemories(language: String = "en"): String {
        val persistent = queryMemoriesByTypes(
            types = listOf(MemoryType.FACT, MemoryType.PREFERENCE, MemoryType.PERSONAL, MemoryType.SKILL_LEARNED),
            minImportance = 0.6f,
            limit = 20
        )

        if (persistent.isEmpty()) return ""

        val isEs = language == "es"
        return buildString {
            append(if (isEs) "\n## Información recordada del usuario:\n" else "\n## Remembered User Information:\n")
            persistent.forEach { entry ->
                append("• ${entry.summary}\n")
            }
        }
    }

    /**
     * Delete a specific memory.
     */
    fun deleteMemory(memoryId: String) {
        memories.removeAll { it.id == memoryId }
        sessionMemories.values.forEach { it.remove(memoryId) }
    }

    /**
     * Delete all memories for a session.
     */
    fun deleteSessionMemories(sessionId: String) {
        val ids = sessionMemories.remove(sessionId) ?: return
        memories.removeAll { it.id in ids }
    }

    /**
     * Clear all memories.
     */
    fun clearAllMemories() {
        memories.clear()
        sessionMemories.clear()
        Log.i(TAG, "All memories cleared")
    }

    /**
     * Get memory statistics.
     */
    fun getStats(): MemoryStats {
        val byType = memories.groupingBy { it.type }.eachCount()
        return MemoryStats(
            totalMemories = memories.size,
            byType = byType,
            averageImportance = if (memories.isNotEmpty()) memories.map { it.importance }.average().toFloat() else 0f,
            oldestMemory = memories.minOfOrNull { it.timestamp } ?: 0L,
            newestMemory = memories.maxOfOrNull { it.timestamp } ?: 0L,
            totalSessions = sessionMemories.size
        )
    }

    // ─── Private helpers ──────────────────────────────────────

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf("the", "a", "an", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "will", "would", "could", "should", "may", "might", "shall", "can", "need", "dare",
            "ought", "used", "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into", "through",
            "during", "before", "after", "above", "below", "between", "out", "off", "over", "under", "again",
            "further", "then", "once", "and", "but", "or", "nor", "not", "so", "yet", "both", "either",
            "neither", "each", "every", "all", "any", "few", "more", "most", "other", "some", "such",
            "no", "only", "own", "same", "than", "too", "very", "just", "because", "if", "when", "where",
            "how", "what", "which", "who", "whom", "this", "that", "these", "those", "i", "me", "my",
            "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
            "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself",
            "they", "them", "their", "theirs", "themselves",
            "el", "la", "los", "las", "de", "en", "que", "por", "con", "para", "una", "uno", "del", "al",
            "es", "son", "fue", "ser", "estar", "tiene", "han", "pero", "como", "mas", "muy", "ya", "no",
            "si", "su", "sus", "se", "le", "lo", "me", "te", "nos", "les", "esto", "eso", "aquello")
        
        return text.lowercase()
            .split(Regex("[^a-záéíóúñü]+"))
            .filter { it.length > 3 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    /**
     * Query memories by multiple types at once.
     */
    private fun queryMemoriesByTypes(types: List<MemoryType>, minImportance: Float, limit: Int): List<MemoryEntry> {
        val now = System.currentTimeMillis()
        return memories.filter { memory ->
            memory.type in types
                && memory.importance >= minImportance
                && (memory.expiresAt == 0L || memory.expiresAt > now)
        }
            .sortedByDescending { it.importance }
            .take(limit)
    }

    private fun evictMemories() {
        val expired = memories.filter { it.expiresAt in 1..System.currentTimeMillis() }
        expired.forEach { deleteMemory(it.id) }

        if (memories.size > MAX_MEMORIES) {
            val toRemove = memories
                .sortedWith(compareBy({ it.accessCount }, { it.importance }, { it.timestamp }))
                .take(memories.size - MAX_MEMORIES + 50)
            toRemove.forEach { deleteMemory(it.id) }
        }
    }

    private fun summarizeMemories() {
        // Group related memories and create summaries
        val bySession = memories.groupBy { it.sessionId }
        bySession.forEach { (sessionId, entries) ->
            if (entries.size > 10) {
                // Keep top 5 by importance, summarize the rest
                val topEntries = entries.sortedByDescending { it.importance }.take(5)
                val rest = entries - topEntries.toSet()
                
                // Create a summary memory for the less important ones
                if (rest.isNotEmpty()) {
                    val summaryContent = rest.map { it.summary }.joinToString("; ")
                    storeMemory(
                        sessionId = sessionId,
                        type = MemoryType.CONTEXT,
                        content = "Session summary: $summaryContent",
                        summary = "Session had ${rest.size} interactions about: ${extractKeywords(summaryContent).take(5).joinToString(", ")}",
                        importance = 0.3f
                    )
                }
            }
        }
    }
}
