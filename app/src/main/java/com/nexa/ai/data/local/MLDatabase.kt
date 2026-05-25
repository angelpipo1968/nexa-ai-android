package com.nexa.ai.data.local

import android.content.Context
import androidx.room.*

// ═══════════════════════════════════════
//  CHAT ROOM ENTITIES — Sessions & Messages
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
//  ML ROOM ENTITIES — On-device ML Storage
// ═══════════════════════════════════════

/**
 * Stores detected emotions for offline learning and pattern analysis.
 * Synced with server-side ML engine when online.
 */
@Entity(tableName = "emotions", indices = [Index("timestamp")])
data class EmotionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val primaryEmotion: String,       // joy, sadness, anger, fear, surprise, love, disgust, neutral
    val secondaryEmotion: String? = null,
    val intensity: Float,             // 0.0 - 1.0
    val confidence: Float,            // 0.0 - 1.0
    val source: String,               // "voice", "text", "sensor", "face"
    val context: String? = null,      // What triggered this emotion
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false       // Whether synced to server
)

/**
 * Stores learning signals from reinforcement learning.
 * Positive/negative feedback that improves AI responses over time.
 */
@Entity(tableName = "learning_signals", indices = [Index("category"), Index("timestamp")])
data class LearningSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                 // "positive", "negative", "neutral"
    val category: String,             // "response_quality", "tool_accuracy", "conversation_style", "topic_interest", "emotion_match"
    val value: Float,                 // -1.0 to 1.0
    val context: String? = null,      // What the signal was about
    val userMessage: String? = null,  // The user message that generated this signal
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

/**
 * Stores user preferences and habits learned over time.
 * Enables personalization even when offline.
 */
@Entity(tableName = "user_preferences", indices = [Index("category")])
data class UserPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,             // "communication_style", "topic", "response_format", "voice", "emotion_pattern"
    val key: String,                  // Specific preference key
    val value: String,                // Preference value
    val confidence: Float = 0.5f,     // How confident we are about this preference
    val source: String = "learned",   // "learned", "explicit", "inferred"
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Stores sensor data for context-aware AI responses.
 * Includes accelerometer, gyroscope, light, battery, etc.
 */
@Entity(tableName = "sensor_data", indices = [Index("sensorType"), Index("timestamp")])
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorType: String,           // "accelerometer", "gyroscope", "light", "battery", "location", "bluetooth", "wifi"
    val value: Float,                 // Primary sensor value
    val extraData: String? = null,    // JSON with additional sensor values
    val context: String? = null,      // Derived context (e.g., "walking", "driving", "sleeping")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Stores IoT device states and commands.
 * Enables smart home control and device interaction.
 */
@Entity(tableName = "iot_devices", indices = [Index("deviceType")])
data class IoTDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,                 // "Living Room Light", "Thermostat"
    val deviceType: String,           // "light", "thermostat", "lock", "camera", "speaker", "switch", "sensor"
    val protocol: String,             // "mqtt", "http", "bluetooth", "zigbee", "wifi"
    val endpoint: String? = null,     // URL or MQTT topic
    val state: String = "off",        // Current state (on/off, temperature, etc.)
    val capabilities: String = "{}",  // JSON: supported features
    val lastCommand: String? = null,  // Last sent command
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false
)

/**
 * Stores conversation memory facts.
 * Long-term memory that persists across sessions.
 */
@Entity(tableName = "memory_facts", indices = [Index("category"), Index("timestamp")])
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,                 // The actual fact/insight
    val category: String,             // "preference", "habit", "personal", "relationship", "work"
    val source: String,               // "conversation", "explicit", "inferred"
    val confidence: Float = 0.5f,
    val accessCount: Int = 0,         // How many times this fact was used
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

/**
 * Stores voice profiles for speaker identification and personalization.
 * Enables the system to recognize and adapt to individual speakers.
 */
@Entity(tableName = "voice_profiles", indices = [Index("speakerId"), Index("detectedLanguage")])
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speakerId: String,              // Unique speaker identifier
    val speakerName: String? = null,    // Optional display name
    val avgPitch: Float = 0f,           // Average fundamental frequency (Hz)
    val pitchVariance: Float = 0f,      // Pitch variation (Hz²)
    val avgEnergy: Float = 0f,          // Average RMS energy
    val energyVariance: Float = 0f,     // Energy variation
    val avgZCR: Float = 0f,             // Average zero-crossing rate
    val avgSpectralCentroid: Float = 0f,// Brightness of voice
    val spectralFeatures: String = "{}",// JSON: 13-band spectral averages
    val detectedLanguage: String = "unknown", // "en", "es", "mixed", "unknown"
    val languageConfidence: Float = 0f,  // 0.0 - 1.0
    val speakingRate: Float = 0f,       // Syllables per second estimate
    val sampleCount: Int = 0,          // How many samples contributed to this profile
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Activity log for pattern recognition.
 * Tracks what the user does and when for predictive features.
 */
