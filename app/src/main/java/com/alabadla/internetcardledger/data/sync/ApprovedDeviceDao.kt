package com.alabadla.internetcardledger.data.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovedDeviceDao {

    @Query("SELECT * FROM approved_devices ORDER BY approved_at DESC")
    fun observeAllApprovedDevices(): Flow<List<ApprovedDeviceEntity>>

    @Query("SELECT * FROM approved_devices ORDER BY approved_at DESC")
    suspend fun getAllApprovedDevices(): List<ApprovedDeviceEntity>

    @Query("SELECT * FROM approved_devices WHERE device_id = :deviceId LIMIT 1")
    suspend fun getApprovedDevice(deviceId: String): ApprovedDeviceEntity?

    @Upsert
    suspend fun upsertApprovedDevice(device: ApprovedDeviceEntity)

    @Query("DELETE FROM approved_devices WHERE device_id = :deviceId")
    suspend fun deleteApprovedDevice(deviceId: String): Int
}
