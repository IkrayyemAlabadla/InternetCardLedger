package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CardType
import com.alabadla.internetcardledger.data.local.entity.PaymentStatus
import com.alabadla.internetcardledger.data.repository.SaleRepository
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.ui.viewmodel.SalesViewModel
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
class SaleRepositoryTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: SaleRepository
    private lateinit var customerRepository: CustomerRepository
    private var currentTime = 1_000L
    private var idSequence = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SaleRepository(
            saleDao = database.saleDao(),
            deviceIdProvider = DeviceIdProvider(context),
            currentTimeMillis = { currentTime },
            newId = { "00000000-0000-0000-0000-${(++idSequence).toString().padStart(12, '0')}" },
        )
        customerRepository = CustomerRepository(
            customerDao = database.customerDao(),
            deviceIdProvider = DeviceIdProvider(context),
            currentTimeMillis = { currentTime },
            newId = { "10000000-0000-0000-0000-${(++idSequence).toString().padStart(12, '0')}" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createSale_savesIdentityAndSyncMetadata() = runBlocking {
        val id = repository.addSale(
            customerId = null,
            description = "  بطاقة 12 ساعة  ",
            quantity = 2,
            unitPriceMinor = 2L,
            cardType = CardType.TWELVE_HOURS,
            paymentStatus = PaymentStatus.PARTIALLY_PAID,
            paidAmountMinor = 2L,
        )

        val sale = database.saleDao().observeById(id).first()

        assertNotNull(sale)
        assertEquals("بطاقة 12 ساعة", sale?.description)
        assertEquals(2L, sale?.paidAmountMinor)
        assertEquals(1_000L, sale?.createdAt)
        assertEquals(1_000L, sale?.updatedAt)
        assertTrue(sale?.createdByDeviceId?.isNotBlank() == true)
        assertEquals(SyncStatus.PENDING, sale?.syncStatus)
        assertFalse(sale?.isDeleted ?: true)
    }

    @Test
    fun updateSale_preservesIdentityAndMarksRecordPending() = runBlocking {
        val id = repository.addSale(null, "بطاقة 6 ساعات", 1, 1L, paymentStatus = PaymentStatus.PAID, paidAmountMinor = 1L)
        val original = requireNotNull(database.saleDao().observeById(id).first())
        database.saleDao().upsert(original.copy(syncStatus = SyncStatus.SYNCED))
        currentTime = 2_000L

        repository.updateSale(
            original.copy(
                description = "بطاقة معدلة",
                quantity = 3,
                unitPriceMinor = 2L,
                cardType = CardType.TWELVE_HOURS,
                paymentStatus = PaymentStatus.PARTIALLY_PAID,
                paidAmountMinor = 4L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        val updated = requireNotNull(database.saleDao().observeById(id).first())
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.createdByDeviceId, updated.createdByDeviceId)
        assertEquals("بطاقة معدلة", updated.description)
        assertEquals(2_000L, updated.updatedAt)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
    }

    @Test
    fun searchSales_returnsOnlyMatchingActiveRecords() = runBlocking {
        repository.addSale(null, "بطاقة إنترنت يومية", 1, 1L, paymentStatus = PaymentStatus.PAID, paidAmountMinor = 1L)
        repository.addSale(null, "جهاز مودم منزلي", 1, 1L, paymentStatus = PaymentStatus.UNPAID, paidAmountMinor = 0L)

        val descriptionMatches = repository.observeSales("إنترنت").first()
        val notesMatches = repository.observeSales("منزلي").first()

        assertEquals(listOf("بطاقة إنترنت يومية"), descriptionMatches.map { it.description })
        assertEquals(listOf("جهاز مودم منزلي"), notesMatches.map { it.description })
    }

    @Test
    fun deleteSale_softDeletesAndMarksRecordPending() = runBlocking {
        val id = repository.addSale(null, "بطاقة 6 ساعات", 1, 1L, paymentStatus = PaymentStatus.PAID, paidAmountMinor = 1L)
        val original = requireNotNull(database.saleDao().observeById(id).first())
        database.saleDao().upsert(original.copy(syncStatus = SyncStatus.SYNCED))
        currentTime = 3_000L

        assertTrue(repository.deleteSale(id))

        assertTrue(repository.observeSales().first().isEmpty())
        val deleted = requireNotNull(database.saleDao().observeById(id).first())
        assertTrue(deleted.isDeleted)
        assertEquals(3_000L, deleted.updatedAt)
        assertEquals(SyncStatus.PENDING, deleted.syncStatus)
    }

    @Test
    fun addCustomerFromSaleForm_selectsNewCustomerWithoutResettingForm() = runBlocking {
        val viewModel = SalesViewModel(repository, customerRepository)
        viewModel.updateCardType(CardType.TWELVE_HOURS)
        viewModel.updateQuantity("3")
        viewModel.updatePaymentStatus(PaymentStatus.PARTIALLY_PAID)
        viewModel.updateCustomPaidAmount("4")

        val customerId = viewModel.createAndSelectCustomer("أحمد إبراهيم", "0599123456")

        assertNotNull(customerId)
        assertEquals(customerId, viewModel.formState.value.customerId)
        assertEquals(CardType.TWELVE_HOURS, viewModel.formState.value.cardType)
        assertEquals("3", viewModel.formState.value.quantity)
        assertEquals(PaymentStatus.PARTIALLY_PAID, viewModel.formState.value.paymentStatus)
        assertEquals("4", viewModel.formState.value.customPaidAmountMinor)
        assertEquals("أحمد إبراهيم", database.customerDao().getById(requireNotNull(customerId))?.name)
    }
}
