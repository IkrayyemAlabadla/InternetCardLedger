package com.alabadla.internetcardledger.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_sessions")
data class SyncSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "peer_device_id")
    val peerDeviceId: String,
    @ColumnInfo(name = "peer_device_name")
    val peerDeviceName: String,
    @ColumnInfo(name = "peer_device_role")
    val peerDeviceRole: DeviceRole,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long,
    val status: String,
    @ColumnInfo(name = "changes_received_count")
    val changesReceivedCount: Int,
    @ColumnInfo(name = "changes_sent_count")
    val changesSentCount: Int,
    @ColumnInfo(name = "conflicts_count")
    val conflictsCount: Int,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
)
