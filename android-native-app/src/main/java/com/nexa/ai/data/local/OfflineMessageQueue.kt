package com.nexa.ai.data.local

import androidx.room.*

/**
 * Queued message waiting to be sent when network is available.
 * Part of the offline-first architecture.
 */
@Entity(tableName = "pending_messages", indices = [Index("sessionId"), Index("createdAt")])
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

@Dao
interface PendingMessageDao {
    @Insert
    suspend fun enqueue(message: PendingMessageEntity): Long

    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getPendingForSession(sessionId: String): List<PendingMessageEntity>

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun dequeue(id: Long)

    @Query("DELETE FROM pending_messages WHERE sessionId = :sessionId")
    suspend fun dequeueAllForSession(sessionId: String)

    @Query("UPDATE pending_messages SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("DELETE FROM pending_messages WHERE retryCount >= maxRetries")
    suspend fun removeFailedMessages()

    @Query("SELECT COUNT(*) FROM pending_messages")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_messages")
    fun countFlow(): kotlinx.coroutines.flow.Flow<Int>
}
