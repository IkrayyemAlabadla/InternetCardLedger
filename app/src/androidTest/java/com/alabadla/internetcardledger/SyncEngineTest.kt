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
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.InvalidSyncBatchException
import com.alabadla.internetcardledger.data.sync.SyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createTestDevice(
        deviceId: String,
        deviceName: String,
        role: DeviceRole,
        initialTime: Long = 1000L,
    ): TestDeviceNode {
        val database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        var currentTime = initialTime
        val timeSupplier = { currentTime }

        val deviceIdProvider = object : DeviceIdProvider(context) {
            override fun getDeviceId(): String = deviceId
        }

        val syncEngine = SyncEngine(
            database = database,
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
        )

        runBlocking {
            syncEngine.getOrCreateLocalIdentity(deviceName, role)
        }

        val customerRepo = CustomerRepository(
            customerDao = database.customerDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            syncEngine = syncEngine,
        )

        val saleRepo = SaleRepository(
            saleDao = database.saleDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            syncEngine = syncEngine,
        )

        val paymentRepo = PaymentRepository(
            paymentDao = database.paymentDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = timeSupplier,
            syncEngine = syncEngine,
        )

        return TestDeviceNode(
            database = database,
            syncEngine = syncEngine,
            customerRepo = customerRepo,
            saleRepo = saleRepo,
            paymentRepo = paymentRepo,
            setTime = { currentTime = it },
            getTime = { currentTime },
        )
    }

    data class TestDeviceNode(
        val database: LedgerDatabase,
        val syncEngine: SyncEngine,
        val customerRepo: CustomerRepository,
        val saleRepo: SaleRepository,
        val paymentRepo: PaymentRepository,
        val setTime: (Long) -> Unit,
        val getTime: () -> Long,
    ) {
        fun close() {
            database.close()
        }
    }

    @Test
    fun persistentDeviceIdentity_mainRoleCannotBeSilentlyChanged() = runBlocking {
        val mainDevice = createTestDevice("device-main-001", "هاتف العم الرئيسي", DeviceRole.MAIN)
        val identity = mainDevice.syncEngine.getOrCreateLocalIdentity()

        assertEquals("device-main-001", identity.deviceId)
        assertEquals("هاتف العم الرئيسي", identity.deviceName)
        assertEquals(DeviceRole.MAIN, identity.role)

        try {
            mainDevice.syncEngine.updateDeviceRole(DeviceRole.SECONDARY)
            fail("Expected IllegalStateException when attempting to change MAIN to SECONDARY")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("الرئيسي") == true)
        }

        mainDevice.close()
    }

    @Test
    fun offlineEdits_generateChangeLogsAndIncrementSequenceNumbers() = runBlocking {
        val node = createTestDevice("device-sec-1", "هاتف فرعي 1", DeviceRole.SECONDARY)

        val custId = node.customerRepo.addCustomer("أحمد خليل", "0599000111")
        val saleId = node.saleRepo.addSale(custId, "بطاقة إنترنت 10G", 2, 1000L)

        val pendingLogs = node.database.syncDao().getPendingChangeLogs()
        assertEquals(2, pendingLogs.size)

        val custLog = pendingLogs.find { it.recordId == custId }
        val saleLog = pendingLogs.find { it.recordId == saleId }

        assertNotNull(custLog)
        assertNotNull(saleLog)
        assertEquals(1L, custLog?.sequenceNumber)
        assertEquals(2L, saleLog?.sequenceNumber)

        node.close()
    }

    @Test
    fun twoWaySync_exchangesChangesBetweenSecondaryAndMain() = runBlocking {
        val main = createTestDevice("main-device", "العم الرئيسي", DeviceRole.MAIN, 1000L)
        val sec1 = createTestDevice("sec-1", "فرعي 1", DeviceRole.SECONDARY, 1000L)

        // Sec1 adds customer
        val custId1 = sec1.customerRepo.addCustomer("خالد محمود", "0599111222")

        // Sec1 generates batch
        val sec1Batch = sec1.syncEngine.generateSyncBatch()
        assertEquals(1, sec1Batch.customers.size)

        // Main imports sec1Batch and generates response
        val mainTwoWayResult = main.syncEngine.performTwoWaySync(sec1Batch)
        assertEquals("SUCCESS", mainTwoWayResult.session.status)

        // Check main has customer
        val mainCust = main.customerRepo.observeCustomer(custId1).first()
        assertNotNull(mainCust)
        assertEquals("خالد محمود", mainCust?.name)

        // Main creates sale
        main.setTime(2000L)
        val saleId1 = main.saleRepo.addSale(custId1, "بطاقة 20G", 1, 2000L)

        // Sec1 imports response batch from main
        sec1.syncEngine.processIncomingBatch(mainTwoWayResult.responseBatch)

        val sec1Sale = sec1.saleRepo.observeSale(saleId1).first()
        assertNotNull(sec1Sale)
        assertEquals("بطاقة 20G", sec1Sale?.description)

        main.close()
        sec1.close()
    }

    @Test
    fun threeDevicesSimulation_allDataConvergesToMainAndSecondaries() = runBlocking {
        val main = createTestDevice("main", "العم الرئيسي", DeviceRole.MAIN, 1000L)
        val sec1 = createTestDevice("sec1", "فرعي 1", DeviceRole.SECONDARY, 1000L)
        val sec2 = createTestDevice("sec2", "فرعي 2", DeviceRole.SECONDARY, 1000L)

        // Sec1 adds Customer A and Sale A
        val custA = sec1.customerRepo.addCustomer("زبون أ")
        sec1.saleRepo.addSale(custA, "مبيعات أ", 1, 500L)

        // Sec2 adds Customer B and Sale B
        val custB = sec2.customerRepo.addCustomer("زبون ب")
        sec2.saleRepo.addSale(custB, "مبيعات ب", 2, 1000L)

        // Sec1 syncs with Main
        val sec1Batch = sec1.syncEngine.generateSyncBatch()
        val mainRes1 = main.syncEngine.performTwoWaySync(sec1Batch)
        sec1.syncEngine.processIncomingBatch(mainRes1.responseBatch)

        // Sec2 syncs with Main
        val sec2Batch = sec2.syncEngine.generateSyncBatch()
        val mainRes2 = main.syncEngine.performTwoWaySync(sec2Batch)
        sec2.syncEngine.processIncomingBatch(mainRes2.responseBatch)

        // Sec1 syncs again with Main to pick up Sec2's changes
        val sec1Batch2 = sec1.syncEngine.generateSyncBatch()
        val mainRes3 = main.syncEngine.performTwoWaySync(sec1Batch2)
        sec1.syncEngine.processIncomingBatch(mainRes3.responseBatch)

        // Verify all devices have both customers
        assertEquals(2, main.customerRepo.observeCustomers().first().size)
        assertEquals(2, sec1.customerRepo.observeCustomers().first().size)
        assertEquals(2, sec2.customerRepo.observeCustomers().first().size)

        main.close()
        sec1.close()
        sec2.close()
    }

    @Test
    fun simultaneousEdits_createConflictAndSaveBothVersions() = runBlocking {
        val main = createTestDevice("main", "الرئيسي", DeviceRole.MAIN, 1000L)
        val sec = createTestDevice("sec", "الفرعي", DeviceRole.SECONDARY, 1000L)

        // Initial customer created and synced
        val custId = main.customerRepo.addCustomer("سعيد علي")
        val mainBatch1 = main.syncEngine.generateSyncBatch()
        sec.syncEngine.processIncomingBatch(mainBatch1)

        // Both edit offline simultaneously
        main.setTime(2000L)
        val mainCust = requireNotNull(main.customerRepo.observeCustomer(custId).first())
        main.customerRepo.updateCustomer(mainCust.copy(name = "سعيد علي (تعديل الرئيسي)"))

        sec.setTime(2000L)
        val secCust = requireNotNull(sec.customerRepo.observeCustomer(custId).first())
        sec.customerRepo.updateCustomer(secCust.copy(name = "سعيد علي (تعديل الفرعي)"))

        // Sync sec -> main
        val secBatch = sec.syncEngine.generateSyncBatch()
        val session = main.syncEngine.processIncomingBatch(secBatch)

        assertEquals("CONFLICT_DETECTED", session.status)
        assertEquals(1, session.conflictsCount)

        val conflicts = main.database.syncDao().getUnresolvedConflicts()
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()

        assertEquals(custId, conflict.recordId)
        assertTrue(conflict.mainVersionJson.contains("تعديل الرئيسي"))
        assertTrue(conflict.secondaryVersionJson.contains("تعديل الفرعي"))

        // Resolve conflict by accepting MAIN version
        main.syncEngine.acceptMainVersion(conflict.id)

        val resolvedCust = main.customerRepo.observeCustomer(custId).first()
        assertEquals("سعيد علي (تعديل الرئيسي)", resolvedCust?.name)

        main.close()
        sec.close()
    }

    @Test
    fun softDeletion_synchronizesCorrectly() = runBlocking {
        val main = createTestDevice("main", "الرئيسي", DeviceRole.MAIN, 1000L)
        val sec = createTestDevice("sec", "الفرعي", DeviceRole.SECONDARY, 1000L)

        val custId = main.customerRepo.addCustomer("مؤقت للحذف")
        val b1 = main.syncEngine.generateSyncBatch()
        sec.syncEngine.processIncomingBatch(b1)

        main.setTime(3000L)
        main.customerRepo.deleteCustomer(custId)

        val b2 = main.syncEngine.generateSyncBatch()
        sec.syncEngine.processIncomingBatch(b2)

        assertTrue(sec.customerRepo.observeCustomers().first().isEmpty())
        val deletedInSec = sec.database.customerDao().getById(custId)
        assertNotNull(deletedInSec)
        assertTrue(deletedInSec?.isDeleted == true)

        main.close()
        sec.close()
    }

    @Test
    fun duplicateBatch_processedSafelyWithoutDuplicates() = runBlocking {
        val main = createTestDevice("main", "الرئيسي", DeviceRole.MAIN, 1000L)
        val sec = createTestDevice("sec", "الفرعي", DeviceRole.SECONDARY, 1000L)

        sec.customerRepo.addCustomer("زبون فرعي")
        val batch = sec.syncEngine.generateSyncBatch()

        val s1 = main.syncEngine.processIncomingBatch(batch)
        val s2 = main.syncEngine.processIncomingBatch(batch) // duplicate replay

        assertEquals(s1.id, s2.id)
        assertEquals(1, main.customerRepo.observeCustomers().first().size)

        main.close()
        sec.close()
    }

    @Test
    fun invalidBatch_rollsBackEntireBatch() = runBlocking {
        val main = createTestDevice("main", "الرئيسي", DeviceRole.MAIN, 1000L)
        val sec = createTestDevice("sec", "الفرعي", DeviceRole.SECONDARY, 1000L)

        // Valid customer
        sec.customerRepo.addCustomer("زبون صالح")
        val batch = sec.syncEngine.generateSyncBatch()

        // Mutate batch to contain an invalid customer (blank name)
        val corruptBatch = batch.copy(
            customers = batch.customers + com.alabadla.internetcardledger.data.local.entity.CustomerEntity(
                id = "corrupt-id",
                name = "", // Invalid blank name!
                phone = null,
                notes = null,
                createdAt = 1000L,
                updatedAt = 1000L,
                createdByDeviceId = "sec",
                syncStatus = SyncStatus.PENDING,
                isDeleted = false,
            ),
        )

        try {
            main.syncEngine.processIncomingBatch(corruptBatch)
            fail("Expected InvalidSyncBatchException")
        } catch (e: InvalidSyncBatchException) {
            assertTrue(e.message?.contains("بلا اسم") == true)
        }

        // Verify transactional rollback: NO customers inserted in main
        assertTrue(main.customerRepo.observeCustomers().first().isEmpty())

        main.close()
        sec.close()
    }
}
