package com.nexa.ai.data.local

import androidx.room.*

@Entity(tableName = "iot_devices")
data class IoTDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: String,
    val protocol: String,
    val endpoint: String,
    val roomId: String? = null,
    val lastState: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

@Dao
interface IoTDeviceDao {
    @Query("SELECT * FROM iot_devices")
    suspend fun getAll(): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE type = :type")
    suspend fun getByType(type: String): List<IoTDeviceEntity>

    @Query("SELECT * FROM iot_devices WHERE roomId = :roomId")
    suspend fun getByRoom(roomId: String): List<IoTDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: IoTDeviceEntity)

    @Delete
    suspend fun delete(device: IoTDeviceEntity)

    @Query("DELETE FROM iot_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)
}
