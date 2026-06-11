package com.nexa.ai.data.local

import androidx.room.*

@Entity(tableName = "iot_devices")
data class IoTDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: String = "unknown",
    val deviceType: String = "unknown",
    val protocol: String = "wifi",
    val endpoint: String? = null,
    val roomId: String? = null,
    val state: String? = null,
    val lastState: String? = null,
    val isOnline: Boolean = false,
    val capabilities: String = "",
    val lastSeen: Long = System.currentTimeMillis()
)

@Dao
interface IoTDeviceDao {
    @Query("SELECT * FROM iot_devices")
    suspend fun getAll(): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): IoTDeviceEntity?

    @Query("SELECT * FROM iot_devices WHERE type = :type")
    suspend fun getByType(type: String): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE roomId = :roomId")
    suspend fun getByRoom(roomId: String): List<IoTDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: IoTDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: IoTDeviceEntity)

    @Update
    suspend fun update(device: IoTDeviceEntity)

    @Query("UPDATE iot_devices SET state = :state, lastState = :state, lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateState(deviceId: String, state: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE iot_devices SET state = :state, lastState = :state WHERE deviceId = :deviceId")
    suspend fun updateState(deviceId: String, state: String)

    @Transaction
    suspend fun updateState(deviceId: String, state: String, source: String) {
        updateState(deviceId, state)
    }

    @Delete
    suspend fun delete(device: IoTDeviceEntity)

    @Query("DELETE FROM iot_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)
}
