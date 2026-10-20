package com.alabadla.internetcardledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.alabadla.internetcardledger.data.backup.BackupDocument
import com.alabadla.internetcardledger.data.backup.LedgerSnapshot
import com.alabadla.internetcardledger.data.backup.RestoreReport
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM customers ORDER BY id")
    abstract suspend fun allCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM sales ORDER BY id")
    abstract suspend fun allSales(): List<SaleEntity>

    @Query("SELECT * FROM payments ORDER BY id")
    abstract suspend fun allPayments(): List<PaymentEntity>

    @Upsert
    abstract suspend fun upsertCustomers(records: List<CustomerEntity>)

    @Upsert
    abstract suspend fun upsertSales(records: List<SaleEntity>)

    @Upsert
    abstract suspend fun upsertPayments(records: List<PaymentEntity>)

    @Query("DELETE FROM payments")
    abstract suspend fun deletePayments()

    @Query("DELETE FROM sales")
    abstract suspend fun deleteSales()

    @Query("DELETE FROM customers")
    abstract suspend fun deleteCustomers()

    @Transaction
    open suspend fun snapshot(): LedgerSnapshot = LedgerSnapshot(
        customers = allCustomers(),
        sales = allSales(),
        payments = allPayments(),
    )

    @Transaction
    open suspend fun merge(document: BackupDocument): RestoreReport {
        var inserted = 0
        var updated = 0
        var unchanged = 0
        var conflicts = 0

        val currentCustomers = allCustomers().associateBy { it.id }
        val customerChanges = document.customers.mapNotNull { incoming ->
            mergeRecord(currentCustomers[incoming.id], incoming,
                sameData = { local, remote -> local.copy(syncStatus = remote.syncStatus) == remote },
                markConflict = { it.copy(syncStatus = SyncStatus.CONFLICT) },
                onInserted = { inserted++ }, onUpdated = { updated++ },
                onUnchanged = { unchanged++ }, onConflict = { conflicts++ })
        }
        if (customerChanges.isNotEmpty()) upsertCustomers(customerChanges)

        val currentSales = allSales().associateBy { it.id }
        val saleChanges = document.sales.mapNotNull { incoming ->
            mergeRecord(currentSales[incoming.id], incoming,
                sameData = { local, remote -> local.copy(syncStatus = remote.syncStatus) == remote },
                markConflict = { it.copy(syncStatus = SyncStatus.CONFLICT) },
                onInserted = { inserted++ }, onUpdated = { updated++ },
                onUnchanged = { unchanged++ }, onConflict = { conflicts++ })
        }
        if (saleChanges.isNotEmpty()) upsertSales(saleChanges)

        val currentPayments = allPayments().associateBy { it.id }
        val paymentChanges = document.payments.mapNotNull { incoming ->
            mergeRecord(currentPayments[incoming.id], incoming,
                sameData = { local, remote -> local.copy(syncStatus = remote.syncStatus) == remote },
                markConflict = { it.copy(syncStatus = SyncStatus.CONFLICT) },
                onInserted = { inserted++ }, onUpdated = { updated++ },
                onUnchanged = { unchanged++ }, onConflict = { conflicts++ })
        }
        if (paymentChanges.isNotEmpty()) upsertPayments(paymentChanges)

        return RestoreReport(inserted, updated, unchanged, conflicts)
    }

    @Transaction
    open suspend fun replace(document: BackupDocument): RestoreReport {
        deletePayments()
        deleteSales()
        deleteCustomers()
        if (document.customers.isNotEmpty()) upsertCustomers(document.customers)
        if (document.sales.isNotEmpty()) upsertSales(document.sales)
        if (document.payments.isNotEmpty()) upsertPayments(document.payments)
        return RestoreReport(
            inserted = document.recordCount,
            updated = 0,
            unchanged = 0,
            conflicts = 0,
        )
    }
}

private fun <T> mergeRecord(
    local: T?,
    incoming: T,
    sameData: (T, T) -> Boolean,
    markConflict: (T) -> T,
    onInserted: () -> Unit,
    onUpdated: () -> Unit,
    onUnchanged: () -> Unit,
    onConflict: () -> Unit,
): T? where T : Any {
    if (local == null) {
        onInserted()
        return incoming
    }
    val localUpdatedAt = updatedAt(local)
    val incomingUpdatedAt = updatedAt(incoming)
    return when {
        incomingUpdatedAt > localUpdatedAt -> {
            onUpdated()
            incoming
        }
        incomingUpdatedAt < localUpdatedAt -> {
            onUnchanged()
            null
        }
        sameData(local, incoming) -> {
            onUnchanged()
            null
        }
        else -> {
            onConflict()
            markConflict(local)
        }
    }
}

private fun updatedAt(record: Any): Long = when (record) {
    is CustomerEntity -> record.updatedAt
    is SaleEntity -> record.updatedAt
    is PaymentEntity -> record.updatedAt
    else -> error("Unsupported backup record")
}
