package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.local.SyncStatus
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
class SaleRepositoryTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: SaleRepository
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createSale_savesIdentityAndSyncMetadata() = runBlocking {
        val id = repository.addSale(
            customerId = null,
            description = "  بطاقة إنترنت  ",
            quantity = 2,
            unitPriceMinor = 1_500L,
            notes = "  ملاحظة  ",
        )

        val sale = database.saleDao().observeById(id).first()

        assertNotNull(sale)
        assertEquals("بطاقة إنترنت", sale?.description)
        assertEquals("ملاحظة", sale?.notes)
        assertEquals(1_000L, sale?.createdAt)
        assertEquals(1_000L, sale?.updatedAt)
        assertTrue(sale?.createdByDeviceId?.isNotBlank() == true)
        assertEquals(SyncStatus.PENDING, sale?.syncStatus)
        assertFalse(sale?.isDeleted ?: true)
    }

    @Test
    fun updateSale_preservesIdentityAndMarksRecordPending() = runBlocking {
        val id = repository.addSale(null, "بطاقة", 1, 500L)
        val original = requireNotNull(database.saleDao().observeById(id).first())
        database.saleDao().upsert(original.copy(syncStatus = SyncStatus.SYNCED))
        currentTime = 2_000L

        repository.updateSale(
            original.copy(
                description = "بطاقة معدلة",
                quantity = 3,
                unitPriceMinor = 750L,
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
        repository.addSale(null, "بطاقة إنترنت يومية", 1, 100L, notes = "سريعة")
        repository.addSale(null, "جهاز مودم", 1, 200L, notes = "منزلي")

        val descriptionMatches = repository.observeSales("إنترنت").first()
        val notesMatches = repository.observeSales("منزلي").first()

        assertEquals(listOf("بطاقة إنترنت يومية"), descriptionMatches.map { it.description })
        assertEquals(listOf("جهاز مودم"), notesMatches.map { it.description })
    }

    @Test
    fun deleteSale_softDeletesAndMarksRecordPending() = runBlocking {
        val id = repository.addSale(null, "بطاقة", 1, 500L)
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
}
