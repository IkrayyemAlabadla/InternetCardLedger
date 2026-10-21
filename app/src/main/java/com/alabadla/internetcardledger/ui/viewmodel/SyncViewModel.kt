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

    private var initialBatchSentToEndpoint: String? = null

    init {
        viewModelScope.launch {
            val identity = syncEngine.getOrCreateLocalIdentity()
            _uiState.update { it.copy(localIdentity = identity) }
        }

        viewModelScope.launch {
            transport.state.collect { tState ->
                _uiState.update { it.copy(transportState = tState) }
                val endpointId = tState.connectedEndpointId
                val identity = _uiState.value.localIdentity
                if (
                    endpointId != null &&
                    identity?.role == DeviceRole.SECONDARY &&
                    initialBatchSentToEndpoint != endpointId
                ) {
                    initialBatchSentToEndpoint = endpointId
                    val sent = transport.sendBatch(endpointId, syncEngine.generateSyncBatch())
                    if (!sent) initialBatchSentToEndpoint = null
                }
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
            initialBatchSentToEndpoint = null
            transport.stop()
            val identity = syncEngine.getOrCreateLocalIdentity()
            if (identity.role == DeviceRole.MAIN) {
                transport.startAdvertising(identity.deviceName)
            } else {
                transport.startDiscovery()
            }
        }
    }

    fun approveConnection(endpointId: String) {
        transport.acceptConnection(endpointId)
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
        initialBatchSentToEndpoint = null
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
                val identity = syncEngine.getOrCreateLocalIdentity()
                val result = if (identity.role == DeviceRole.MAIN) {
                    syncEngine.performTwoWaySync(incomingBatch)
                } else {
                    val session = syncEngine.processIncomingBatch(incomingBatch)
                    com.alabadla.internetcardledger.data.sync.TwoWaySyncResult(
                        session = session,
                        responseBatch = incomingBatch.copy(changes = emptyList(), customers = emptyList(), sales = emptyList(), payments = emptyList()),
                    )
                }
                val now = System.currentTimeMillis()
                val previousApproval = approvedDeviceDao.getApprovedDevice(incomingBatch.senderDeviceId)
                approvedDeviceDao.upsertApprovedDevice(
                    ApprovedDeviceEntity(
                        deviceId = incomingBatch.senderDeviceId,
                        deviceName = incomingBatch.senderDeviceName,
                        approvedAt = previousApproval?.approvedAt ?: now,
                        lastSyncedAt = now,
                    ),
                )
                val sentCount = if (identity.role == DeviceRole.MAIN) {
                    val sent = transport.sendBatch(endpointId, result.responseBatch)
                    if (!sent) error("تعذر إرسال رد المزامنة.")
                    result.responseBatch.changes.size
                } else {
                    0
                }
                _uiState.update {
                    it.copy(
                        summary = SyncResultSummary(
                            uploadedCount = sentCount,
                            downloadedCount = incomingBatch.changes.size,
                            skippedCount = 0,
                            conflictsCount = result.session.conflictsCount,
                        ),
                        transportState = TransportState(
                            stage = if (result.session.conflictsCount > 0) TransportStage.FAILED else TransportStage.SUCCESS,
                            errorMessage = if (result.session.conflictsCount > 0) "تم اكتشاف تعارضات. يرجى مراجعتها." else null,
                        ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        transportState = TransportState(
                            stage = TransportStage.FAILED,
                            errorMessage = "فشلت المزامنة. لم تُحذف بياناتك ويمكنك المحاولة مرة أخرى.",
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
