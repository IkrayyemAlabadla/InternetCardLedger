package com.alabadla.internetcardledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alabadla.internetcardledger.data.local.SyncStatus

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sale_id"]),
        Index(value = ["customer_id", "is_deleted", "paid_at"]),
        Index(value = ["is_deleted", "paid_at"]),
        Index(value = ["sync_status", "updated_at"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "customer_id")
    val customerId: String,
    @ColumnInfo(name = "sale_id")
    val saleId: String?,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    @ColumnInfo(name = "paid_at")
    val paidAt: Long,
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
