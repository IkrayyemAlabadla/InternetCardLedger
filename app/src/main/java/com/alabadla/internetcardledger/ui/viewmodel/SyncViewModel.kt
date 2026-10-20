package com.alabadla.internetcardledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.local.entity.PaymentEntity
import com.alabadla.internetcardledger.data.local.entity.SaleEntity
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceDao
import com.alabadla.internetcardledger.data.sync.ApprovedDeviceEntity
import com.alabadla.internetcardledger.data.sync.DeviceIdentityEntity
import com.alabadla.internetcardledger.data.sync.DeviceRole
import com.alabadla.internetcardledger.data.sync.SyncConflictEntity
import com.alabadla.internetcardledger.data.sync.SyncDao
import com.alabadla.internetcardledger.data.sync.SyncEngine
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec
import com.alabadla.internetcardledger.data.sync.SyncSessionEntity
import com.alabadla.internetcardledger.data.sync.transport.SyncTransport
import com.alabadla.internetcardledger.data.sync.transport.TransportStage
import com.alabadla.internetcardledger.data.sync.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncResultSummary(
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictsCount: Int = 0,
)

data class SyncUiState(
    val localIdentity: DeviceIdentityEntity? = null,
    val transportState: TransportState = TransportState(),
    val summary: SyncResultSummary = SyncResultSummary(),
    val errorMessage: String? = null,
    val showPermissionPrompt: Boolean = false,
)

class SyncViewModel(
    private val syncEngine: SyncEngine,
    private val syncDao: SyncDao,
    private val approvedDeviceDao: ApprovedDeviceDao,
    private val transport: SyncTransport,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    val approvedDevices: StateFlow<List<ApprovedDeviceEntity>> = approvedDeviceDao.observeAllApprovedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unresolvedConflicts: StateFlow<List<SyncConflictEntity>> = syncDao.observeUnresolvedConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<SyncSessionEntity>> = syncDao.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val identity = syncEngine.getOrCreateLocalIdentity()
            _uiState.update { it.copy(localIdentity = identity) }
        }

        viewModelScope.launch {
            transport.state.collect { tState ->
                _uiState.update { it.copy(transportState = tState) }
            }
        }

        transport.setBatchReceivedListener { endpointId, incomingBatch ->
            handleIncomingBatch(endpointId, incomingBatch)
        }
    }

    fun requestStartSync() {
        _uiState.update { it.copy(showPermissionPrompt = true) }
    }

    fun dismissPermissionPrompt() {
        _uiState.update { it.copy(showPermissionPrompt = false) }
    }

    fun onPermissionsGranted() {
        _uiState.update { it.copy(showPermissionPrompt = false) }
        startSync()
    }

    fun startSync() {
        viewModelScope.launch {
            val identity = syncEngine.getOrCreateLocalIdentity()
            if (identity.role == DeviceRole.MAIN) {
                transport.startAdvertising(identity.deviceName)
            } else {
                transport.startDiscovery()
            }
        }
    }

    fun approveConnection(endpointId: String, peerName: String, peerDeviceId: String? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deviceId = peerDeviceId ?: endpointId
            approvedDeviceDao.upsertApprovedDevice(
                ApprovedDeviceEntity(
                    deviceId = deviceId,
                    deviceName = peerName,
                    approvedAt = now,
                    lastSyncedAt = now,
                ),
            )
            transport.acceptConnection(endpointId)

            // Send local batch
            val batch = syncEngine.generateSyncBatch()
            transport.sendBatch(endpointId, batch)
        }
    }

    fun rejectConnection(endpointId: String) {
        transport.rejectConnection(endpointId)
    }

    fun removeApprovedDevice(deviceId: String) {
        viewModelScope.launch {
            approvedDeviceDao.deleteApprovedDevice(deviceId)
        }
    }

    fun acceptMainVersion(conflictId: String) {
        viewModelScope.launch {
            syncEngine.acceptMainVersion(conflictId)
        }
    }

    fun acceptSecondaryVersion(conflictId: String) {
        viewModelScope.launch {
            syncEngine.acceptSecondaryVersion(conflictId)
        }
    }

    fun manuallyResolveConflict(conflictId: String, customerName: String? = null) {
        viewModelScope.launch {
            val conflict = syncDao.getConflictById(conflictId) ?: return@launch
            when (conflict.entityType) {
                "CUSTOMER" -> {
                    val base = SyncJsonCodec.customerFromJson(conflict.mainVersionJson)
                    val resolved = base.copy(name = customerName ?: base.name)
                    syncEngine.manuallyResolveConflict(conflictId, SyncJsonCodec.customerToJson(resolved))
                }
                else -> {
                    syncEngine.acceptMainVersion(conflictId)
                }
            }
        }
    }

    fun stopSync() {
        transport.stop()
        _uiState.update {
            it.copy(
                transportState = TransportState(stage = TransportStage.IDLE),
                errorMessage = null,
            )
        }
    }

    private fun handleIncomingBatch(endpointId: String, incomingBatch: com.alabadla.internetcardledger.data.sync.SyncBatch) {
        viewModelScope.launch {
            runCatching {
                val result = syncEngine.performTwoWaySync(incomingBatch)
                _uiState.update {
                    it.copy(
                        summary = SyncResultSummary(
                            uploadedCount = result.responseBatch.changes.size,
                            downloadedCount = incomingBatch.changes.size,
                            skippedCount = 0,
                            conflictsCount = result.session.conflictsCount,
                        ),
                        transportState = TransportState(
                            stage = if (result.session.conflictsCount > 0) TransportStage.FAILED else TransportStage.SUCCESS,
                            errorMessage = if (result.session.conflictsCount > 0) "تم اكتشاف تضاربات. يرجى مراجعتها" else null,
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        transportState = TransportState(
                            stage = TransportStage.FAILED,
                            errorMessage = "فشلت المزامنة: ${err.message}",
                        ),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            syncEngine: SyncEngine,
            syncDao: SyncDao,
            approvedDeviceDao: ApprovedDeviceDao,
            transport: SyncTransport,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SyncViewModel(syncEngine, syncDao, approvedDeviceDao, transport)
            }
        }
    }
}
