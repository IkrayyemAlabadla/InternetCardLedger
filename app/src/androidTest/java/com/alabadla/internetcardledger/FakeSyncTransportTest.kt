package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.repository.CustomerRepository
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.InvalidSyncBatchException
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.transport.FakeSyncTransport
import com.alabadla.internetcardledger.data.sync.transport.TransportStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FakeSyncTransportTest {

    private lateinit var context: Context
    private lateinit var database: LedgerDatabase
    private lateinit var syncEngine: SyncEngine
    private lateinit var customerRepo: CustomerRepository
    private lateinit var fakeTransport: FakeSyncTransport

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val deviceIdProvider = object : DeviceIdProvider(context) {
            override fun getDeviceId(): String = "device-local-001"
        }

        syncEngine = SyncEngine(
            database = database,
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = { 1000L },
        )

        customerRepo = CustomerRepository(
            customerDao = database.customerDao(),
            deviceIdProvider = deviceIdProvider,
            currentTimeMillis = { 1000L },
            syncEngine = syncEngine,
        )

        fakeTransport = FakeSyncTransport()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun connectionLoss_updatesStageAndPreservesLocalData() = runBlocking {
        val custId = customerRepo.addCustomer("زبون محلي 1")

        fakeTransport.startDiscovery()
        assertEquals(TransportStage.SEARCHING, fakeTransport.state.value.stage)

        fakeTransport.simulateEndpointFound("ep1", "جهاز مجاور")
        assertEquals(TransportStage.FOUND_DEVICE, fakeTransport.state.value.stage)

        // Connection drops
        fakeTransport.simulateConnectionLoss()
        assertEquals(TransportStage.FAILED, fakeTransport.state.value.stage)
        assertNotNull(fakeTransport.state.value.errorMessage)

        // Verify local data is completely preserved
        val localCust = customerRepo.observeCustomer(custId).first()
        assertNotNull(localCust)
        assertEquals("زبون محلي 1", localCust?.name)
    }

    @Test
    fun retrySync_allowsRetryingAfterFailure() = runBlocking {
        fakeTransport.startDiscovery()
        fakeTransport.simulateConnectionLoss()
        assertEquals(TransportStage.FAILED, fakeTransport.state.value.stage)

        // Retry sync
        fakeTransport.startDiscovery()
        assertEquals(TransportStage.SEARCHING, fakeTransport.state.value.stage)

        fakeTransport.simulateEndpointFound("ep2", "جهاز مجاور 2", "123456")
        assertEquals(TransportStage.FOUND_DEVICE, fakeTransport.state.value.stage)
        assertEquals("123456", fakeTransport.state.value.discoveredEndpoint?.authDigits)
    }

    @Test
    fun rejection_cancelsConnectionWithoutDataExchange() = runBlocking {
        fakeTransport.startAdvertising("هاتف العم الرئيسي")
        fakeTransport.rejectConnection("ep1")

        assertEquals(TransportStage.FAILED, fakeTransport.state.value.stage)
        assertTrue(fakeTransport.sentBatches.isEmpty())
    }

    @Test
    fun corruptedBatch_isRejectedAndRollsBack() = runBlocking {
        fakeTransport.simulateCorruptedBatchOnReceive = true
        var errorThrown = false

        fakeTransport.setBatchReceivedListener { _, batch ->
            try {
                syncEngine.processIncomingBatch(batch)
            } catch (_: InvalidSyncBatchException) {
                errorThrown = true
            }
        }

        fakeTransport.acceptConnection("ep1")

        val batch = syncEngine.generateSyncBatch()
        fakeTransport.simulateReceiveBatch("ep1", batch)

        assertTrue(errorThrown)
        assertTrue(database.customerDao().observeActive().first().isEmpty())
    }

    @Test
    fun duplicateBatch_handledSafelyWithoutDuplicates() = runBlocking {
        customerRepo.addCustomer("زبون أصلي")
        val batch = syncEngine.generateSyncBatch()

        val s1 = syncEngine.processIncomingBatch(batch)
        val s2 = syncEngine.processIncomingBatch(batch) // duplicate replay

        assertEquals(s1.id, s2.id)
        assertEquals(1, customerRepo.observeCustomers().first().size)
    }

    @Test
    fun successfulSync_exchangesBatchesAndCompletes() = runBlocking {
        val remoteCustId = "remote-c-1"

        fakeTransport.setBatchReceivedListener { endpointId, incomingBatch ->
            syncEngine.processIncomingBatch(incomingBatch)
        }

        fakeTransport.acceptConnection("ep-remote")
        assertEquals(TransportStage.RECEIVING_DATA, fakeTransport.state.value.stage)

        // Create remote batch
        val remoteBatch = com.alabadla.internetcardledger.data.sync.SyncBatch(
            batchId = "batch-remote-1",
            senderDeviceId = "remote-dev-1",
            senderDeviceName = "هاتف فرعي أ",
            senderRole = DeviceRole.SECONDARY,
            createdAt = 1000L,
            maxSequenceNumber = 1L,
            changes = emptyList(),
            customers = listOf(
                com.alabadla.internetcardledger.data.local.entity.CustomerEntity(
                    id = remoteCustId,
                    name = "زبون قادم من البلوتوث",
                    phone = "0599000888",
                    notes = null,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    createdByDeviceId = "remote-dev-1",
                    syncStatus = com.alabadla.internetcardledger.data.local.SyncStatus.PENDING,
                    isDeleted = false,
                ),
            ),
            sales = emptyList(),
            payments = emptyList(),
        )

        fakeTransport.simulateReceiveBatch("ep-remote", remoteBatch)

        val syncedCustomer = customerRepo.observeCustomer(remoteCustId).first()
        assertNotNull(syncedCustomer)
        assertEquals("زبون قادم من البلوتوث", syncedCustomer?.name)
    }
}
