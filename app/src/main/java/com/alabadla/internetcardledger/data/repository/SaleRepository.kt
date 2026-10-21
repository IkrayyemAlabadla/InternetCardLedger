package com.alabadla.internetcardledger.data.repository

import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.dao.SaleDao
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SaleRepository(
    private val saleDao: SaleDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val syncEngine: SyncEngine? = null,
) {
    fun observeAllSales(): Flow<List<SaleEntity>> = saleDao.observeAll()

    fun observeSales(): Flow<List<SaleEntity>> = saleDao.observeActive()

    fun observeSales(searchQuery: String): Flow<List<SaleEntity>> =
        saleDao.observeActiveMatching(searchQuery.trim())

    fun observeSalesForCustomer(customerId: String): Flow<List<SaleEntity>> =
        saleDao.observeForCustomer(customerId)

    fun observeSale(id: String): Flow<SaleEntity?> = saleDao.observeById(id)

    fun observePendingSync(): Flow<List<SaleEntity>> = saleDao.observePendingSync()

    suspend fun addSale(
        customerId: String?,
        description: String,
        quantity: Int,
        unitPriceMinor: Long,
        soldAt: Long = currentTimeMillis(),
        notes: String? = null,
    ): String {
        require(description.isNotBlank()) { "Sale description cannot be blank" }
        require(quantity > 0) { "Sale quantity must be positive" }
        require(unitPriceMinor >= 0) { "Unit price cannot be negative" }
        Math.multiplyExact(quantity.toLong(), unitPriceMinor)
        val timestamp = currentTimeMillis()
        val id = newId()
        val sale = SaleEntity(
            id = id,
            customerId = customerId,
            description = description.trim(),
            quantity = quantity,
            unitPriceMinor = unitPriceMinor,
            soldAt = soldAt,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = timestamp,
            updatedAt = timestamp,
            createdByDeviceId = deviceIdProvider.getDeviceId(),
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )
        saleDao.upsert(sale)
        syncEngine?.logChange(id, "SALE", "CREATE", SyncJsonCodec.saleToJson(sale))
        return id
    }

    suspend fun updateSale(sale: SaleEntity) {
        require(sale.description.isNotBlank()) { "Sale description cannot be blank" }
        require(sale.quantity > 0) { "Sale quantity must be positive" }
        require(sale.unitPriceMinor >= 0) { "Unit price cannot be negative" }
        Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor)
        val updated = sale.copy(
            description = sale.description.trim(),
            notes = sale.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        saleDao.upsert(updated)
        syncEngine?.logChange(updated.id, "SALE", "UPDATE", SyncJsonCodec.saleToJson(updated))
    }

    suspend fun deleteSale(id: String): Boolean {
        val timestamp = currentTimeMillis()
        val result = saleDao.softDelete(id, timestamp) > 0
        if (result) {
            saleDao.getById(id)?.let { deletedSale ->
                syncEngine?.logChange(id, "SALE", "DELETE", SyncJsonCodec.saleToJson(deletedSale))
            }
        }
        return result
    }
}
