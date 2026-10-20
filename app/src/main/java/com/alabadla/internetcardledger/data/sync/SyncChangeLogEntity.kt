package com.alabadla.internetcardledger.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alabadla.internetcardledger.data.local.SyncStatus

@Entity(
    tableName = "sync_change_logs",
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["device_id", "sequence_number"], unique = true),
        Index(value = ["created_at"]),
    ],
)
data class SyncChangeLogEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
)
