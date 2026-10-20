package com.alabadla.internetcardledger.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["status"]),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "main_version_json")
    val mainVersionJson: String,
    @ColumnInfo(name = "secondary_version_json")
    val secondaryVersionJson: String,
    @ColumnInfo(name = "main_device_id")
    val mainDeviceId: String,
    @ColumnInfo(name = "secondary_device_id")
    val secondaryDeviceId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val status: String,
)
