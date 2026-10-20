package com.alabadla.internetcardledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE is_deleted = 0 ORDER BY sold_at DESC, created_at DESC")
    fun observeActive(): Flow<List<SaleEntity>>

    @Query(
        """
        SELECT * FROM sales
        WHERE is_deleted = 0
          AND (
            :query = ''
            OR description LIKE '%' || :query || '%' COLLATE NOCASE
            OR notes LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY sold_at DESC, created_at DESC
        """,
    )
    fun observeActiveMatching(query: String): Flow<List<SaleEntity>>

    @Query(
        """
        SELECT * FROM sales
        WHERE customer_id = :customerId AND is_deleted = 0
        ORDER BY sold_at DESC, created_at DESC
        """,
    )
    fun observeForCustomer(customerId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SaleEntity?>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE sync_status != :syncedStatus ORDER BY updated_at")
    fun observePendingSync(syncedStatus: SyncStatus = SyncStatus.SYNCED): Flow<List<SaleEntity>>

    @Upsert
    suspend fun upsert(sale: SaleEntity)

    @Query(
        """
        UPDATE sales
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