@Entity(tableName = "activity_log", indices = [Index("activityType"), Index("timestamp")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,         // "app_open", "voice_command", "search", "image_gen", "setting_change"
    val details: String? = null,      // JSON with activity details
    val dayOfWeek: Int,               // 1-7 (Mon-Sun)
    val hourOfDay: Int,               // 0-23
    val timestamp: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════
//  DAOs — Data Access Objects
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

    @Transaction
    suspend fun saveAll(sessions: List<SessionEntity>, messages: List<MessageEntity>) {
        deleteAllMessages()
        deleteAll()
        sessions.forEach { insertSession(it) }
        if (messages.isNotEmpty()) insertMessages(messages)
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

@Dao
interface EmotionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emotion: EmotionEntity): Long

    @Query("SELECT * FROM emotions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<EmotionEntity>

    @Query("SELECT primaryEmotion, COUNT(*) as count, AVG(intensity) as avgIntensity FROM emotions WHERE timestamp > :since GROUP BY primaryEmotion ORDER BY count DESC")
    suspend fun getEmotionDistribution(since: Long): List<EmotionDistribution>

    @Query("SELECT * FROM emotions WHERE timestamp > :since AND synced = 0")
    suspend fun getUnsynced(since: Long): List<EmotionEntity>

    @Query("UPDATE emotions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM emotions WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class EmotionDistribution(
    val primaryEmotion: String,
    val count: Int,
    val avgIntensity: Float
)

@Dao
interface LearningSignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: LearningSignalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(signals: List<LearningSignalEntity>)

    @Query("SELECT category, AVG(value) as avgValue, COUNT(*) as count FROM learning_signals WHERE timestamp > :since GROUP BY category")
    suspend fun getAggregateScores(since: Long): List<CategoryScore>

    @Query("SELECT * FROM learning_signals WHERE timestamp > :since AND synced = 0")
    suspend fun getUnsynced(since: Long): List<LearningSignalEntity>

    @Query("UPDATE learning_signals SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM learning_signals WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class CategoryScore(
    val category: String,
    val avgValue: Float,
    val count: Int
)

@Dao
interface UserPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: UserPreferenceEntity)

    @Query("SELECT * FROM user_preferences WHERE category = :category")
    suspend fun getByCategory(category: String): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun get(category: String, key: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreferenceEntity>

    @Query("DELETE FROM user_preferences WHERE category = :category AND `key` = :key")
    suspend fun delete(category: String, key: String)
}

@Dao
interface SensorDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: SensorDataEntity): Long

    @Query("SELECT * FROM sensor_data WHERE sensorType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(type: String, limit: Int = 10): List<SensorDataEntity>

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllLatest(limit: Int = 50): List<SensorDataEntity>

    @Query("DELETE FROM sensor_data WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface IoTDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: IoTDeviceEntity)

    @Query("SELECT * FROM iot_devices ORDER BY name ASC")
    suspend fun getAll(): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE deviceType = :type")
    suspend fun getByType(type: String): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE deviceId = :id")
    suspend fun getById(id: String): IoTDeviceEntity?

    @Query("UPDATE iot_devices SET state = :state, lastCommand = :command, lastSeen = :timestamp WHERE deviceId = :id")
    suspend fun updateState(id: String, state: String, command: String?, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM iot_devices WHERE deviceId = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: MemoryFactEntity): Long

    @Query("SELECT * FROM memory_facts WHERE category = :category ORDER BY confidence DESC")
    suspend fun getByCategory(category: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY confidence DESC, accessCount DESC LIMIT :limit")
    suspend fun getTop(limit: Int = 20): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE fact LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int = 10): List<MemoryFactEntity>

    @Query("UPDATE memory_facts SET accessCount = accessCount + 1 WHERE id = :id")
    suspend fun incrementAccess(id: Long)

    @Query("DELETE FROM memory_facts WHERE timestamp < :before AND confidence < :minConfidence")
    suspend fun deleteOldAndUnimportant(before: Long, minConfidence: Float = 0.3f)
}

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActivityLogEntity): Long

    @Query("SELECT activityType, COUNT(*) as count FROM activity_log WHERE timestamp > :since GROUP BY activityType ORDER BY count DESC")
    suspend fun getActivityCounts(since: Long): List<ActivityCount>

    @Query("SELECT hourOfDay, COUNT(*) as count FROM activity_log WHERE timestamp > :since GROUP BY hourOfDay ORDER BY hourOfDay")
    suspend fun getHourlyPattern(since: Long): List<HourlyActivity>

    @Query("DELETE FROM activity_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class ActivityCount(
    val activityType: String,
    val count: Int
)

data class HourlyActivity(
    val hourOfDay: Int,
    val count: Int
)

@Dao
interface VoiceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VoiceProfileEntity): Long

    @Query("SELECT * FROM voice_profiles WHERE speakerId = :speakerId LIMIT 1")
    suspend fun getBySpeakerId(speakerId: String): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<VoiceProfileEntity>

    @Query("SELECT * FROM voice_profiles WHERE detectedLanguage = :language")
    suspend fun getByLanguage(language: String): List<VoiceProfileEntity>

    @Query("DELETE FROM voice_profiles WHERE speakerId = :speakerId")
    suspend fun delete(speakerId: String)

    @Query("DELETE FROM voice_profiles WHERE lastUpdated < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ═══════════════════════════════════════
//  ML DATABASE — Room Database
// ═══════════════════════════════════════

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        EmotionEntity::class,
        LearningSignalEntity::class,
        UserPreferenceEntity::class,
        SensorDataEntity::class,
        IoTDeviceEntity::class,
        MemoryFactEntity::class,
        ActivityLogEntity::class,
        VoiceProfileEntity::class,
        PendingMessageEntity::class,
        CachedResponseEntity::class,
        CachedSearchEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun emotionDao(): EmotionDao
    abstract fun learningSignalDao(): LearningSignalDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun sensorDataDao(): SensorDataDao
    abstract fun iotDeviceDao(): IoTDeviceDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun cachedResponseDao(): CachedResponseDao
    abstract fun cachedSearchDao(): CachedSearchDao

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
