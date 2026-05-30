package com.nexa.ai.data.local

import androidx.room.*

// ─── LEARNING SIGNAL ENTITIES ───

@Entity(tableName = "learning_signals")
data class LearningSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val category: String,
    val value: Float,
    val context: String,
    val isSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AggregateScore(
    val category: String,
    val avgValue: Float,
    val count: Int
)

@Dao
interface LearningSignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: LearningSignalEntity)

    @Query("SELECT category, AVG(value) as avgValue, COUNT(*) as count FROM learning_signals WHERE timestamp > :cutoff GROUP BY category")
    suspend fun getAggregateScores(cutoff: Long): List<AggregateScore>

    @Query("SELECT * FROM learning_signals WHERE timestamp > :cutoff AND isSynced = 0")
    suspend fun getUnsynced(cutoff: Long): List<LearningSignalEntity>

    @Query("UPDATE learning_signals SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}

// ─── USER PREFERENCES ───

@Entity(tableName = "user_preferences", indices = [Index(value = ["category", "key"], unique = true)])
data class UserPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val confidence: Float,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface UserPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: UserPreferenceEntity)

    @Query("SELECT * FROM user_preferences WHERE category = :category")
    suspend fun getByCategory(category: String): List<UserPreferenceEntity>
}

// ─── ACTIVITY LOGS ───

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val count: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActivityCount(
    val activityType: String,
    val count: Int
)

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity)

    @Query("SELECT activityType, SUM(count) as count FROM activity_logs WHERE timestamp > :cutoff GROUP BY activityType")
    suspend fun getActivityCounts(cutoff: Long): List<ActivityCount>
}

// ─── SENSOR DATA LOGS ───

@Entity(tableName = "sensor_data")
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorType: String,
    val value: Float,
    val extraData: String? = null,
    val context: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SensorDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: SensorDataEntity)

    @Query("SELECT * FROM sensor_data WHERE sensorType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(type: String, limit: Int): List<SensorDataEntity>
}
