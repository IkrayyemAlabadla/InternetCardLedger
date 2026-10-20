package com.alabadla.internetcardledger.data.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    // Device Identity
    @Query("SELECT * FROM device_identities WHERE device_id = :deviceId")
    suspend fun getDeviceIdentity(deviceId: String): DeviceIdentityEntity?

    @Query("SELECT * FROM device_identities")
    suspend fun getAllDeviceIdentities(): List<DeviceIdentityEntity>

    @Query("SELECT * FROM device_identities WHERE role = 'MAIN' LIMIT 1")
    suspend fun getMainDeviceIdentity(): DeviceIdentityEntity?

    @Upsert
    suspend fun upsertDeviceIdentity(identity: DeviceIdentityEntity)


    // Change Logs
    @Query("SELECT * FROM sync_change_logs WHERE id = :id")
    suspend fun getChangeLogById(id: String): SyncChangeLogEntity?

    @Query("SELECT * FROM sync_change_logs WHERE device_id = :deviceId AND sequence_number > :sequenceNumber ORDER BY sequence_number ASC")
    suspend fun getChangeLogsAfterSequence(deviceId: String, sequenceNumber: Long): List<SyncChangeLogEntity>

    @Query("SELECT * FROM sync_change_logs WHERE sync_status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingChangeLogs(): List<SyncChangeLogEntity>

    @Query("SELECT * FROM sync_change_logs WHERE record_id = :recordId ORDER BY created_at ASC")
    suspend fun getChangeLogsForRecord(recordId: String): List<SyncChangeLogEntity>

    @Query("SELECT MAX(sequence_number) FROM sync_change_logs WHERE device_id = :deviceId")
    suspend fun getMaxSequenceNumberForDevice(deviceId: String): Long?

    @Upsert
    suspend fun upsertChangeLog(changeLog: SyncChangeLogEntity)

    @Upsert
    suspend fun upsertChangeLogs(changeLogs: List<SyncChangeLogEntity>)


    // Conflicts
    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun getConflictById(id: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE record_id = :recordId AND status = 'UNRESOLVED'")
    suspend fun getUnresolvedConflictsForRecord(recordId: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE status = 'UNRESOLVED' ORDER BY created_at DESC")
    suspend fun getUnresolvedConflicts(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE status = 'UNRESOLVED' ORDER BY created_at DESC")
    fun observeUnresolvedConflicts(): Flow<List<SyncConflictEntity>>

    @Upsert
    suspend fun upsertConflict(conflict: SyncConflictEntity)


    // Sessions Audit Log
    @Query("SELECT * FROM sync_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SyncSessionEntity?

    @Query("SELECT * FROM sync_sessions ORDER BY started_at DESC")
    suspend fun getAllSessions(): List<SyncSessionEntity>

    @Query("SELECT * FROM sync_sessions ORDER BY started_at DESC")
    fun observeAllSessions(): Flow<List<SyncSessionEntity>>

    @Upsert
    suspend fun upsertSession(session: SyncSessionEntity)
}
