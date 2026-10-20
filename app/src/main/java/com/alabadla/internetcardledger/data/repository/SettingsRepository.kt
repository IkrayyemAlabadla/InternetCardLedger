package com.alabadla.internetcardledger.data.repository

import android.content.Context
import com.alabadla.internetcardledger.data.DeviceIdProvider
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceDao
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceIdentityEntity
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.SyncDao
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.SyncSessionEntity
import kotlinx.coroutines.flow.Flow

enum class BackupReminder { DAILY, WEEKLY, NEVER }

data class LocalSettings(
    val onboardingComplete: Boolean,
    val backupReminder: BackupReminder,
    val identity: DeviceIdentityEntity?,
)

class SettingsRepository(
    context: Context,
    private val deviceIdProvider: DeviceIdProvider,
    private val syncEngine: SyncEngine,
    private val syncDao: SyncDao,
    private val approvedDeviceDao: ApprovedDeviceDao,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = preferences.getBoolean(KEY_ONBOARDING, false)

    suspend fun load(): LocalSettings = LocalSettings(
        onboardingComplete = isOnboardingComplete(),
        backupReminder = runCatching {
            BackupReminder.valueOf(preferences.getString(KEY_REMINDER, null) ?: BackupReminder.WEEKLY.name)
        }.getOrDefault(BackupReminder.WEEKLY),
        identity = syncDao.getDeviceIdentity(deviceIdProvider.getDeviceId()),
    )

    suspend fun completeOnboarding(deviceName: String, role: DeviceRole) {
        require(deviceName.isNotBlank()) { "أدخل اسم الجهاز." }
        syncEngine.getOrCreateLocalIdentity(deviceName.trim(), role)
        check(preferences.edit().putBoolean(KEY_ONBOARDING, true).commit())
    }

    suspend fun updateIdentity(deviceName: String, role: DeviceRole) {
        require(deviceName.isNotBlank()) { "أدخل اسم الجهاز." }
        val current = syncEngine.getOrCreateLocalIdentity()
        syncDao.upsertDeviceIdentity(
            current.copy(deviceName = deviceName.trim(), role = role, updatedAt = System.currentTimeMillis()),
        )
    }

    fun setBackupReminder(value: BackupReminder) {
        check(preferences.edit().putString(KEY_REMINDER, value.name).commit())
    }

    fun approvedDevices(): Flow<List<ApprovedDeviceEntity>> = approvedDeviceDao.observeAllApprovedDevices()
    fun auditSessions(): Flow<List<SyncSessionEntity>> = syncDao.observeAllSessions()
    suspend fun removeApprovedDevice(deviceId: String) = approvedDeviceDao.deleteApprovedDevice(deviceId)

    private companion object {
        const val FILE_NAME = "application_settings"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_REMINDER = "backup_reminder"
    }
}
