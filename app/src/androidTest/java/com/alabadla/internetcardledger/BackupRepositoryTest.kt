package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.backup.BackupDocument
import com.alabadla.internetcardledger.data.backup.BackupJsonCodec
import com.alabadla.internetcardledger.data.backup.InvalidBackupException
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.repository.BackupRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: LedgerDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, database.backupDao(), DeviceIdProvider(context)) { 9_000L }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun exportAndImport_includeDeletedRecordsAndRelationships() = runBlocking {
        val customer = customer(CUSTOMER_A, updatedAt = 100L, deleted = true)
        val sale = sale(SALE_A, customer.id, updatedAt = 110L)
        val payment = payment(PAYMENT_A, customer.id, sale.id, updatedAt = 120L)
        database.customerDao().upsert(customer)
        database.saleDao().upsert(sale)
        database.paymentDao().upsert(payment)

        val bytes = ByteArrayOutputStream().also { repository.export(it) }.toByteArray()
        val restored = repository.inspect(ByteArrayInputStream(bytes))

        assertEquals(1, restored.version)
        assertEquals(3, restored.recordCount)
        assertTrue(restored.customers.single().isDeleted)
        assertEquals(customer.id, restored.sales.single().customerId)
        assertEquals(sale.id, restored.payments.single().saleId)
        assertEquals(customer.id, restored.payments.single().customerId)
    }

    @Test
    fun corruptedOrInvalidBackup_isRejectedWithoutChangingDatabase() = runBlocking {
        val original = customer(CUSTOMER_A, 100L)
        database.customerDao().upsert(original)

        expectInvalid { repository.inspect(ByteArrayInputStream("not-json".toByteArray())) }
        val brokenRelationship = document(
            customers = emptyList(),
            sales = listOf(sale(SALE_A, CUSTOMER_A, 100L)),
        )
        expectInvalid { BackupJsonCodec.validate(brokenRelationship) }

        assertEquals(listOf(original), database.backupDao().snapshot().customers)
    }

    @Test
    fun merge_insertsNewerSkipsEqualAndOlderAndMarksConflict() = runBlocking {
        val newerTarget = customer(CUSTOMER_A, 100L, name = "قديم")
        val conflictTarget = customer(CUSTOMER_B, 200L, name = "محلي")
        val equalTarget = customer(CUSTOMER_C, 300L, name = "نفسه")
        val olderTarget = customer(CUSTOMER_D, 400L, name = "الأحدث محليًا")
        listOf(newerTarget, conflictTarget, equalTarget, olderTarget).forEach { customer ->
            database.customerDao().upsert(customer)
        }
        val newId = UUID.randomUUID().toString()
        val incoming = document(
            customers = listOf(
                customer(CUSTOMER_A, 500L, name = "حديث"),
                customer(CUSTOMER_B, 200L, name = "مختلف"),
                equalTarget.copy(syncStatus = SyncStatus.SYNCED),
                customer(CUSTOMER_D, 350L, name = "قديم مستورد"),
                customer(newId, 600L, name = "جديد"),
            ),
        )

        val report = repository.merge(incoming)
        val records = database.backupDao().snapshot().customers.associateBy { it.id }

        assertEquals(1, report.inserted)
        assertEquals(1, report.updated)
        assertEquals(2, report.unchanged)
        assertEquals(1, report.conflicts)
        assertEquals("حديث", records.getValue(CUSTOMER_A).name)
        assertEquals(SyncStatus.CONFLICT, records.getValue(CUSTOMER_B).syncStatus)
        assertEquals("محلي", records.getValue(CUSTOMER_B).name)
        assertEquals("الأحدث محليًا", records.getValue(CUSTOMER_D).name)
        assertNotNull(records[newId])
    }

    @Test
    fun replace_restoresRelatedRowsAndCreatesSafetyBackup() = runBlocking {
        database.customerDao().upsert(customer(CUSTOMER_A, 100L))
        val replacementCustomer = customer(CUSTOMER_B, 200L)
        val replacementSale = sale(SALE_A, CUSTOMER_B, 210L)
        val replacementPayment = payment(PAYMENT_A, CUSTOMER_B, SALE_A, 220L)
        val before = safetyFiles().toSet()

        val report = repository.replace(document(
            customers = listOf(replacementCustomer),
            sales = listOf(replacementSale),
            payments = listOf(replacementPayment),
        ))
        val snapshot = database.backupDao().snapshot()

        assertEquals(3, report.inserted)
        assertNotNull(report.safetyBackupName)
        assertEquals(listOf(replacementCustomer), snapshot.customers)
        assertEquals(listOf(replacementSale), snapshot.sales)
        assertEquals(listOf(replacementPayment), snapshot.payments)
        assertTrue(safetyFiles().toSet().minus(before).isNotEmpty())
    }

    @Test
    fun replaceTransaction_rollsBackEveryTableWhenForeignKeyWriteFails() = runBlocking {
        val original = customer(CUSTOMER_A, 100L)
        database.customerDao().upsert(original)
        val invalidDocument = document(
            customers = listOf(customer(CUSTOMER_B, 200L)),
            payments = listOf(payment(PAYMENT_A, CUSTOMER_C, null, 210L)),
        )

        var failed = false
        try {
            database.backupDao().replace(invalidDocument)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue("Expected the foreign-key write to fail", failed)
        val snapshot = database.backupDao().snapshot()
        assertEquals(listOf(original), snapshot.customers)
        assertTrue(snapshot.sales.isEmpty())
        assertTrue(snapshot.payments.isEmpty())
    }

    private fun document(
        customers: List<CustomerEntity> = emptyList(),
        sales: List<SaleEntity> = emptyList(),
        payments: List<PaymentEntity> = emptyList(),
    ) = BackupDocument(
        createdAt = 1_000L,
        deviceId = DEVICE,
        customers = customers,
        sales = sales,
        payments = payments,
    )

    private fun customer(id: String, updatedAt: Long, name: String = "زبون", deleted: Boolean = false) = CustomerEntity(
        id = id, name = name, phone = null, notes = null,
        createdAt = 10L, updatedAt = updatedAt, createdByDeviceId = DEVICE,
        syncStatus = SyncStatus.PENDING, isDeleted = deleted,
    )

    private fun sale(id: String, customerId: String?, updatedAt: Long) = SaleEntity(
        id = id, customerId = customerId, description = "بطاقة", quantity = 2,
        unitPriceMinor = 1_000L, soldAt = 20L, notes = null,
        createdAt = 10L, updatedAt = updatedAt, createdByDeviceId = DEVICE,
        syncStatus = SyncStatus.PENDING, isDeleted = false,
    )

    private fun payment(id: String, customerId: String, saleId: String?, updatedAt: Long) = PaymentEntity(
        id = id, customerId = customerId, saleId = saleId, amountMinor = 500L,
        paidAt = 20L, notes = null, createdAt = 10L, updatedAt = updatedAt,
        createdByDeviceId = DEVICE, syncStatus = SyncStatus.PENDING, isDeleted = false,
    )

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: InvalidBackupException) { failed = true }
        assertTrue("Expected InvalidBackupException", failed)
    }

    private fun safetyFiles(): List<String> =
        context.filesDir.resolve("safety-backups").list()?.toList().orEmpty()

    private companion object {
        val DEVICE: String = UUID.randomUUID().toString()
        val CUSTOMER_A: String = UUID.randomUUID().toString()
        val CUSTOMER_B: String = UUID.randomUUID().toString()
        val CUSTOMER_C: String = UUID.randomUUID().toString()
        val CUSTOMER_D: String = UUID.randomUUID().toString()
        val SALE_A: String = UUID.randomUUID().toString()
        val PAYMENT_A: String = UUID.randomUUID().toString()
    }
}
