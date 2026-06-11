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
    val userMessage: String? = null,
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

    @Query("SELECT * FROM learning_signals")
    suspend fun getAll(): List<LearningSignalEntity>

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

    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE category = :category")
    suspend fun getByCategory(category: String): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun get(category: String, key: String): UserPreferenceEntity?
}

// ─── ACTIVITY LOGS ───

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val details: String? = null,
    val dayOfWeek: Int = 0,
    val hourOfDay: Int = 0,
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

    @Query("SELECT * FROM activity_logs")
    suspend fun getAll(): List<ActivityLogEntity>

    @Query("SELECT activityType, SUM(count) as count FROM activity_logs WHERE timestamp > :cutoff GROUP BY activityType")
    suspend fun getActivityCounts(cutoff: Long): List<ActivityCount>

    @Query("SELECT * FROM activity_logs WHERE activityType = :type")
    suspend fun getByType(type: String): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs WHERE timestamp > :cutoff ORDER BY timestamp DESC")
    suspend fun getRecent(cutoff: Long): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs WHERE activityType LIKE '%' || :query || '%' OR details LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ActivityLogEntity>

    @Transaction
    suspend fun incrementAccess(activityType: String) {
        val existing = getByType(activityType).firstOrNull()
        if (existing != null) {
            insert(existing.copy(count = existing.count + 1, timestamp = System.currentTimeMillis()))
        } else {
            insert(ActivityLogEntity(activityType = activityType))
        }
    }

    @Query("SELECT hourOfDay, SUM(count) as count FROM activity_logs WHERE activityType = :type GROUP BY hourOfDay")
    suspend fun getHourlyPattern(type: String): List<HourPattern>

    @Query("SELECT hourOfDay, SUM(count) as count FROM activity_logs WHERE timestamp > :cutoff GROUP BY hourOfDay")
    suspend fun getHourlyPattern(cutoff: Long): List<HourPattern>

    @Query("DELETE FROM activity_logs WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM activity_logs WHERE timestamp > :cutoff LIMIT :limit")
    suspend fun getUnsynced(cutoff: Long, limit: Int): List<ActivityLogEntity>

    @Query("UPDATE activity_logs SET count = count + 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT activityType, SUM(count) as totalCount FROM activity_logs GROUP BY activityType ORDER BY totalCount DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<TopActivity>
}

data class HourPattern(val hourOfDay: Int, val count: Int)
data class TopActivity(val activityType: String, val totalCount: Int)

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

    @Query("DELETE FROM sensor_data WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
