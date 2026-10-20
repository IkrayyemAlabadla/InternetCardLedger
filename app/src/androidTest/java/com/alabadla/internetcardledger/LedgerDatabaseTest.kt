package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {
    private lateinit var database: LedgerDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun records_areStoredObservedAndSoftDeleted() = runBlocking {
        val customer = CustomerEntity(
            id = "customer-id",
            name = "زبون تجريبي",
            phone = null,
            notes = null,
            createdAt = 100L,
            updatedAt = 100L,
            createdByDeviceId = "device-id",
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )
        val sale = SaleEntity(
            id = "sale-id",
            customerId = customer.id,
            description = "بطاقة إنترنت",
            quantity = 2,
            unitPriceMinor = 1_000L,
            soldAt = 110L,
            notes = null,
            createdAt = 110L,
            updatedAt = 110L,
            createdByDeviceId = "device-id",
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )
        val payment = PaymentEntity(
            id = "payment-id",
            customerId = customer.id,
            saleId = sale.id,
            amountMinor = 500L,
            paidAt = 120L,
            notes = null,
            createdAt = 120L,
            updatedAt = 120L,
            createdByDeviceId = "device-id",
            syncStatus = SyncStatus.PENDING,
            isDeleted = false,
        )

        database.customerDao().upsert(customer)
        database.saleDao().upsert(sale)
        database.paymentDao().upsert(payment)

        assertEquals(listOf(customer), database.customerDao().observeActive().first())
        assertEquals(listOf(sale), database.saleDao().observeForCustomer(customer.id).first())
        assertEquals(listOf(payment), database.paymentDao().observeForCustomer(customer.id).first())
        assertEquals(1, database.customerDao().observePendingSync().first().size)

        assertEquals(1, database.paymentDao().softDelete(payment.id, 200L))
        assertTrue(database.paymentDao().observeActive().first().isEmpty())
        val deletedPayment = database.paymentDao().observeById(payment.id).first()
        assertEquals(200L, deletedPayment?.updatedAt)
        assertEquals(SyncStatus.PENDING, deletedPayment?.syncStatus)
        assertTrue(deletedPayment?.isDeleted == true)
        assertFalse(database.paymentDao().observePendingSync().first().isEmpty())
    }
}
