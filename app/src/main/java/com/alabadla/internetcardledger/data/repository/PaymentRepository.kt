package com.alabadla.internetcardledger.data.repository

import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.dao.PaymentDao
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val syncEngine: SyncEngine? = null,
) {
    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.observeAll()

    fun observePayments(): Flow<List<PaymentEntity>> = paymentDao.observeActive()

    fun observePaymentsForCustomer(customerId: String): Flow<List<PaymentEntity>> =
        paymentDao.observeForCustomer(customerId)

    fun observePayment(id: String): Flow<PaymentEntity?> = paymentDao.observeById(id)

    fun observePendingSync(): Flow<List<PaymentEntity>> = paymentDao.observePendingSync()

    suspend fun addPayment(
        customerId: String,
        saleId: String? = null,
        amountMinor: Long,
        paidAt: Long = currentTimeMillis(),
        notes: String? = null,
    ): String {
        require(customerId.isNotBlank()) { "Customer id cannot be blank" }
        require(amountMinor > 0) { "Payment amount must be positive" }
        val timestamp = currentTimeMillis()
        val id = newId()
        val payment = PaymentEntity(
            id = id,
            customerId = customerId,
            saleId = saleId,
            amountMinor = amountMinor,
            paidAt = paidAt,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = timestamp,
            updatedAt = timestamp,
            createdByDeviceId = deviceIdProvider.getDeviceId(),
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )
        paymentDao.upsert(payment)
        syncEngine?.logChange(id, "PAYMENT", "CREATE", SyncJsonCodec.paymentToJson(payment))
        return id
    }

    suspend fun updatePayment(payment: PaymentEntity) {
        require(payment.customerId.isNotBlank()) { "Customer id cannot be blank" }
        require(payment.amountMinor > 0) { "Payment amount must be positive" }
        val updated = payment.copy(
            notes = payment.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        paymentDao.upsert(updated)
        syncEngine?.logChange(updated.id, "PAYMENT", "UPDATE", SyncJsonCodec.paymentToJson(updated))
    }

    suspend fun deletePayment(id: String): Boolean {
        val timestamp = currentTimeMillis()
        val result = paymentDao.softDelete(id, timestamp) > 0
        if (result) {
            paymentDao.getById(id)?.let { deletedPayment ->
                syncEngine?.logChange(id, "PAYMENT", "DELETE", SyncJsonCodec.paymentToJson(deletedPayment))
            }
        }
        return result
    }
}
