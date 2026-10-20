package com.alabadla.internetcardledger.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_identities")
data class DeviceIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    val role: DeviceRole,
    @ColumnInfo(name = "last_sequence_number")
    val lastSequenceNumber: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
