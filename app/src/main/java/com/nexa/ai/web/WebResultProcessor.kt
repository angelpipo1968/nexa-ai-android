package com.nexa.ai.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebResultProcessor — Processes web search results and scraped content.
 * Summarizes, formats, and integrates web data with NLP pipeline.
 */

data class ProcessedResult(
    val summary: String,
    val sources: List<String>,
    val keyPoints: List<String>,
    val confidence: Float,
    val category: String,
    val relatedTopics: List<String>,
    val formattedForChat: String,
    val formattedForVoice: String,
    val timestamp: Long = System.currentTimeMillis()
)

class WebResultProcessor(private val searchManager: WebSearchManager) {

    companion object {
        private const val TAG = "WebResultProcessor"
        private const val MAX_SUMMARY_LENGTH = 500
        private const val MAX_VOICE_LENGTH = 200
        private const val MIN_KEY_POINT_WORDS = 5
    }

    /**
     * Process search results into a formatted response for the chat.
     */
    suspend fun processSearchResults(
        query: String,
        results: List<SearchResult>,
        language: String = "en"
    ): ProcessedResult = withContext(Dispatchers.Default) {
        if (results.isEmpty()) {
            return@withContext ProcessedResult(
                summary = if (language == "es") "No encontré resultados para \"$query\"." else "I couldn't find results for \"$query\".",
                sources = emptyList(),
                keyPoints = emptyList(),
                confidence = 0f,
                category = "unknown",
                relatedTopics = emptyList(),
                formattedForChat = if (language == "es") "❌ No encontré resultados para: **$query**\n\nIntenta reformular tu búsqueda o pregúntame de otra manera." else "❌ No results found for: **$query**\n\nTry rephrasing your search or ask me in a different way.",
                formattedForVoice = if (language == "es") "No encontré resultados para esa búsqueda." else "I couldn't find results for that search."
            )
        }

        // Extract key points from snippets
        val keyPoints = results.mapNotNull { result ->
            val point = result.snippet.trim()
            if (point.split(" ").size >= MIN_KEY_POINT_WORDS) point else null
        }.distinct().take(5)

        // Build summary from top results
        val topSnippets = results.take(3).map { it.snippet }.filter { it.isNotBlank() }
        val summary = buildSummary(query, topSnippets, language)

        // Extract sources
        val sources = results.map { 
            if (it.url.isNotBlank()) it.url else it.source 
        }.distinct().take(5)

        // Detect category from query and results
        val category = detectCategory(query, results)

        // Extract related topics
        val relatedTopics = extractRelatedTopics(query, results)

        // Calculate confidence based on result quality
        val confidence = calculateConfidence(results)

        // Format for chat (markdown)
        val chatFormatted = formatForChat(query, results, keyPoints, language)

        // Format for voice (concise, spoken)
        val voiceFormatted = formatForVoice(summary, keyPoints.take(3), language)

        ProcessedResult(
            summary = summary,
            sources = sources,
            keyPoints = keyPoints,
            confidence = confidence,
            category = category,
            relatedTopics = relatedTopics,
            formattedForChat = chatFormatted,
            formattedForVoice = voiceFormatted
        )
    }

    /**
     * Process scraped web page content into a formatted response.
     */
    suspend fun processScrapedContent(
        query: String,
        content: ScrapedContent,
        language: String = "en"
    ): ProcessedResult = withContext(Dispatchers.Default) {
        val sentences = content.text.split(Regex("""[.!?]+"""))
            .map { it.trim() }
            .filter { it.split(" ").size >= 5 }
        
        // Extract most relevant sentences based on query keywords
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val relevantSentences = sentences
            .sortedByDescending { sentence ->
                val sentenceWords = sentence.lowercase().split(Regex("\\W+")).toSet()
                sentenceWords.count { it in queryWords }.toFloat() / maxOf(sentenceWords.size, 1)
            }
            .take(5)

        val summary = relevantSentences.joinToString(". ").trim()
            .let { if (it.length > MAX_SUMMARY_LENGTH) it.take(MAX_SUMMARY_LENGTH) + "..." else it }

        val keyPoints = relevantSentences.map { 
            if (it.length > 100) it.take(97) + "..." else it 
        }

        val chatFormatted = buildString {
            append("## ${content.title}\n\n")
            if (content.author.isNotBlank()) append("**Author:** ${content.author}  ")
            if (content.publishDate.isNotBlank()) append("**Date:** ${content.publishDate}\n")
            append("\n${summary}\n\n")
            if (keyPoints.size > 1) {
                append("**Key Points:**\n")
                keyPoints.forEach { append("• $it\n") }
                append("\n")
            }
            append("🔗 [Source](${content.url})")
        }

        val voiceFormatted = buildString {
            append("According to ${content.title.ifBlank { "the source" }}")
            append(". ${relevantSentences.firstOrNull()?.take(MAX_VOICE_LENGTH) ?: "No relevant information found."}")
        }

        ProcessedResult(
            summary = summary,
            sources = listOf(content.url),
            keyPoints = keyPoints,
            confidence = if (relevantSentences.isNotEmpty()) 0.7f else 0.3f,
            category = detectCategory(query, emptyList()),
            relatedTopics = extractRelatedTopics(query, emptyList()),
            formattedForChat = chatFormatted,
            formattedForVoice = voiceFormatted
        )
    }

