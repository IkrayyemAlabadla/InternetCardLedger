package com.alabadla.internetcardledger.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "approved_devices")
data class ApprovedDeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    @ColumnInfo(name = "approved_at")
    val approvedAt: Long,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long?,
)
