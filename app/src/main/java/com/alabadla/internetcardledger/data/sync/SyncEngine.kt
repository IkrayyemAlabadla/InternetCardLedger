package com.alabadla.internetcardledger.data.sync

import androidx.room.withTransaction
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import java.util.UUID

class InvalidSyncBatchException(message: String) : Exception(message)

data class TwoWaySyncResult(
    val session: SyncSessionEntity,
    val responseBatch: SyncBatch,
)

class SyncEngine(
    private val database: LedgerDatabase,
    private val deviceIdProvider: DeviceIdProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val syncDao = database.syncDao()
    private val customerDao = database.customerDao()
    private val saleDao = database.saleDao()
    private val paymentDao = database.paymentDao()

    suspend fun getOrCreateLocalIdentity(
        deviceName: String? = null,
        role: DeviceRole = DeviceRole.SECONDARY,
    ): DeviceIdentityEntity {
        val deviceId = deviceIdProvider.getDeviceId()
        val existing = syncDao.getDeviceIdentity(deviceId)
        if (existing != null) {
            if (existing.role == DeviceRole.MAIN && role == DeviceRole.SECONDARY) {
                throw IllegalStateException("لا يمكن تغيير دور الجهاز الرئيسي إلى جهاز فرعي تلقائياً.")
            }
            return existing
        }

        val name = deviceName?.trim()?.takeIf(String::isNotEmpty)
            ?: if (role == DeviceRole.MAIN) "هاتف العم الرئيسي" else "هاتف فرعي"

        val identity = DeviceIdentityEntity(
            deviceId = deviceId,
            deviceName = name,
            role = role,
            lastSequenceNumber = 0L,
            updatedAt = currentTimeMillis(),
        )
        syncDao.upsertDeviceIdentity(identity)
        return identity
    }

    suspend fun updateDeviceRole(newRole: DeviceRole, newName: String? = null) {
        val identity = getOrCreateLocalIdentity()
        if (identity.role == DeviceRole.MAIN && newRole == DeviceRole.SECONDARY) {
            throw IllegalStateException("لا يمكن تغيير دور الجهاز الرئيسي إلى جهاز فرعي تلقائياً.")
        }
        val name = newName?.trim()?.takeIf(String::isNotEmpty) ?: identity.deviceName
        val updated = identity.copy(
            role = newRole,
            deviceName = name,
            updatedAt = currentTimeMillis(),
        )
        syncDao.upsertDeviceIdentity(updated)
    }

    suspend fun nextSequenceNumber(): Long {
        val identity = getOrCreateLocalIdentity()
        val nextSeq = identity.lastSequenceNumber + 1L
        val updated = identity.copy(
            lastSequenceNumber = nextSeq,
            updatedAt = currentTimeMillis(),
        )
        syncDao.upsertDeviceIdentity(updated)
        return nextSeq
    }

    suspend fun logChange(
        recordId: String,
        entityType: String,
        operationType: String,
        payloadJson: String,
    ): SyncChangeLogEntity {
        val identity = getOrCreateLocalIdentity()
        val seq = nextSequenceNumber()
        val timestamp = currentTimeMillis()
        val log = SyncChangeLogEntity(
            id = newId(),
            recordId = recordId,
            entityType = entityType,
            operationType = operationType,
            deviceId = identity.deviceId,
            sequenceNumber = seq,
            createdAt = timestamp,
            updatedAt = timestamp,
            syncStatus = SyncStatus.PENDING,
            payloadJson = payloadJson,
        )
        syncDao.upsertChangeLog(log)
        return log
    }

    suspend fun generateSyncBatch(
        lastReceivedSequence: Long = 0L,
    ): SyncBatch {
        val identity = getOrCreateLocalIdentity()
        val changes = syncDao.getChangeLogsAfterSequence(identity.deviceId, lastReceivedSequence)
        val maxSeq = changes.maxOfOrNull { it.sequenceNumber } ?: lastReceivedSequence

        val recordIds = changes.map { it.recordId }.toSet()

        val customers = mutableListOf<CustomerEntity>()
        val sales = mutableListOf<SaleEntity>()
        val payments = mutableListOf<PaymentEntity>()

        for (recordId in recordIds) {
            customerDao.getById(recordId)?.let { customers.add(it) }
            saleDao.getById(recordId)?.let { sales.add(it) }
            paymentDao.getById(recordId)?.let { payments.add(it) }
        }

        return SyncBatch(
            batchId = newId(),
            senderDeviceId = identity.deviceId,
            senderDeviceName = identity.deviceName,
            senderRole = identity.role,
            createdAt = currentTimeMillis(),
            maxSequenceNumber = maxSeq,
            changes = changes,
            customers = customers,
            sales = sales,
            payments = payments,
        )
    }

    suspend fun processIncomingBatch(batch: SyncBatch): SyncSessionEntity {
        return database.withTransaction {
            val startedAt = currentTimeMillis()

            // Idempotency check: if session with batchId already processed successfully
            val existingSession = syncDao.getSessionById(batch.batchId)
            if (existingSession != null) {
                return@withTransaction existingSession
            }

            // Batch validation
            if (batch.senderDeviceId.isBlank()) {
                throw InvalidSyncBatchException("معرّف الجهاز المرسل مفقود أو غير صالح.")
            }

            // Validate all entities in batch
            batch.customers.forEach { customer ->
                if (customer.name.isBlank()) {
                    throw InvalidSyncBatchException("تحتوي المزامنة على زبون بلا اسم.")
                }
            }
            batch.sales.forEach { sale ->
                if (sale.description.isBlank() || sale.quantity <= 0 || sale.unitPriceMinor < 0L) {
                    throw InvalidSyncBatchException("تحتوي المزامنة على بيع غير صالح.")
                }
                if (runCatching { Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor) }.isFailure) {
                    throw InvalidSyncBatchException("تحتوي المزامنة على قيمة بيع أكبر من الحد المسموح.")
                }
            }
            batch.payments.forEach { payment ->
                if (payment.amountMinor <= 0L) {
                    throw InvalidSyncBatchException("تحتوي المزامنة على دفعة غير صالحة.")
                }
            }

            val localIdentity = getOrCreateLocalIdentity()
            var conflictsCount = 0
            var changesReceived = 0

            // Merge Customers
            batch.customers.forEach { incoming ->
                val local = customerDao.getById(incoming.id)
                if (local == null) {
                    customerDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                    changesReceived++
                } else if (!isCustomerEqual(local, incoming)) {
                    if (local.syncStatus == SyncStatus.PENDING) {
                        // Simultaneous edit conflict
                        createConflict(
                            recordId = incoming.id,
                            entityType = "CUSTOMER",
                            localJson = SyncJsonCodec.customerToJson(local),
                            incomingJson = SyncJsonCodec.customerToJson(incoming),
                            localDeviceId = localIdentity.deviceId,
                            incomingDeviceId = batch.senderDeviceId,
                            incomingDeviceRole = batch.senderRole,
                        )
                        customerDao.upsert(local.copy(syncStatus = SyncStatus.CONFLICT))
                        conflictsCount++
                    } else if (incoming.updatedAt > local.updatedAt) {
                        customerDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                        changesReceived++
                    }
                }
            }

            // Merge Sales
            batch.sales.forEach { incoming ->
                val local = saleDao.getById(incoming.id)
                if (local == null) {
                    saleDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                    changesReceived++
                } else if (!isSaleEqual(local, incoming)) {
                    if (local.syncStatus == SyncStatus.PENDING) {
                        createConflict(
                            recordId = incoming.id,
                            entityType = "SALE",
                            localJson = SyncJsonCodec.saleToJson(local),
                            incomingJson = SyncJsonCodec.saleToJson(incoming),
                            localDeviceId = localIdentity.deviceId,
                            incomingDeviceId = batch.senderDeviceId,
                            incomingDeviceRole = batch.senderRole,
                        )
                        saleDao.upsert(local.copy(syncStatus = SyncStatus.CONFLICT))
                        conflictsCount++
                    } else if (incoming.updatedAt > local.updatedAt) {
                        saleDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                        changesReceived++
                    }
                }
            }

            // Merge Payments
            batch.payments.forEach { incoming ->
                val local = paymentDao.getById(incoming.id)
                if (local == null) {
                    paymentDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                    changesReceived++
                } else if (!isPaymentEqual(local, incoming)) {
                    if (local.syncStatus == SyncStatus.PENDING) {
                        createConflict(
                            recordId = incoming.id,
                            entityType = "PAYMENT",
                            localJson = SyncJsonCodec.paymentToJson(local),
                            incomingJson = SyncJsonCodec.paymentToJson(incoming),
                            localDeviceId = localIdentity.deviceId,
                            incomingDeviceId = batch.senderDeviceId,
                            incomingDeviceRole = batch.senderRole,
                        )
                        paymentDao.upsert(local.copy(syncStatus = SyncStatus.CONFLICT))
                        conflictsCount++
                    } else if (incoming.updatedAt > local.updatedAt) {
                        paymentDao.upsert(incoming.copy(syncStatus = SyncStatus.SYNCED))
                        changesReceived++
                    }
                }
            }

            // Save incoming change logs marked as SYNCED
            batch.changes.forEach { changeLog ->
                syncDao.upsertChangeLog(changeLog.copy(syncStatus = SyncStatus.SYNCED))
            }

            val session = SyncSessionEntity(
                id = batch.batchId,
                peerDeviceId = batch.senderDeviceId,
                peerDeviceName = batch.senderDeviceName,
                peerDeviceRole = batch.senderRole,
                startedAt = startedAt,
                completedAt = currentTimeMillis(),
                status = if (conflictsCount > 0) "CONFLICT_DETECTED" else "SUCCESS",
                changesReceivedCount = changesReceived,
                changesSentCount = 0,
                conflictsCount = conflictsCount,
                errorMessage = null,
            )
            syncDao.upsertSession(session)
            session
        }
    }

    suspend fun performTwoWaySync(
        incomingBatch: SyncBatch,
        lastReceivedSequenceFromPeer: Long = 0L,
    ): TwoWaySyncResult {
        return database.withTransaction {
            val importSession = processIncomingBatch(incomingBatch)
            val responseBatch = generateSyncBatch(lastReceivedSequenceFromPeer)

            val updatedSession = importSession.copy(
                changesSentCount = responseBatch.changes.size,
            )
            syncDao.upsertSession(updatedSession)

            TwoWaySyncResult(
                session = updatedSession,
                responseBatch = responseBatch,
            )
        }
    }

    suspend fun acceptMainVersion(conflictId: String) {
        database.withTransaction {
            val conflict = syncDao.getConflictById(conflictId)
                ?: throw IllegalArgumentException("التضارب غير موجود.")

            val mainJson = conflict.mainVersionJson
            val now = currentTimeMillis()

            when (conflict.entityType) {
                "CUSTOMER" -> {
                    val customer = SyncJsonCodec.customerFromJson(mainJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    customerDao.upsert(customer)
                    logChange(customer.id, "CUSTOMER", "UPDATE", SyncJsonCodec.customerToJson(customer))
                }
                "SALE" -> {
                    val sale = SyncJsonCodec.saleFromJson(mainJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    saleDao.upsert(sale)
                    logChange(sale.id, "SALE", "UPDATE", SyncJsonCodec.saleToJson(sale))
                }
                "PAYMENT" -> {
                    val payment = SyncJsonCodec.paymentFromJson(mainJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    paymentDao.upsert(payment)
                    logChange(payment.id, "PAYMENT", "UPDATE", SyncJsonCodec.paymentToJson(payment))
                }
                else -> error("نوع السجل غير معروف")
            }

            syncDao.upsertConflict(conflict.copy(status = "RESOLVED_MAIN"))
        }
    }

    suspend fun acceptSecondaryVersion(conflictId: String) {
        database.withTransaction {
            val conflict = syncDao.getConflictById(conflictId)
                ?: throw IllegalArgumentException("التضارب غير موجود.")

            val secondaryJson = conflict.secondaryVersionJson
            val now = currentTimeMillis()

            when (conflict.entityType) {
                "CUSTOMER" -> {
                    val customer = SyncJsonCodec.customerFromJson(secondaryJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    customerDao.upsert(customer)
                    logChange(customer.id, "CUSTOMER", "UPDATE", SyncJsonCodec.customerToJson(customer))
                }
                "SALE" -> {
                    val sale = SyncJsonCodec.saleFromJson(secondaryJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    saleDao.upsert(sale)
                    logChange(sale.id, "SALE", "UPDATE", SyncJsonCodec.saleToJson(sale))
                }
                "PAYMENT" -> {
                    val payment = SyncJsonCodec.paymentFromJson(secondaryJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    paymentDao.upsert(payment)
                    logChange(payment.id, "PAYMENT", "UPDATE", SyncJsonCodec.paymentToJson(payment))
                }
                else -> error("نوع السجل غير معروف")
            }

            syncDao.upsertConflict(conflict.copy(status = "RESOLVED_SECONDARY"))
        }
    }

    suspend fun manuallyResolveConflict(conflictId: String, resolvedJson: String) {
        database.withTransaction {
            val conflict = syncDao.getConflictById(conflictId)
                ?: throw IllegalArgumentException("التضارب غير موجود.")

            val now = currentTimeMillis()

            when (conflict.entityType) {
                "CUSTOMER" -> {
                    val customer = SyncJsonCodec.customerFromJson(resolvedJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    customerDao.upsert(customer)
                    logChange(customer.id, "CUSTOMER", "UPDATE", SyncJsonCodec.customerToJson(customer))
                }
                "SALE" -> {
                    val sale = SyncJsonCodec.saleFromJson(resolvedJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    saleDao.upsert(sale)
                    logChange(sale.id, "SALE", "UPDATE", SyncJsonCodec.saleToJson(sale))
                }
                "PAYMENT" -> {
                    val payment = SyncJsonCodec.paymentFromJson(resolvedJson)
                        .copy(updatedAt = now, syncStatus = SyncStatus.PENDING)
                    paymentDao.upsert(payment)
                    logChange(payment.id, "PAYMENT", "UPDATE", SyncJsonCodec.paymentToJson(payment))
                }
                else -> error("نوع السجل غير معروف")
            }

            syncDao.upsertConflict(conflict.copy(status = "RESOLVED_MANUAL"))
        }
    }

    private suspend fun createConflict(
        recordId: String,
        entityType: String,
        localJson: String,
        incomingJson: String,
        localDeviceId: String,
        incomingDeviceId: String,
        incomingDeviceRole: DeviceRole,
    ) {
        val (mainJson, secJson, mainId, secId) = if (incomingDeviceRole == DeviceRole.MAIN) {
            Tuple4(incomingJson, localJson, incomingDeviceId, localDeviceId)
        } else {
            Tuple4(localJson, incomingJson, localDeviceId, incomingDeviceId)
        }

        val conflict = SyncConflictEntity(
            id = newId(),
            recordId = recordId,
            entityType = entityType,
            mainVersionJson = mainJson,
            secondaryVersionJson = secJson,
            mainDeviceId = mainId,
            secondaryDeviceId = secId,
            createdAt = currentTimeMillis(),
            status = "UNRESOLVED",
        )
        syncDao.upsertConflict(conflict)
    }

    private fun isCustomerEqual(a: CustomerEntity, b: CustomerEntity): Boolean {
        return a.id == b.id &&
            a.name == b.name &&
            a.phone == b.phone &&
            a.notes == b.notes &&
            a.isDeleted == b.isDeleted
    }

    private fun isSaleEqual(a: SaleEntity, b: SaleEntity): Boolean {
        return a.id == b.id &&
            a.customerId == b.customerId &&
            a.description == b.description &&
            a.quantity == b.quantity &&
            a.unitPriceMinor == b.unitPriceMinor &&
            a.soldAt == b.soldAt &&
            a.notes == b.notes &&
            a.isDeleted == b.isDeleted
    }

    private fun isPaymentEqual(a: PaymentEntity, b: PaymentEntity): Boolean {
        return a.id == b.id &&
            a.customerId == b.customerId &&
            a.saleId == b.saleId &&
            a.amountMinor == b.amountMinor &&
            a.paidAt == b.paidAt &&
            a.notes == b.notes &&
            a.isDeleted == b.isDeleted
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
