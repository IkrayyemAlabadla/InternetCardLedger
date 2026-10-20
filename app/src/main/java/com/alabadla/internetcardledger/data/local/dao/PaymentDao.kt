package com.alabadla.internetcardledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE is_deleted = 0 ORDER BY paid_at DESC, created_at DESC")
    fun observeActive(): Flow<List<PaymentEntity>>

    @Query(
        """
        SELECT * FROM payments
        WHERE customer_id = :customerId AND is_deleted = 0
        ORDER BY paid_at DESC, created_at DESC
        """,
    )
    fun observeForCustomer(customerId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE sync_status != :syncedStatus ORDER BY updated_at")
    fun observePendingSync(syncedStatus: SyncStatus = SyncStatus.SYNCED): Flow<List<PaymentEntity>>

    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    @Query(
        """
        UPDATE payments
        SET is_deleted = 1, updated_at = :updatedAt, sync_status = :syncStatus
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        updatedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    ): Int
}
