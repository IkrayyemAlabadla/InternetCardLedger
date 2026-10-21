package com.alabadla.internetcardledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.local.LedgerDatabase
import com.alabadla.internetcardledger.data.repository.BackupReminder
import com.alabadla.internetcardledger.data.repository.SettingsRepository
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.security.PinSecurityManager
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
class SecurityAndSettingsTest {
    private lateinit var context: Context
    private lateinit var database: LedgerDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("application_settings", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val deviceProvider = object : DeviceIdProvider(context) {
            override fun getDeviceId(): String = DEVICE_ID
        }
        repository = SettingsRepository(
            context,
            deviceProvider,
            SyncEngine(database, deviceProvider, currentTimeMillis = { 1_000L }),
            database.syncDao(),
            database.approvedDeviceDao(),
        )
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("application_settings", Context.MODE_PRIVATE).edit().clear().commit()
        database.close()
    }

    @Test
    fun onboardingAndRole_arePersistedAndOnlyCompleteAfterSave() = runBlocking {
        assertFalse(repository.load().onboardingComplete)

        repository.completeOnboarding("هاتف المتجر", DeviceRole.SECONDARY)
        val configured = repository.load()

        assertTrue(configured.onboardingComplete)
        assertEquals("هاتف المتجر", configured.identity?.deviceName)
        assertEquals(DeviceRole.SECONDARY, configured.identity?.role)

        repository.updateIdentity("هاتف العم", DeviceRole.MAIN)
        assertEquals(DeviceRole.MAIN, repository.load().identity?.role)
    }

    @Test
    fun reminderAndApprovedDeviceRemoval_arePersisted() = runBlocking {
        repository.setBackupReminder(BackupReminder.DAILY)
        assertEquals(BackupReminder.DAILY, repository.load().backupReminder)
        val device = ApprovedDeviceEntity("peer", "هاتف فرعي", 10L, null)
        database.approvedDeviceDao().upsertApprovedDevice(device)
        assertEquals(listOf(device), repository.approvedDevices().first())

        assertEquals(1, repository.removeApprovedDevice(device.deviceId))
        assertTrue(repository.approvedDevices().first().isEmpty())
    }

    @Test
    fun pin_isHashedVerifiedChangedAndResetOnlyThroughExplicitRecoveryMethod() {
        val security = PinSecurityManager(context)
        security.setPin("2468")
        assertTrue(security.hasPin())
        assertTrue(security.verify("2468"))
        assertFalse(security.verify("1111"))
        assertFalse(security.changePin("1111", "1357"))
        assertTrue(security.changePin("2468", "1357"))
        assertTrue(security.verify("1357"))

        security.resetAfterRecoveryBackup("9876")
        assertTrue(security.verify("9876"))
        assertFalse(security.verify("1357"))
    }

    private companion object { const val DEVICE_ID = "settings-test-device" }
}
