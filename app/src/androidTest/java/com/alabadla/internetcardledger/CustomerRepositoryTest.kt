package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.repository.PaymentRepository
import com.alabadla.internetcardledger.data.repository.SaleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerRepositoryTest {
    private lateinit var database: LedgerDatabase
    private lateinit var customerRepository: CustomerRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var paymentRepository: PaymentRepository
    private var currentTime = 1_000L
    private var idSequence = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val deviceIdProvider = DeviceIdProvider(context)
        val timeSupplier = { currentTime }
        val idGenerator = { "00000000-0000-0000-0000-${(++idSequence).toString().padStart(12, '0')}" }

        customerRepository = CustomerRepository(
            customerDao = database.customerDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            newId = idGenerator,
        )
        saleRepository = SaleRepository(
            saleDao = database.saleDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            newId = idGenerator,
        )
        paymentRepository = PaymentRepository(
            paymentDao = database.paymentDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            newId = idGenerator,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createCustomer_savesIdentityAndSyncMetadata() = runBlocking {
        val id = customerRepository.addCustomer(
            name = "  محمد أحمد  ",
            phone = "  0599123456  ",
            notes = "  ملاحظة تجريبية  ",
        )

        val customer = database.customerDao().observeById(id).first()

        assertNotNull(customer)
        assertEquals("محمد أحمد", customer?.name)
        assertEquals("0599123456", customer?.phone)
        assertEquals("ملاحظة تجريبية", customer?.notes)
        assertEquals(1_000L, customer?.createdAt)
        assertEquals(1_000L, customer?.updatedAt)
        assertTrue(customer?.createdByDeviceId?.isNotBlank() == true)
        assertEquals(SyncStatus.PENDING, customer?.syncStatus)
        assertFalse(customer?.isDeleted ?: true)
    }

    @Test
    fun updateCustomer_preservesIdentityAndMarksRecordPending() = runBlocking {
        val id = customerRepository.addCustomer("علي محمود", "0590000000")
        val original = requireNotNull(database.customerDao().observeById(id).first())
        database.customerDao().upsert(original.copy(syncStatus = SyncStatus.SYNCED))
        currentTime = 2_000L

        customerRepository.updateCustomer(
            original.copy(
                name = "علي محمود المعدل",
                phone = "0591111111",
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        val updated = requireNotNull(database.customerDao().observeById(id).first())
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.createdByDeviceId, updated.createdByDeviceId)
        assertEquals("علي محمود المعدل", updated.name)
        assertEquals("0591111111", updated.phone)
        assertEquals(2_000L, updated.updatedAt)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
    }

    @Test
    fun searchCustomers_returnsOnlyMatchingActiveRecords() = runBlocking {
        customerRepository.addCustomer("أحمد خليل", "0599111222")
        customerRepository.addCustomer("سارة يوسف", "0598333444", notes = "زبون ممتاز")

        val nameMatches = customerRepository.observeCustomers("أحمد").first()
        val phoneMatches = customerRepository.observeCustomers("8333").first()
        val notesMatches = customerRepository.observeCustomers("ممتاز").first()

        assertEquals(listOf("أحمد خليل"), nameMatches.map { it.name })
        assertEquals(listOf("سارة يوسف"), phoneMatches.map { it.name })
        assertEquals(listOf("سارة يوسف"), notesMatches.map { it.name })
    }

    @Test
    fun deleteCustomer_softDeletesAndMarksRecordPending() = runBlocking {
        val id = customerRepository.addCustomer("زبون مؤقت")
        val original = requireNotNull(database.customerDao().observeById(id).first())
        database.customerDao().upsert(original.copy(syncStatus = SyncStatus.SYNCED))
        currentTime = 3_000L

        assertTrue(customerRepository.deleteCustomer(id))

        assertTrue(customerRepository.observeCustomers().first().isEmpty())
        val deleted = requireNotNull(database.customerDao().observeById(id).first())
        assertTrue(deleted.isDeleted)
        assertEquals(3_000L, deleted.updatedAt)
        assertEquals(SyncStatus.PENDING, deleted.syncStatus)
    }

    @Test
    fun customerCalculations_computesSalesPaymentsAndBalanceCorrectly() = runBlocking {
        val customerId = customerRepository.addCustomer("سامر علي")

        // Add 2 sales for this customer: quantity 2 @ 1000 = 2000, quantity 1 @ 3000 = 3000 -> Total = 5000
        saleRepository.addSale(customerId, "بطاقة فئة 10", 2, 1_000L)
        saleRepository.addSale(customerId, "بطاقة فئة 30", 1, 3_000L)

        // Add 1 payment for this customer: 2000 -> Total = 2000
        paymentRepository.addPayment(customerId, null, 2_000L)

        val sales = saleRepository.observeSalesForCustomer(customerId).first()
        val payments = paymentRepository.observePaymentsForCustomer(customerId).first()

        val totalSales = sales.sumOf { it.quantity.toLong() * it.unitPriceMinor }
        val totalPayments = payments.sumOf { it.amountMinor }
        val balance = totalSales - totalPayments

        assertEquals(2, sales.size)
        assertEquals(1, payments.size)
        assertEquals(5_000L, totalSales)
        assertEquals(2_000L, totalPayments)
        assertEquals(3_000L, balance)
    }
}
