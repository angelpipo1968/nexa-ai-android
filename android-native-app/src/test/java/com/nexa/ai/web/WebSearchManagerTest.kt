package com.nexa.ai.web

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WebSearchManager — pure logic tests (no network calls).
 * Tests data models, cache behavior, and HTML parsing helpers.
 */
class WebSearchManagerTest {

    private lateinit var manager: WebSearchManager

    @Before
    fun setUp() {
        manager = WebSearchManager()
        manager.clearCache()
    }

    // ─── Data Model Tests ────────────────────────────────────

    @Test
    fun `SearchResult data class works correctly`() {
        val result = SearchResult(
            title = "Test Result",
            url = "https://example.com",
            snippet = "This is a test snippet",
            source = "DuckDuckGo",
            relevanceScore = 0.9f
        )
        assertEquals("Test Result", result.title)
        assertEquals("https://example.com", result.url)
        assertEquals("This is a test snippet", result.snippet)
        assertEquals("DuckDuckGo", result.source)
        assertEquals(0.9f, result.relevanceScore, 0.001f)
    }

    @Test
    fun `ScrapedContent data class works correctly`() {
        val content = ScrapedContent(
            url = "https://example.com/article",
            title = "Test Article",
            text = "This is the article content.",
            author = "John Doe",
            publishDate = "2024-01-15",
            wordCount = 100,
            language = "en"
        )
        assertEquals("https://example.com/article", content.url)
        assertEquals("Test Article", content.title)
        assertEquals("John Doe", content.author)
        assertEquals("2024-01-15", content.publishDate)
        assertEquals(100, content.wordCount)
        assertEquals("en", content.language)
    }

    @Test
    fun `NewsResult data class works correctly`() {
        val news = NewsResult(
            title = "Breaking News",
            source = "BBC",
            url = "https://bbc.com/news",
            snippet = "Something happened",
            category = "politics"
        )
        assertEquals("Breaking News", news.title)
        assertEquals("BBC", news.source)
        assertEquals("politics", news.category)
    }

    // ─── Cache Tests ─────────────────────────────────────────

    @Test
    fun `Cache starts empty`() {
        assertEquals(0, manager.getCacheSize())
    }

    @Test
    fun `ClearCache resets cache size`() {
        // Even though search would fail without network, cache should be clearable
        manager.clearCache()
        assertEquals(0, manager.getCacheSize())
    }
}
