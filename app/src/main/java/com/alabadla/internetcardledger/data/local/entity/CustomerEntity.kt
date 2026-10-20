package com.alabadla.internetcardledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alabadla.internetcardledger.data.local.SyncStatus

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["is_deleted", "name"]),
        Index(value = ["sync_status", "updated_at"]),
    ],
)
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val phone: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "created_by_device_id")
    val createdByDeviceId: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean,
)
