package com.alabadla.internetcardledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers
        WHERE is_deleted = 0
          AND (
            :query = ''
            OR name LIKE '%' || :query || '%' COLLATE NOCASE
            OR phone LIKE '%' || :query || '%' COLLATE NOCASE
            OR notes LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeActiveMatching(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE sync_status != :syncedStatus ORDER BY updated_at")
    fun observePendingSync(syncedStatus: SyncStatus = SyncStatus.SYNCED): Flow<List<CustomerEntity>>

    @Upsert
    suspend fun upsert(customer: CustomerEntity)

    @Query(
        """
        UPDATE customers
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
