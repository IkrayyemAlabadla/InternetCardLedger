package com.alabadla.internetcardledger.domain

import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import java.util.Calendar
import java.util.TimeZone

enum class HistoryRecordType {
    ALL,
    SALE,
    PAYMENT,
    CUSTOMER,
}

enum class HistoryDateFilter {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

enum class HistoryOperation {
    CREATED,
    UPDATED,
    DELETED,
}

data class HistoryRecord(
    val recordId: String,
    val type: HistoryRecordType,
    val operation: HistoryOperation,
    val title: String?,
    val relatedName: String?,
    val quantity: Int?,
    val amountMinor: Long?,
    val updatedAt: Long,
    val isPending: Boolean,
    val isDeleted: Boolean,
)

data class DashboardSummary(
    val activeCustomers: Int,
    val activeSales: Int,
    val activePayments: Int,
    val totalQuantity: Long,
    val totalSalesMinor: Long,
    val totalPaymentsMinor: Long,
    val outstandingBalanceMinor: Long,
    val pendingChanges: Int,
)

data class LedgerOverview(
    val summary: DashboardSummary,
    val allHistory: List<HistoryRecord>,
    val recentHistory: List<HistoryRecord>,
)

object LedgerInsights {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

    fun calculate(
        customers: List<CustomerEntity>,
        sales: List<SaleEntity>,
        payments: List<PaymentEntity>,
        recentLimit: Int = 5,
    ): LedgerOverview {
        val activeCustomers = customers.filterNot(CustomerEntity::isDeleted)
        val activeSales = sales.filterNot(SaleEntity::isDeleted)
        val activePayments = payments.filterNot(PaymentEntity::isDeleted)

        val totalSales = activeSales.fold(0L) { total, sale ->
            Math.addExact(total, saleTotal(sale))
        }
        val totalPayments = activePayments.fold(0L) { total, payment ->
            Math.addExact(total, payment.amountMinor)
        }
        val customerNames = customers.associate { it.id to it.name }

        val history = buildList {
            customers.forEach { customer ->
                add(
                    HistoryRecord(
                        recordId = customer.id,
                        type = HistoryRecordType.CUSTOMER,
                        operation = operationOf(customer.createdAt, customer.updatedAt, customer.isDeleted),
                        title = customer.name,
                        relatedName = null,
                        quantity = null,
                        amountMinor = null,
                        updatedAt = customer.updatedAt,
                        isPending = customer.syncStatus != SyncStatus.SYNCED,
                        isDeleted = customer.isDeleted,
                    ),
                )
            }
            sales.forEach { sale ->
                add(
                    HistoryRecord(
                        recordId = sale.id,
                        type = HistoryRecordType.SALE,
                        operation = operationOf(sale.createdAt, sale.updatedAt, sale.isDeleted),
                        title = sale.description,
                        relatedName = sale.customerId?.let(customerNames::get),
                        quantity = sale.quantity,
                        amountMinor = saleTotal(sale),
                        updatedAt = sale.updatedAt,
                        isPending = sale.syncStatus != SyncStatus.SYNCED,
                        isDeleted = sale.isDeleted,
                    ),
                )
            }
            payments.forEach { payment ->
                add(
                    HistoryRecord(
                        recordId = payment.id,
                        type = HistoryRecordType.PAYMENT,
                        operation = operationOf(payment.createdAt, payment.updatedAt, payment.isDeleted),
                        title = null,
                        relatedName = customerNames[payment.customerId],
                        quantity = null,
                        amountMinor = payment.amountMinor,
                        updatedAt = payment.updatedAt,
                        isPending = payment.syncStatus != SyncStatus.SYNCED,
                        isDeleted = payment.isDeleted,
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<HistoryRecord> { it.updatedAt }
                .thenBy { it.type.name }
                .thenBy { it.recordId },
        )

        return LedgerOverview(
            summary = DashboardSummary(
                activeCustomers = activeCustomers.size,
                activeSales = activeSales.size,
                activePayments = activePayments.size,
                totalQuantity = activeSales.fold(0L) { total, sale ->
                    Math.addExact(total, sale.quantity.toLong())
                },
                totalSalesMinor = totalSales,
                totalPaymentsMinor = totalPayments,
                outstandingBalanceMinor = Math.subtractExact(totalSales, totalPayments),
                pendingChanges = customers.count { it.syncStatus != SyncStatus.SYNCED } +
                    sales.count { it.syncStatus != SyncStatus.SYNCED } +
                    payments.count { it.syncStatus != SyncStatus.SYNCED },
            ),
            allHistory = history,
            recentHistory = history.take(recentLimit.coerceAtLeast(0)),
        )
    }

    fun filterHistory(
        records: List<HistoryRecord>,
        query: String,
        type: HistoryRecordType,
        dateFilter: HistoryDateFilter,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<HistoryRecord> {
        val normalizedQuery = query.trim()
        val earliestTimestamp = when (dateFilter) {
            HistoryDateFilter.ALL -> Long.MIN_VALUE
            HistoryDateFilter.TODAY -> startOfDay(now, timeZone)
            HistoryDateFilter.LAST_7_DAYS -> now - (7L * DAY_MILLIS)
            HistoryDateFilter.LAST_30_DAYS -> now - (30L * DAY_MILLIS)
        }

        return records.filter { record ->
            val matchesType = type == HistoryRecordType.ALL || record.type == type
            val matchesDate = record.updatedAt >= earliestTimestamp && record.updatedAt <= now
            val matchesQuery = normalizedQuery.isEmpty() || listOfNotNull(
                record.title,
                record.relatedName,
                record.quantity?.toString(),
                record.amountMinor?.toString(),
            ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
            matchesType && matchesDate && matchesQuery
        }
    }

    private fun operationOf(createdAt: Long, updatedAt: Long, isDeleted: Boolean): HistoryOperation =
        when {
            isDeleted -> HistoryOperation.DELETED
            createdAt == updatedAt -> HistoryOperation.CREATED
            else -> HistoryOperation.UPDATED
        }

    private fun saleTotal(sale: SaleEntity): Long =
        Math.multiplyExact(sale.quantity.toLong(), sale.unitPriceMinor)

    private fun startOfDay(timestamp: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
