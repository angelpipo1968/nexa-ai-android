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
            Regex("(?:me gusta|me encanta|me fascina|amo|prefiero)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
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
                // Update profile interests
                val currentInterests = getUserProfile().interests.toMutableList()
                if (isPositive && preference !in currentInterests) {
                    currentInterests.add(preference.trim())
                    updateProfile { it.copy(interests = currentInterests.takeLast(10)) }
                }
            }
        }
        
        // Detect location references
        val locationPatterns = listOf(
            Regex("(?:vivo en|estoy en|soy de|mi casa está en|estamos en|vive en)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:i live in|i'm from|i'm in|my home is in|we are in|we live in)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE)
        )
        for (pattern in locationPatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { location ->
                memories.add(MemoryEntry(
                    content = "Ubicación del usuario: $location",
                    category = MemoryCategory.LOCATION,
                    sessionId = sessionId,
                    importance = 0.8f
                ))
                updateProfile { it.copy(location = location.trim()) }
            }
        }
        
        // Detect occupation
        val occupationPatterns = listOf(
            Regex("(?:soy|trabajo como|me dedico a|trabajo de)\\s+((?:ingeniero|doctor|profesor|estudiante|programador|diseñador|arquitecto|abogado|enfermero|chef|empresario|desarrollador|mecánico|electricista|plomero|vendedor|contador|administrador|médico|piloto|polícia|militar|taxista|conductor)[\\wáéíóúñ]*)", RegexOption.IGNORE_CASE),
            Regex("(?:i am|i work as|i'm a)\\s+((?:engineer|doctor|teacher|student|programmer|designer|architect|lawyer|nurse|chef|entrepreneur|developer|mechanic|electrician|plumber|salesperson|accountant|administrator|physician|pilot|police|military|driver)\\w*)", RegexOption.IGNORE_CASE)
        )
        for (pattern in occupationPatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { occupation ->
                memories.add(MemoryEntry(
                    content = "Ocupación: $occupation",
                    category = MemoryCategory.PERSONAL,
                    sessionId = sessionId,
                    importance = 0.8f
                ))
                updateProfile { it.copy(occupation = occupation.trim()) }
            }
        }
        
        // v6.0: Detect age/birthday
        val agePatterns = listOf(
            Regex("(?:tengo|tengo)\\s+(\\d+)\\s*(?:años|año)", RegexOption.IGNORE_CASE),
            Regex("(?:i am|i'm)\\s+(\\d+)\\s*(?:years old|yr old|yo)", RegexOption.IGNORE_CASE)
        )
        for (pattern in agePatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { age ->
                memories.add(MemoryEntry(
                    content = "Edad del usuario: $age años",
                    category = MemoryCategory.PERSONAL,
                    sessionId = sessionId,
                    importance = 0.8f
                ))
            }
        }
        
        // v6.0: Detect family references
        val familyPatterns = listOf(
            Regex("(?:mi (?:esposa|mujer|marido|esposo|novia|novio|hijo|hija|mamá|papá|madre|padre|hermano|hermana))\\s+(?:se llama|es|se llama)\\s*([\\wáéíóúñ]+)", RegexOption.IGNORE_CASE),
            Regex("(?:my (?:wife|husband|girlfriend|boyfriend|son|daughter|mom|dad|mother|father|brother|sister))(?:'s| is| is named| named)\\s*([\\w]+)?", RegexOption.IGNORE_CASE)
        )
        for (pattern in familyPatterns) {
            pattern.find(content)?.let { match ->
                val familyInfo = match.value
                memories.add(MemoryEntry(
                    content = "Familia: $familyInfo",
                    category = MemoryCategory.PERSONAL,
                    sessionId = sessionId,
                    importance = 0.85f
                ))
            }
        }
        
        // v6.0: Detect communication style preference
        if (lower.contains("habla menos") || lower.contains("sé breve") || lower.contains("corto") ||
            lower.contains("be brief") || lower.contains("short answer") || lower.contains("keep it short")) {
            updateProfile { it.copy(communicationStyle = "concise") }
            memories.add(MemoryEntry(
                content = "Prefiere respuestas breves y concisas",
                category = MemoryCategory.PREFERENCE,
                sessionId = sessionId,
                importance = 0.9f
            ))
        }
        if (lower.contains("explica más") || lower.contains("dame detalles") || lower.contains("más detalle") ||
            lower.contains("explain more") || lower.contains("more details") || lower.contains("elaborate")) {
            updateProfile { it.copy(communicationStyle = "detailed") }
            memories.add(MemoryEntry(
                content = "Prefiere respuestas detalladas y completas",
                category = MemoryCategory.PREFERENCE,
                sessionId = sessionId,
                importance = 0.9f
            ))
        }
        
        // v6.0: Detect food/drink preferences
        val foodPatterns = listOf(
            Regex("(?:me gusta comer|me encanta comer|mi comida favorita|me gusta tomar|mi bebida favorita)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE),
            Regex("(?:i like to eat|i love eating|my favorite food|i like to drink|my favorite drink)\\s+(.+?)(?:\\.|,|$)", RegexOption.IGNORE_CASE)
        )
        for (pattern in foodPatterns) {
            pattern.find(content)?.groupValues?.get(1)?.let { food ->
                memories.add(MemoryEntry(
                    content = "Comida/bebida favorita: $food",
                    category = MemoryCategory.PREFERENCE,
                    sessionId = sessionId,
                    importance = 0.6f
                ))
            }
        }
        
        // v6.0: Detect important events / appointments
        val eventPatterns = listOf(
            Regex("(?:mañana|el próximo|el lunes|el martes|el miércoles|el jueves|el viernes|el sábado|el domingo|next|tomorrow)\\s+.+?(?:cita|reunión|cumpleaños|viaje|vuelo|entrevista|doctor|médico|appointment|meeting|birthday|trip|flight|interview))", RegexOption.IGNORE_CASE),
            Regex("(?:tengo|I have)\\s+(?:una|un|a|an)\\s+.+?(?:cita|reunión|cumpleaños|viaje|vuelo|entrevista|appointment|meeting|birthday|trip|flight)", RegexOption.IGNORE_CASE)
        )
        for (pattern in eventPatterns) {
            pattern.find(content)?.let { match ->
                memories.add(MemoryEntry(
                    content = "Evento: ${match.value}",
                    category = MemoryCategory.EVENT,
                    sessionId = sessionId,
                    importance = 0.85f
                ))
            }
        }
        
        // Store all extracted memories
        memories.forEach { storeMemory(it) }
        
        // v6.0: Always store a conversation summary if the message is substantial
        if (content.length > 30 && memories.isEmpty()) {
            storeMemory(MemoryEntry(
                content = content.take(100),
                category = MemoryCategory.CONVERSATION,
                sessionId = sessionId,
                importance = 0.3f
            ))
        }
        
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
        // v6.0: Include communication style so AI knows how to respond
        if (profile.communicationStyle.isNotBlank() && profile.communicationStyle != "friendly") {
            contextBuilder.append("USER COMMUNICATION STYLE: ${profile.communicationStyle}. ")
        }
        // v6.0: Include total interactions for loyalty recognition
        if (profile.totalInteractions > 10) {
            contextBuilder.append("This is a returning user who has interacted ${profile.totalInteractions} times. Make them feel recognized. ")
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
