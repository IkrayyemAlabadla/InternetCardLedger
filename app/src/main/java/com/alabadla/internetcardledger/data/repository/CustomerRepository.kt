package com.alabadla.internetcardledger.data.repository

import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.dao.CustomerDao
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CustomerRepository(
    private val customerDao: CustomerDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val syncEngine: SyncEngine? = null,
) {
    fun observeAllCustomers(): Flow<List<CustomerEntity>> = customerDao.observeAll()

    fun observeCustomers(): Flow<List<CustomerEntity>> = customerDao.observeActive()

    fun observeCustomers(searchQuery: String): Flow<List<CustomerEntity>> =
        customerDao.observeActiveMatching(searchQuery.trim())

    fun observeCustomer(id: String): Flow<CustomerEntity?> = customerDao.observeById(id)

    fun observePendingSync(): Flow<List<CustomerEntity>> = customerDao.observePendingSync()

    suspend fun addCustomer(name: String, phone: String? = null, notes: String? = null): String {
        require(name.isNotBlank()) { "Customer name cannot be blank" }
        val timestamp = currentTimeMillis()
        val id = newId()
        val customer = CustomerEntity(
            id = id,
            name = name.trim(),
            phone = phone?.trim()?.takeIf(String::isNotEmpty),
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = timestamp,
            updatedAt = timestamp,
            createdByDeviceId = deviceIdProvider.getDeviceId(),
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )
        customerDao.upsert(customer)
        syncEngine?.logChange(id, "CUSTOMER", "CREATE", SyncJsonCodec.customerToJson(customer))
        return id
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        require(customer.name.isNotBlank()) { "Customer name cannot be blank" }
        val updated = customer.copy(
            name = customer.name.trim(),
            phone = customer.phone?.trim()?.takeIf(String::isNotEmpty),
            notes = customer.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        customerDao.upsert(updated)
        syncEngine?.logChange(updated.id, "CUSTOMER", "UPDATE", SyncJsonCodec.customerToJson(updated))
    }

    suspend fun deleteCustomer(id: String): Boolean {
        val timestamp = currentTimeMillis()
        val result = customerDao.softDelete(id, timestamp) > 0
        if (result) {
            customerDao.getById(id)?.let { deletedCustomer ->
                syncEngine?.logChange(id, "CUSTOMER", "DELETE", SyncJsonCodec.customerToJson(deletedCustomer))
            }
        }
        return result
    }
}
