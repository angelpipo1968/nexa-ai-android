package com.nexa.ai.data.local

import androidx.room.*

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val speakerId: String,
    val speakerName: String?,
    val avgPitch: Float,
    val pitchVariance: Float,
    val avgEnergy: Float,
    val energyVariance: Float,
    val avgZCR: Float,
    val avgSpectralCentroid: Float,
    val spectralFeatures: String, // Store JSON features as string
    val detectedLanguage: String,
    val languageConfidence: Float,
    val speakingRate: Float,
    val sampleCount: Int,
    val lastUpdated: Long,
    val createdAt: Long
)

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles WHERE speakerId = :speakerId")
    suspend fun getBySpeakerId(speakerId: String): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles")
    suspend fun getAll(): List<VoiceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VoiceProfileEntity)
}

@Entity(tableName = "emotions")
data class EmotionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val primaryEmotion: String,
    val secondaryEmotion: String? = null,
    val intensity: Float,
    val confidence: Float,
    val source: String,
    val context: String,
    val isSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface EmotionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(emotion: EmotionEntity)

    @Query("SELECT * FROM emotions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<EmotionEntity>

    @Query("SELECT * FROM emotions WHERE timestamp > :cutoff AND isSynced = 0")
    suspend fun getUnsynced(cutoff: Long): List<EmotionEntity>

    @Query("UPDATE emotions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

@Entity(tableName = "memory_facts", indices = [Index("fact", unique = true)])
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val category: String,
    val source: String,
    val confidence: Float,
    val accessCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: MemoryFactEntity)

    @Query("SELECT * FROM memory_facts")
    suspend fun getAll(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryFactEntity?

    @Query("SELECT * FROM memory_facts WHERE fact LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int = 10): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY confidence DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<MemoryFactEntity>

    @Transaction
    suspend fun incrementAccess(id: Long) {
        val fact = getById(id)
        if (fact != null) {
            upsert(fact.copy(accessCount = fact.accessCount + 1, timestamp = System.currentTimeMillis()))
        }
    }
}
