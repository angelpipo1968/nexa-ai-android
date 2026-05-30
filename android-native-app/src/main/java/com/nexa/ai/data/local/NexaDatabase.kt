package com.nexa.ai.data.local

import android.content.Context
import androidx.room.*

// ═══════════════════════════════════════
//  ROOM ENTITIES
// ═══════════════════════════════════════

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val messageId: String,
    val role: String,
    val content: String
)

// ═══════════════════════════════════════
//  DAO
// ═══════════════════════════════════════

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<SessionWithMessages>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()


    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    /** Atomic replace-all: borra mensajes primero, luego sesiones, luego inserta todo. */
    @Transaction
    suspend fun saveAll(sessions: List<SessionEntity>, messages: List<MessageEntity>) {
        deleteAllMessages()   // 1. borra messages primero (evita FK violation)
        deleteAll()           // 2. borra sessions
        sessions.forEach { insertSession(it) }  // 3. inserta sessions
        if (messages.isNotEmpty()) insertMessages(messages) // 4. inserta messages
    }
}

data class SessionWithMessages(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<MessageEntity>
)

// ═══════════════════════════════════════
//  DATABASE
// ═══════════════════════════════════════

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        PendingMessageEntity::class,
        CachedResponseEntity::class,
        CachedSearchEntity::class,
        VoiceProfileEntity::class,
        EmotionEntity::class,
        MemoryFactEntity::class,
        LearningSignalEntity::class,
        UserPreferenceEntity::class,
        ActivityLogEntity::class,
        SensorDataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun cachedResponseDao(): CachedResponseDao
    abstract fun cachedSearchDao(): CachedSearchDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun emotionDao(): EmotionDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun learningSignalDao(): LearningSignalDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun sensorDataDao(): SensorDataDao

    companion object {
        @Volatile
        private var INSTANCE: NexaDatabase? = null

        fun getInstance(context: Context): NexaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexa_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
