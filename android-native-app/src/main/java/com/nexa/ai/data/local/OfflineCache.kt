package com.nexa.ai.data.local

import androidx.room.*

/**
 * Cached AI response for offline access.
 * Stores the last response for each conversation context so users can
 * view recent responses even without internet.
 */
@Entity(tableName = "response_cache", indices = [Index("sessionId"), Index("queryHash"), Index("cachedAt")])
data class CachedResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val queryHash: String,      // Hash of the query for deduplication
    val query: String,           // Original user query
    val response: String,        // Full AI response
    val provider: String = "",   // "groq", "pollinations", "backend"
    val cachedAt: Long = System.currentTimeMillis(),
    val ttl: Long = 24 * 60 * 60 * 1000L  // 24 hours default TTL
)

@Dao
interface CachedResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cache(response: CachedResponseEntity)

    @Query("SELECT * FROM response_cache WHERE sessionId = :sessionId ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getForSession(sessionId: String, limit: Int = 50): List<CachedResponseEntity>

    @Query("SELECT * FROM response_cache WHERE queryHash = :queryHash AND cachedAt > :minTime")
    suspend fun getCached(queryHash: String, minTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000L): CachedResponseEntity?

    @Query("DELETE FROM response_cache WHERE cachedAt < :before")
    suspend fun evictOlderThan(before: Long = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)

    @Query("DELETE FROM response_cache WHERE sessionId = :sessionId")
    suspend fun evictSession(sessionId: String)
}

/**
 * Cached web search result for offline access.
 */
@Entity(tableName = "search_cache", indices = [Index("query"), Index("cachedAt")])
data class CachedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val resultsJson: String,    // JSON serialized search results
    val resultCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis(),
    val ttl: Long = 2 * 60 * 60 * 1000L  // 2 hours TTL for search results
)

@Dao
interface CachedSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cache(search: CachedSearchEntity)

    @Query("SELECT * FROM search_cache WHERE query = :query AND cachedAt > :minTime")
    suspend fun getCached(query: String, minTime: Long = System.currentTimeMillis() - 2 * 60 * 60 * 1000L): CachedSearchEntity?

    @Query("DELETE FROM search_cache WHERE cachedAt < :before")
    suspend fun evictOlderThan(before: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
}
