package com.alabadla.internetcardledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alabadla.internetcardledger.data.local.SyncStatus

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["customer_id", "is_deleted", "sold_at"]),
        Index(value = ["is_deleted", "sold_at"]),
        Index(value = ["sync_status", "updated_at"]),
    ],
)
data class SaleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "customer_id")
    val customerId: String?,
    val description: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price_minor")
    val unitPriceMinor: Long,
    @ColumnInfo(name = "sold_at")
    val soldAt: Long,
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
    @ColumnInfo(name = "card_type", defaultValue = "'SIX_HOURS'")
    val cardType: CardType = CardType.SIX_HOURS,
    @ColumnInfo(name = "payment_status", defaultValue = "'UNPAID'")
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    @ColumnInfo(name = "paid_amount_minor", defaultValue = "0")
    val paidAmountMinor: Long = 0L,
)
