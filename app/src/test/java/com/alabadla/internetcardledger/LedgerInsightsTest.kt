package com.alabadla.internetcardledger

import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.domain.HistoryDateFilter
import com.alabadla.internetcardledger.domain.HistoryOperation
import com.alabadla.internetcardledger.domain.HistoryRecordType
import com.alabadla.internetcardledger.domain.LedgerInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LedgerInsightsTest {
    private val day = 24L * 60L * 60L * 1_000L
    private val now = 1_800_000_000_000L

    @Test
    fun calculate_usesLongMinorUnitsForAllTotalsAndBalance() {
        val overview = LedgerInsights.calculate(
            customers = listOf(customer("c1"), customer("c2")),
            sales = listOf(
                sale("s1", "c1", quantity = 3, unitPriceMinor = 1_250L),
                sale("s2", "c2", quantity = 2, unitPriceMinor = 2_000L),
            ),
            payments = listOf(
                payment("p1", "c1", amountMinor = 2_500L),
                payment("p2", "c2", amountMinor = 1_000L),
            ),
        )

        assertEquals(2, overview.summary.activeCustomers)
        assertEquals(2, overview.summary.activeSales)
        assertEquals(2, overview.summary.activePayments)
        assertEquals(5L, overview.summary.totalQuantity)
        assertEquals(7_750L, overview.summary.totalSalesMinor)
        assertEquals(3_500L, overview.summary.totalPaymentsMinor)
        assertEquals(4_250L, overview.summary.outstandingBalanceMinor)
        assertEquals(6, overview.summary.pendingChanges)
    }

    @Test
    fun calculate_excludesEverySoftDeletedRecordFromDashboardTotals() {
        val overview = LedgerInsights.calculate(
            customers = listOf(customer("active"), customer("deleted", isDeleted = true)),
            sales = listOf(
                sale("active-sale", "active", quantity = 2, unitPriceMinor = 500L),
                sale("deleted-sale", "deleted", quantity = 99, unitPriceMinor = 9_999L, isDeleted = true),
            ),
            payments = listOf(
                payment("active-payment", "active", amountMinor = 250L),
                payment("deleted-payment", "deleted", amountMinor = 50_000L, isDeleted = true),
            ),
        )

        assertEquals(1, overview.summary.activeCustomers)
        assertEquals(1, overview.summary.activeSales)
        assertEquals(1, overview.summary.activePayments)
        assertEquals(2L, overview.summary.totalQuantity)
        assertEquals(1_000L, overview.summary.totalSalesMinor)
        assertEquals(250L, overview.summary.totalPaymentsMinor)
        assertEquals(750L, overview.summary.outstandingBalanceMinor)
        assertTrue(overview.allHistory.any { it.recordId == "deleted-sale" && it.isDeleted })
    }

    @Test
    fun calculate_ordersRecentActivityByUpdatedAtDescendingAndLimitsResults() {
        val overview = LedgerInsights.calculate(
            customers = listOf(customer("c1", updatedAt = now - day)),
            sales = listOf(
                sale("s-old", null, updatedAt = now - 10_000L),
                sale("s-new", null, updatedAt = now),
            ),
            payments = listOf(payment("p1", "c1", updatedAt = now - 5_000L)),
            recentLimit = 3,
        )

        assertEquals(listOf("s-new", "p1", "s-old"), overview.recentHistory.map { it.recordId })
        assertEquals(HistoryOperation.UPDATED, overview.recentHistory.first().operation)
    }

    @Test
    fun filterHistory_appliesSearchTypeAndEveryDateFilter() {
        val overview = LedgerInsights.calculate(
            customers = listOf(customer("c1", name = "أحمد", updatedAt = now)),
            sales = listOf(
                sale("today", "c1", description = "بطاقة يومية", updatedAt = now - 1_000L),
                sale("week", "c1", description = "بطاقة أسبوعية", updatedAt = now - 6L * day),
                sale("month", "c1", description = "بطاقة شهرية", updatedAt = now - 20L * day),
                sale("old", "c1", description = "بطاقة قديمة", updatedAt = now - 40L * day),
            ),
            payments = listOf(payment("p1", "c1", amountMinor = 777L, updatedAt = now - day)),
        )
        val utc = TimeZone.getTimeZone("UTC")

        val searched = LedgerInsights.filterHistory(
            overview.allHistory,
            query = "أسبوعية",
            type = HistoryRecordType.SALE,
            dateFilter = HistoryDateFilter.ALL,
            now = now,
            timeZone = utc,
        )
        val lastSevenDays = LedgerInsights.filterHistory(
            overview.allHistory,
            query = "",
            type = HistoryRecordType.SALE,
            dateFilter = HistoryDateFilter.LAST_7_DAYS,
            now = now,
            timeZone = utc,
        )
        val todayOnly = LedgerInsights.filterHistory(
            overview.allHistory,
            query = "",
            type = HistoryRecordType.SALE,
            dateFilter = HistoryDateFilter.TODAY,
            now = now,
            timeZone = utc,
        )
        val lastThirtyDays = LedgerInsights.filterHistory(
            overview.allHistory,
            query = "",
            type = HistoryRecordType.SALE,
            dateFilter = HistoryDateFilter.LAST_30_DAYS,
            now = now,
            timeZone = utc,
        )
        val paymentsOnly = LedgerInsights.filterHistory(
            overview.allHistory,
            query = "777",
            type = HistoryRecordType.PAYMENT,
            dateFilter = HistoryDateFilter.ALL,
            now = now,
            timeZone = utc,
        )

        assertEquals(listOf("week"), searched.map { it.recordId })
        assertEquals(listOf("today"), todayOnly.map { it.recordId })
        assertEquals(listOf("today", "week"), lastSevenDays.map { it.recordId })
        assertEquals(listOf("today", "week", "month"), lastThirtyDays.map { it.recordId })
        assertEquals(listOf("p1"), paymentsOnly.map { it.recordId })
        assertFalse(lastThirtyDays.any { it.recordId == "old" })
    }

    private fun customer(
        id: String,
        name: String = id,
        updatedAt: Long = now - 2_000L,
        isDeleted: Boolean = false,
    ) = CustomerEntity(
        id = id,
        name = name,
        phone = null,
        notes = null,
        createdAt = updatedAt - 1L,
        updatedAt = updatedAt,
        createdByDeviceId = "device",
        syncStatus = SyncStatus.PENDING,
        isDeleted = isDeleted,
    )

    private fun sale(
        id: String,
        customerId: String?,
        description: String = id,
        quantity: Int = 1,
        unitPriceMinor: Long = 100L,
        updatedAt: Long = now - 2_000L,
        isDeleted: Boolean = false,
    ) = SaleEntity(
        id = id,
        customerId = customerId,
        description = description,
        quantity = quantity,
        unitPriceMinor = unitPriceMinor,
        soldAt = updatedAt,
        notes = null,
        createdAt = updatedAt - 1L,
        updatedAt = updatedAt,
        createdByDeviceId = "device",
        syncStatus = SyncStatus.PENDING,
        isDeleted = isDeleted,
    )

    private fun payment(
        id: String,
        customerId: String,
        amountMinor: Long = 100L,
        updatedAt: Long = now - 2_000L,
        isDeleted: Boolean = false,
    ) = PaymentEntity(
        id = id,
        customerId = customerId,
        saleId = null,
        amountMinor = amountMinor,
        paidAt = updatedAt,
        notes = null,
        createdAt = updatedAt - 1L,
        updatedAt = updatedAt,
        createdByDeviceId = "device",
        syncStatus = SyncStatus.PENDING,
        isDeleted = isDeleted,
    )
}
