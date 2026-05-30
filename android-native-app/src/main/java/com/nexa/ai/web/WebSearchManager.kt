package com.nexa.ai.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * WebSearchManager — Handles web search via DuckDuckGo API and web scraping.
 * Provides real-time information retrieval for the NEXA AI assistant.
 */

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "",
    val relevanceScore: Float = 0f
)

data class ScrapedContent(
    val url: String,
    val title: String,
    val text: String,
    val cleanHtml: String = "",
    val publishDate: String = "",
    val author: String = "",
    val wordCount: Int = 0,
    val language: String = "en"
)

data class NewsResult(
    val title: String,
    val source: String,
    val url: String,
    val snippet: String,
    val publishDate: String = "",
    val category: String = "general",
    val imageUrl: String = ""
)

class WebSearchManager {

    companion object {
        private const val TAG = "WebSearchManager"
        private const val DDG_API_URL = "https://api.duckduckgo.com/"
        private const val DDG_LITE_URL = "https://lite.duckduckgo.com/lite/"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 15000
        private val CACHE = mutableMapOf<String, Pair<List<SearchResult>, Long>>() // query -> results + timestamp
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    // ─── Web Search ───────────────────────────────────────────

    /**
     * Search the web using DuckDuckGo Instant Answer API + HTML lite endpoint.
     * Returns a list of SearchResult sorted by relevance.
     */
    suspend fun searchWeb(query: String, maxResults: Int = 8): List<SearchResult> = withContext(Dispatchers.IO) {
        // Check cache first
        val cacheKey = query.lowercase().trim()
        CACHE[cacheKey]?.let { (results, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
                Log.d(TAG, "Cache hit for: $query")
                return@withContext results.take(maxResults)
            }
        }

        val results = mutableListOf<SearchResult>()

        try {
            // Method 1: DuckDuckGo Instant Answer API
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("${DDG_API_URL}?q=$encodedQuery&format=json&no_html=1&skip_disambig=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", USER_AGENT)

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                // Abstract (instant answer)
                json.optString("Abstract", "")?.let {
                    if (it.isNotBlank()) {
                        results.add(SearchResult(
                            title = json.optString("Heading", query),
                            url = json.optString("AbstractURL", ""),
                            snippet = it,
                            source = "DuckDuckGo",
                            relevanceScore = 1.0f
                        ))
                    }
                }

                // Related topics
                val topics = json.optJSONArray("RelatedTopics")
                if (topics != null) {
                    for (i in 0 until minOf(topics.length(), maxResults)) {
                        val topic = topics.optJSONObject(i) ?: continue
                        val text = topic.optString("Text", "")
                        if (text.isNotBlank()) {
                            val firstResult = topic.optJSONArray("FirstURL")?.optJSONObject(0)
                            results.add(SearchResult(
                                title = text.take(80),
                                url = topic.optString("FirstURL", firstResult?.optString("URL", "") ?: ""),
                                snippet = text,
                                source = "DuckDuckGo",
                                relevanceScore = (1.0f - (i * 0.1f)).coerceAtLeast(0.1f)
                            ))
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "DDG API search failed: ${e.message}")
        }

        // Method 2: DuckDuckGo Lite HTML scraping as fallback
        if (results.size < 3) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = URL("${DDG_LITE_URL}?q=$encodedQuery&kl=wt-wt")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout = READ_TIMEOUT
                conn.setRequestProperty("User-Agent", USER_AGENT)

                if (conn.responseCode == 200) {
                    val html = conn.inputStream.bufferedReader().readText()
                    // Parse HTML results from DuckDuckGo Lite
                    val parsedResults = parseDdgLiteHtml(html, maxResults - results.size)
                    results.addAll(parsedResults)
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "DDG Lite scraping failed: ${e.message}")
            }
        }

        // Sort by relevance
        val sorted = results.sortedByDescending { it.relevanceScore }.take(maxResults)
        CACHE[cacheKey] = sorted to System.currentTimeMillis()
        sorted
    }

    /**
     * Parse DuckDuckGo Lite HTML to extract search results.
     */
    private fun parseDdgLiteHtml(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            // Extract search result links and snippets from DDG Lite HTML
            // DDG Lite uses <a class="result-link"> and <td class="result-snippet">
            val linkRegex = Regex("""<a[^>]+class="result-link"[^>]+href="([^"]+)"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            val snippetRegex = Regex("""<td[^>]+class="result-snippet"[^>]*>([^<]+)</td>""", RegexOption.IGNORE_CASE)

            val links = linkRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).toList()

            val count = minOf(links.size, snippets.size, maxResults)
            for (i in 0 until count) {
                val linkMatch = links[i]
                val snippetMatch = snippets[i]
                results.add(SearchResult(
                    title = decodeHtmlEntities(linkMatch.groupValues[2].trim()),
                    url = linkMatch.groupValues[1].trim(),
                    snippet = decodeHtmlEntities(snippetMatch.groupValues[1].trim()),
                    source = "DuckDuckGo",
                    relevanceScore = (1.0f - (i * 0.1f)).coerceAtLeast(0.1f)
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML parsing error: ${e.message}")
        }
        return results
    }

    // ─── Web Scraping ─────────────────────────────────────────

    /**
     * Scrape a web page and extract clean text content.
     */
    suspend fun scrapeWebPage(urlString: String): ScrapedContent? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (conn.responseCode != 200) {
                Log.w(TAG, "Failed to scrape ${urlString}: HTTP ${conn.responseCode}")
                return@withContext null
            }

            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Extract title
            val titleRegex = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            val title = titleRegex.find(html)?.groupValues?.get(1)?.let { decodeHtmlEntities(it) } ?: ""

            // Extract meta description
            val descRegex = Regex("""<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val description = descRegex.find(html)?.groupValues?.get(1)?.let { decodeHtmlEntities(it) } ?: ""

            // Extract author
            val authorRegex = Regex("""<meta[^>]+name=["']author["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val author = authorRegex.find(html)?.groupValues?.get(1)?.let { decodeHtmlEntities(it) } ?: ""

            // Extract publish date
            val dateRegex = Regex("""<meta[^>]+(?:name|property)=["'](?:date|article:published_time|publish-date)["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val publishDate = dateRegex.find(html)?.groupValues?.get(1)?.let { decodeHtmlEntities(it) } ?: ""

            // Clean HTML — remove scripts, styles, tags
            val cleanText = html
                .replace(Regex("""<script[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<style[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<nav[^>]*>[\s\S]*?</nav>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<footer[^>]*>[\s\S]*?</footer>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<header[^>]*>[\s\S]*?</header>""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""&nbsp;"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .let { decodeHtmlEntities(it) }

            // Extract main article content (heuristic: longest paragraph block)
            val paragraphs = cleanText.split(Regex("""\.\s"""))
                .filter { it.trim().split(" ").size > 15 } // Only substantial paragraphs
                .sortedByDescending { it.length }

            val mainContent = if (paragraphs.size > 3) {
                paragraphs.take(paragraphs.size / 2 + 1).joinToString(". ")
            } else {
                cleanText.take(5000) // Fallback to first 5000 chars
            }

            ScrapedContent(
                url = urlString,
                title = title,
                text = if (description.isNotBlank() && mainContent.length > description.length) 
                    "$description\n\n$mainContent" else mainContent,
                cleanHtml = cleanText,
                publishDate = publishDate,
                author = author,
                wordCount = mainContent.split(" ").size,
                language = detectLanguage(cleanText)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping $urlString: ${e.message}")
            null
        }
    }

    // ─── News Search ──────────────────────────────────────────

    /**
     * Search for recent news articles on a topic.
     */
    suspend fun searchNews(query: String, maxResults: Int = 5): List<NewsResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NewsResult>()
        try {
            val encodedQuery = URLEncoder.encode("$query news", "UTF-8")
            val url = URL("${DDG_API_URL}?q=$encodedQuery&format=json&no_html=1&skip_disambig=1&df=d")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("User-Agent", USER_AGENT)

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val topics = json.optJSONArray("RelatedTopics")
                if (topics != null) {
                    for (i in 0 until minOf(topics.length(), maxResults)) {
                        val topic = topics.optJSONObject(i) ?: continue
                        val text = topic.optString("Text", "")
                        if (text.isNotBlank()) {
                            results.add(NewsResult(
                                title = text.take(100),
                                source = topic.optString("FirstURL", "").let { extractDomain(it) },
                                url = topic.optString("FirstURL", ""),
                                snippet = text,
                                category = categorizeNews(text)
                            ))
                        }
                    }
                }

                // Abstract as a news result if available
                json.optString("Abstract", "")?.let {
                    if (it.isNotBlank()) {
                        results.add(0, NewsResult(
                            title = json.optString("Heading", query),
                            source = json.optString("AbstractSource", extractDomain(json.optString("AbstractURL", ""))),
                            url = json.optString("AbstractURL", ""),
                            snippet = it,
                            category = categorizeNews(it)
                        ))
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "News search failed: ${e.message}")
        }
        results
    }

    // ─── Fact Checking ────────────────────────────────────────

    /**
     * Verify a claim by searching for corroborating sources.
     * Returns a confidence score and list of sources.
     */
    suspend fun factCheck(claim: String): Triple<Float, String, List<SearchResult>> = withContext(Dispatchers.IO) {
        val searchResults = searchWeb(claim, maxResults = 5)
        
        if (searchResults.isEmpty()) {
            return@withContext Triple(0f, "No sources found to verify this claim.", emptyList())
        }

        // Analyze snippet overlap with the claim
        val claimWords = claim.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        var matchCount = 0
        var totalWords = 0
        
        searchResults.forEach { result ->
            val snippetWords = result.snippet.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
            totalWords += snippetWords.size
            matchCount += snippetWords.count { it in claimWords }
        }

        val overlapRatio = if (totalWords > 0) matchCount.toFloat() / totalWords else 0f
        val confidence = when {
            overlapRatio > 0.3f -> 0.8f + (overlapRatio * 0.2f)
            overlapRatio > 0.15f -> 0.5f + (overlapRatio * 0.5f)
            overlapRatio > 0.05f -> 0.3f + (overlapRatio * 0.5f)
            else -> overlapRatio
        }.coerceIn(0f, 1f)

        val assessment = when {
            confidence > 0.7f -> "This claim appears to be supported by multiple sources."
            confidence > 0.4f -> "This claim is partially supported but could not be fully verified."
            else -> "This claim could not be reliably verified. Multiple perspectives found."
        }

        Triple(confidence.coerceAtMost(1f), assessment, searchResults)
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    }

    private fun extractDomain(url: String): String {
        return try {
            val domain = URL(url).host
            domain.replace("www.", "").split(".").takeLast(2).joinToString(".")
        } catch (e: Exception) { "unknown" }
    }

    private fun detectLanguage(text: String): String {
        val sample = text.take(500).lowercase()
        val spanishPatterns = listOf(" el ", " la ", " los ", " las ", " de ", " en ", " que ", " por ", " con ", " para ", " una ", " uno ", " del ", " al ")
        val spanishMatches = spanishPatterns.count { it in sample }
        return if (spanishMatches > 5) "es" else "en"
    }

    private fun categorizeNews(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("sport") || lower.contains("game") || lower.contains("football") || lower.contains("soccer") -> "sports"
            lower.contains("tech") || lower.contains("ai") || lower.contains("software") || lower.contains("robot") -> "technology"
            lower.contains("politic") || lower.contains("government") || lower.contains("election") -> "politics"
            lower.contains("science") || lower.contains("research") || lower.contains("study") -> "science"
            lower.contains("health") || lower.contains("medical") || lower.contains("disease") -> "health"
            lower.contains("business") || lower.contains("market") || lower.contains("economy") || lower.contains("stock") -> "business"
            lower.contains("entertainment") || lower.contains("movie") || lower.contains("music") || lower.contains("celebrity") -> "entertainment"
            else -> "general"
        }
    }

    fun clearCache() {
        CACHE.clear()
    }

    fun getCacheSize(): Int = CACHE.size
}
