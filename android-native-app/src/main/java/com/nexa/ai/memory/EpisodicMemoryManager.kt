package com.nexa.ai.memory

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * EpisodicMemoryManager — Stores and retrieves conversational memories.
 * Remembers facts about the user across sessions: name, preferences, 
 * important events, and conversation context.
 */
class EpisodicMemoryManager(context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("nexa_episodic_memory", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TAG = "NexaMemory"
        private const val KEY_MEMORIES = "episodic_memories"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_FACTS = "user_facts"
        private const val MAX_MEMORIES = 100
    }

    // ─── Memory Entry ────────────────────────────
    data class MemoryEntry(
        val id: String = System.currentTimeMillis().toString(),
        val content: String,
        val category: MemoryCategory,
        val timestamp: Long = System.currentTimeMillis(),
        val sessionId: String = "",
        val importance: Float = 0.5f, // 0.0 to 1.0
        val accessCount: Int = 0
    )

    enum class MemoryCategory {
        PERSONAL,       // Name, age, family
        PREFERENCE,     // Likes, dislikes, favorites
        LOCATION,       // Home, work, frequent places
        EVENT,          // Important dates, appointments
        SKILL,          // Things user knows or wants to learn
        CONVERSATION,   // Key conversation topics
        EMOTION,        // Emotional state or mood
        GENERAL         // Everything else
    }

    // ─── User Profile ────────────────────────────
    data class UserProfile(
        val name: String = "",
        val preferredLanguage: String = "es",
        val location: String = "",
        val occupation: String = "",
        val interests: List<String> = emptyList(),
        val communicationStyle: String = "friendly", // friendly, formal, casual
        val lastInteraction: Long = 0,
        val totalInteractions: Int = 0
    )

    // ─── Store Memory ────────────────────────────
    
    fun storeMemory(entry: MemoryEntry) {
        val memories = getMemories().toMutableList()
        
        // Check for duplicate or similar memory
        val existingIdx = memories.indexOfFirst { 
            it.content.lowercase() == entry.content.lowercase() ||
            similarity(it.content, entry.content) > 0.8f
        }
        
        if (existingIdx >= 0) {
            // Update existing memory (increase importance)
            memories[existingIdx] = memories[existingIdx].copy(
                importance = (memories[existingIdx].importance + 0.1f).coerceAtMost(1.0f),
                accessCount = memories[existingIdx].accessCount + 1,
                timestamp = System.currentTimeMillis()
            )
        } else {
            memories.add(0, entry) // Add new memory at top
        }
        
        // Keep only most important/recent memories
        val trimmed = memories
            .sortedByDescending { it.importance * 0.7 + (it.accessCount * 0.1f) }
            .take(MAX_MEMORIES)
        
        saveMemories(trimmed)
        Log.d(TAG, "Memory stored: ${entry.content.take(50)}... (${entry.category})")
    }

    // ─── Retrieve Memories ───────────────────────
    
    fun getMemories(category: MemoryCategory? = null): List<MemoryEntry> {
        val json = prefs.getString(KEY_MEMORIES, null) ?: return emptyList()
        val type = object : TypeToken<List<MemoryEntry>>() {}.type
        val memories: List<MemoryEntry> = try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
        return if (category != null) memories.filter { it.category == category } else memories
    }

    fun searchMemories(query: String, limit: Int = 5): List<MemoryEntry> {
        val memories = getMemories()
        val queryLower = query.lowercase()
        return memories
            .map { it to relevanceScore(it, queryLower) }
            .filter { it.second > 0.2f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun relevanceScore(memory: MemoryEntry, query: String): Float {
        val content = memory.content.lowercase()
        var score = 0f
        
        // Exact word match
        val queryWords = query.split("\\s+".toRegex())
        for (word in queryWords) {
            if (content.contains(word)) score += 0.3f
        }
        
        // Full query match bonus
        if (content.contains(query)) score += 0.5f
        
        // Importance weight
        score *= (0.5f + memory.importance * 0.5f)
        
        // Recency bonus (newer = more relevant)
        val ageHours = (System.currentTimeMillis() - memory.timestamp) / (1000 * 60 * 60)
        score *= (1.0f - (ageHours / 168f).coerceAtMost(0.5f)) // Decay over 1 week
        
        return score.coerceIn(0f, 1f)
    }

    // ─── Auto-extract Memories from Conversation ──
    
    fun extractMemoriesFromMessage(role: String, content: String, sessionId: String): List<MemoryEntry> {
        val memories = mutableListOf<MemoryEntry>()
        val lower = content.lowercase()
        
        if (role != "user") return memories // Only extract from user messages
        
        // Detect name introduction
        val namePatterns = listOf(
            Regex("(?:me llamo|mi nombre es|soy)\\s+([\\wáéíóúñ]+)", RegexOption.IGNORE_CASE),
            Regex("(?:my name is|i am|i'm)\\s+([\\w]+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in namePatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { name ->
                memories.add(MemoryEntry(
                    content = "El usuario se llama $name",
                    category = MemoryCategory.PERSONAL,
                    sessionId = sessionId,
                    importance = 0.9f
                ))
                // Also update user profile
                updateProfile { it.copy(name = name) }
            }
        }
        
        // Detect preferences
        val preferencePatterns = listOf(
            Regex("(?:me gusta|me encanta|me fascina|amo)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:i like|i love|i enjoy|i prefer)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:no me gusta|odio|detesto)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:i don't like|i hate|i dislike)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE)
        )
        for (pattern in preferencePatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { preference ->
                val isPositive = !lower.contains("no me gusta") && !lower.contains("odio") && 
                                 !lower.contains("detesto") && !lower.contains("don't like") && 
                                 !lower.contains("hate") && !lower.contains("dislike")
                memories.add(MemoryEntry(
                    content = if (isPositive) "Le gusta: $preference" else "No le gusta: $preference",
                    category = MemoryCategory.PREFERENCE,
                    sessionId = sessionId,
                    importance = 0.7f
                ))
            }
        }
        
        // Detect location references
        val locationPatterns = listOf(
            Regex("(?:vivo en|estoy en|soy de|mi casa está en)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:i live in|i'm from|i'm in|my home is in)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE)
        )
        for (pattern in locationPatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { location ->
                memories.add(MemoryEntry(
                    content = "Ubicación del usuario: $location",
                    category = MemoryCategory.LOCATION,
                    sessionId = sessionId,
                    importance = 0.8f
                ))
                updateProfile { it.copy(location = location) }
            }
        }
        
        // Detect occupation
        val occupationPatterns = listOf(
            Regex("(?:soy|trabajo como|me dedico a)\\s+((?:ingeniero|doctor|profesor|estudiante|programador|diseñador|arquitecto|abogado|enfermero|chef|empresario|desarrollador)[\\wáéíóúñ]*)", RegexOption.IGNORE_CASE),
            Regex("(?:i am|i work as)\\s+((?:engineer|doctor|teacher|student|programmer|designer|architect|lawyer|nurse|chef|entrepreneur|developer)\\w*)", RegexOption.IGNORE_CASE)
        )
        for (pattern in occupationPatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { occupation ->
                memories.add(MemoryEntry(
                    content = "Ocupación: $occupation",
                    category = MemoryCategory.PERSONAL,
                    sessionId = sessionId,
                    importance = 0.8f
                ))
                updateProfile { it.copy(occupation = occupation) }
            }
        }
        
        // Store all extracted memories
        memories.forEach { storeMemory(it) }
        return memories
    }

    // ─── User Profile Management ─────────────────
    
    fun getUserProfile(): UserProfile {
        val json = prefs.getString(KEY_USER_PROFILE, null) ?: return UserProfile()
        return try { gson.fromJson(json, UserProfile::class.java) } catch (_: Exception) { UserProfile() }
    }

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        val current = getUserProfile()
        val updated = transform(current).copy(
            lastInteraction = System.currentTimeMillis(),
            totalInteractions = current.totalInteractions + 1
        )
        prefs.edit().putString(KEY_USER_PROFILE, gson.toJson(updated)).apply()
    }

    // ─── Facts Storage ───────────────────────────
    
    fun storeFact(fact: String) {
        val facts = getFacts().toMutableList()
        if (!facts.any { it.lowercase() == fact.lowercase() }) {
            facts.add(fact)
            prefs.edit().putString(KEY_FACTS, gson.toJson(facts)).apply()
        }
    }

    fun getFacts(): List<String> {
        val json = prefs.getString(KEY_FACTS, null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<String>>() {}.type) } 
        catch (_: Exception) { emptyList() }
    }

    // ─── Build Memory Context for AI ─────────────
    
    fun buildMemoryContext(query: String): String {
        val profile = getUserProfile()
        val relevantMemories = searchMemories(query, limit = 5)
        val facts = getFacts().takeLast(10)
        
        val contextBuilder = StringBuilder()
        
        if (profile.name.isNotBlank()) {
            contextBuilder.append("USER NAME: ${profile.name}. ")
        }
        if (profile.occupation.isNotBlank()) {
            contextBuilder.append("USER OCCUPATION: ${profile.occupation}. ")
        }
        if (profile.location.isNotBlank()) {
            contextBuilder.append("USER HOME: ${profile.location}. ")
        }
        if (profile.interests.isNotEmpty()) {
            contextBuilder.append("USER INTERESTS: ${profile.interests.joinToString(", ")}. ")
        }
        
        if (relevantMemories.isNotEmpty()) {
            contextBuilder.append("\nRELEVANT MEMORIES: ")
            relevantMemories.forEach { mem ->
                contextBuilder.append("- ${mem.content} ")
            }
        }
        
        if (facts.isNotEmpty()) {
            contextBuilder.append("\nKNOWN FACTS: ")
            facts.forEach { fact ->
                contextBuilder.append("- $fact ")
            }
        }
        
        return contextBuilder.toString().trim()
    }

    // ─── Utility ─────────────────────────────────

    private fun similarity(a: String, b: String): Float {
        val setA = a.lowercase().split("\\s+".toRegex()).toSet()
        val setB = b.lowercase().split("\\s+".toRegex()).toSet()
        val intersection = setA.intersect(setB)
        val union = setA.union(setB)
        return if (union.isEmpty()) 0f else intersection.size.toFloat() / union.size
    }

    private fun saveMemories(memories: List<MemoryEntry>) {
        prefs.edit().putString(KEY_MEMORIES, gson.toJson(memories)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
