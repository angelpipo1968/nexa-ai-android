package com.nexa.ai.data

import android.content.Context
import android.util.Log
import com.nexa.ai.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * OfflineManager — Orchestrates offline-first behavior.
 * - Queues messages when network is unavailable
 * - Automatically flushes queue when connectivity is restored
 * - Caches AI responses for offline viewing
 * - Provides pending message count for UI indicator
 */
class OfflineManager(private val context: Context) {

    private val db by lazy { NexaDatabase.getInstance(context) }
    private val pendingDao by lazy { db.pendingMessageDao() }
    private val cacheDao by lazy { db.cachedResponseDao() }
    private val searchDao by lazy { db.cachedSearchDao() }
    private val networkMonitor by lazy { NetworkMonitor(context) }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NexaOffline"
    }

    // Pending message count for UI badge
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    // Whether we're currently online
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        // Observe pending count
        scope.launch {
            pendingDao.countFlow().collect { count ->
                _pendingCount.value = count
            }
        }
        // Observe network state
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                val wasOffline = !_isOnline.value
                _isOnline.value = online
                if (online && wasOffline) {
                    // Network restored — try to flush pending messages
                    flushPendingMessages()
                }
            }
        }
    }

    /**
     * Enqueue a message for later sending when offline.
     * Returns true if the message was queued (offline), false if online.
     */
    suspend fun enqueueIfOffline(sessionId: String, content: String): Boolean {
        if (_isOnline.value) return false

        pendingDao.enqueue(
            PendingMessageEntity(
                sessionId = sessionId,
                content = content,
                createdAt = System.currentTimeMillis()
            )
        )
        Log.i(TAG, "Message queued for offline delivery: ${content.take(30)}...")
        return true
    }

    /**
     * Flush all pending messages. Called when connectivity is restored.
     * Returns a list of (sessionId, content) pairs to be sent.
     */
    suspend fun flushPendingMessages(): List<Pair<String, String>> {
        val pending = pendingDao.getAllPending()
        if (pending.isEmpty()) return emptyList()

        Log.i(TAG, "Flushing ${pending.size} pending messages")
        val messages = pending.map { it.sessionId to it.content }

        // Remove successfully flushed messages
        pending.forEach { msg ->
            pendingDao.dequeue(msg.id)
        }

        return messages
    }

    /**
     * Mark a pending message as failed (increment retry count).
     * Returns true if the message should be abandoned (max retries reached).
     */
    suspend fun markFailed(messageId: Long): Boolean {
        pendingDao.incrementRetryCount(messageId)
        pendingDao.removeFailedMessages()
        return true
    }

    /**
     * Cache an AI response for offline viewing.
     */
    suspend fun cacheResponse(sessionId: String, query: String, response: String, provider: String = "") {
        val queryHash = query.hashCode().toString()
        cacheDao.cache(
            CachedResponseEntity(
                sessionId = sessionId,
                queryHash = queryHash,
                query = query,
                response = response,
                provider = provider
            )
        )
    }

    /**
     * Get cached responses for a session.
     */
    suspend fun getCachedResponses(sessionId: String, limit: Int = 50): List<CachedResponseEntity> {
        return cacheDao.getForSession(sessionId, limit)
    }

    /**
     * Check if we have a cached response for a query.
     */
    suspend fun getCachedResponse(queryHash: String): CachedResponseEntity? {
        return cacheDao.getCached(queryHash)
    }

    /**
     * Clean up old cache entries.
     */
    suspend fun evictOldCache() {
        cacheDao.evictOlderThan()
        searchDao.evictOlderThan()
    }

    /**
     * Get count of pending messages.
     */
    suspend fun pendingMessageCount(): Int = pendingDao.count()

    fun destroy() {
        scope.cancel()
    }
}