    /**
     * Full pipeline: search + optional scrape + process.
     */
    suspend fun searchAndProcess(
        query: String,
        scrapeTopResult: Boolean = true,
        language: String = "en"
    ): ProcessedResult {
        val results = searchManager.searchWeb(query)
        var processed = processSearchResults(query, results, language)

        // Optionally scrape the top result for deeper content
        if (scrapeTopResult && results.isNotEmpty()) {
            val topUrl = results.first().url
            if (topUrl.isNotBlank() && topUrl.startsWith("http")) {
                val scraped = searchManager.scrapeWebPage(topUrl)
                if (scraped != null && scraped.wordCount > 50) {
                    val scrapedProcessed = processScrapedContent(query, scraped, language)
                    // Merge: use scraped content if it's more detailed
                    if (scrapedProcessed.summary.length > processed.summary.length) {
                        processed = processed.copy(
                            summary = scrapedProcessed.summary,
                            keyPoints = (processed.keyPoints + scrapedProcessed.keyPoints).distinct().take(7),
                            formattedForChat = scrapedProcessed.formattedForChat,
                            confidence = (processed.confidence + scrapedProcessed.confidence) / 2
                        )
                    }
                }
            }
        }

        return processed
    }

    // ─── Private helpers ──────────────────────────────────────

    private fun buildSummary(query: String, snippets: List<String>, language: String): String {
        if (snippets.isEmpty()) {
            return if (language == "es") "Sin información detallada disponible." else "No detailed information available."
        }
        return snippets.joinToString(" ").trim()
            .let { if (it.length > MAX_SUMMARY_LENGTH) it.take(MAX_SUMMARY_LENGTH - 3) + "..." else it }
    }

    private fun formatForChat(
        query: String, 
        results: List<SearchResult>, 
        keyPoints: List<String>, 
        language: String
    ): String {
        return buildString {
            append(if (language == "es") "## Resultados de búsqueda: $query\n\n" else "## Search Results: $query\n\n")
            
            // Top answer if high confidence
            val topResult = results.firstOrNull()
            if (topResult != null && topResult.relevanceScore > 0.8f) {
                append("### ${topResult.title}\n")
                append("${topResult.snippet}\n\n")
            }

            // Key points
            if (keyPoints.isNotEmpty()) {
                append(if (language == "es") "**Puntos clave:**\n" else "**Key Points:**\n")
                keyPoints.forEach { append("• $it\n") }
                append("\n")
            }

            // Sources
            val sourceResults = results.filter { it.url.isNotBlank() }.take(3)
            if (sourceResults.isNotEmpty()) {
                append(if (language == "es") "**Fuentes:**\n" else "**Sources:**\n")
                sourceResults.forEach { result ->
                    append("• [${result.title.take(60)}](${result.url})\n")
                }
            }
        }
    }

    private fun formatForVoice(summary: String, keyPoints: List<String>, language: String): String {
        val voiceSummary = summary.take(MAX_VOICE_LENGTH).let { 
            val lastPeriod = it.lastIndexOf('.')
            if (lastPeriod > 0) it.take(lastPeriod + 1) else it
        }
        
        return buildString {
            append(voiceSummary)
            if (keyPoints.size > 1) {
                append(". ")
                append(if (language == "es") "Los puntos principales son: " else "The key points are: ")
                append(keyPoints.take(2).joinToString(", ") { 
                    it.take(50).let { s -> val lastPeriod = s.lastIndexOf('.'); if (lastPeriod > 0) s.take(lastPeriod) else s }
                })
                append(".")
            }
        }
    }

    private fun detectCategory(query: String, results: List<SearchResult>): String {
        val text = (query + " " + results.joinToString(" ") { it.snippet + " " + it.title }).lowercase()
        return when {
            text.contains("weather") || text.contains("temperature") || text.contains("forecast") -> "weather"
            text.contains("news") || text.contains("breaking") || text.contains("today") -> "news"
            text.contains("price") || text.contains("stock") || text.contains("market") || text.contains("crypto") -> "finance"
            text.contains("recipe") || text.contains("food") || text.contains("cook") -> "food"
            text.contains("movie") || text.contains("film") || text.contains("show") || text.contains("series") -> "entertainment"
            text.contains("sport") || text.contains("score") || text.contains("game") -> "sports"
            text.contains("health") || text.contains("symptom") || text.contains("medicine") -> "health"
            text.contains("code") || text.contains("programming") || text.contains("software") -> "technology"
            text.contains("define") || text.contains("meaning") || text.contains("what is") -> "definition"
            else -> "general"
        }
    }

    private fun extractRelatedTopics(query: String, results: List<SearchResult>): List<String> {
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val related = mutableSetOf<String>()
        
        results.forEach { result ->
            val words = (result.title + " " + result.snippet).lowercase().split(Regex("\\W+")).toSet()
            words.filter { it.length > 4 && it !in queryWords }.take(3).forEach { related.add(it) }
        }
        
        return related.take(5).toList()
    }

    private fun calculateConfidence(results: List<SearchResult>): Float {
        if (results.isEmpty()) return 0f
        val avgRelevance = results.map { it.relevanceScore }.average().toFloat()
        val hasHighRelevance = results.any { it.relevanceScore > 0.8f }
        return when {
            hasHighRelevance -> (avgRelevance * 0.7f + 0.3f).coerceAtMost(1f)
            else -> avgRelevance
        }
    }
}
