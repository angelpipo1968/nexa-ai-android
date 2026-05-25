package com.nexa.ai.web

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WebResultProcessor — tests formatting and processing logic.
 */
class WebResultProcessorTest {

    private val searchManager = WebSearchManager()
    private val processor = WebResultProcessor(searchManager)

    @Test
    fun `ProcessedResult data class works correctly`() {
        val result = ProcessedResult(
            summary = "Test summary",
            sources = listOf("https://example.com"),
            keyPoints = listOf("Point 1", "Point 2"),
            confidence = 0.8f,
            category = "technology",
            relatedTopics = listOf("AI", "ML"),
            formattedForChat = "## Test\nContent",
            formattedForVoice = "Test summary"
        )
        assertEquals("Test summary", result.summary)
        assertEquals(1, result.sources.size)
        assertEquals(2, result.keyPoints.size)
        assertEquals(0.8f, result.confidence, 0.001f)
        assertEquals("technology", result.category)
        assertEquals(2, result.relatedTopics.size)
    }

    @Test
    fun `SearchResult default values`() {
        val result = SearchResult(title = "Test", url = "", snippet = "Snippet")
        assertEquals(0f, result.relevanceScore, 0.001f)
        assertEquals("", result.source)
    }

    @Test
    fun `ScrapedContent default values`() {
        val content = ScrapedContent(url = "https://test.com", title = "Test", text = "Text")
        assertEquals("", content.cleanHtml)
        assertEquals("", content.publishDate)
        assertEquals("", content.author)
        assertEquals(0, content.wordCount)
        assertEquals("en", content.language)
    }

    @Test
    fun `NewsResult default values`() {
        val news = NewsResult(title = "News", source = "Source", url = "https://url.com", snippet = "Snippet")
        assertEquals("", news.publishDate)
        assertEquals("general", news.category)
        assertEquals("", news.imageUrl)
    }
}
