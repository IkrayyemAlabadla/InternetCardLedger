package com.alabadla.internetcardledger.data.sync.transport

import com.alabadla.internetcardledger.data.local.SyncStatus
import com.alabadla.internetcardledger.data.local.entity.CustomerEntity
import com.alabadla.internetcardledger.data.sync.SyncBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncTransport : SyncTransport {
    private val _state = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var batchListener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)? = null
    var simulateSendFailure = false
    var simulateCorruptedBatchOnReceive = false
    val sentBatches = mutableListOf<Pair<String, SyncBatch>>()

    override fun startAdvertising(localDeviceName: String) {
        _state.value = TransportState(
            stage = TransportStage.WAITING_APPROVAL,
            discoveredEndpoint = TransportDiscoveredEndpoint("endpoint-fake-1", "هاتف العمل الرئيسي", "123456"),
        )
    }

    override fun startDiscovery() {
        _state.value = TransportState(stage = TransportStage.SEARCHING)
    }

    fun simulateEndpointFound(endpointId: String, name: String, authCode: String = "654321") {
        _state.value = TransportState(
            stage = TransportStage.FOUND_DEVICE,
            discoveredEndpoint = TransportDiscoveredEndpoint(endpointId, name, authCode),
        )
    }

    fun simulateConnectionLoss() {
        _state.value = TransportState(
            stage = TransportStage.FAILED,
            errorMessage = "انقطع الاتصال بالجهاز المجاور",
        )
    }

    override fun stop() {
        _state.value = TransportState(stage = TransportStage.IDLE)
    }

    override fun acceptConnection(endpointId: String) {
        _state.value = TransportState(
            stage = TransportStage.RECEIVING_DATA,
            discoveredEndpoint = _state.value.discoveredEndpoint,
            connectedEndpointId = endpointId,
        )
    }

    override fun rejectConnection(endpointId: String) {
        _state.value = TransportState(stage = TransportStage.FAILED, errorMessage = "رفض المستخدم الاتصال")
    }

    override suspend fun sendBatch(endpointId: String, batch: SyncBatch): Boolean {
        if (simulateSendFailure) {
            _state.value = TransportState(stage = TransportStage.FAILED, errorMessage = "فشل إرسال حزمة البيانات")
            return false
        }
        sentBatches += endpointId to batch
        _state.value = _state.value.copy(stage = TransportStage.SENDING_DATA)
        return true
    }

    suspend fun simulateReceiveBatch(endpointId: String, batch: SyncBatch) {
        val listener = batchListener ?: return
        val received = if (simulateCorruptedBatchOnReceive) {
            batch.copy(
                customers = batch.customers + CustomerEntity(
                    id = "corrupt-id",
                    name = "",
                    phone = null,
                    notes = null,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    createdByDeviceId = "corrupt",
                    syncStatus = SyncStatus.PENDING,
                    isDeleted = false,
                ),
            )
        } else {
            batch
        }
        listener(endpointId, received)
    }

    override fun setBatchReceivedListener(listener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)?) {
        batchListener = listener
    }

    override fun disconnect(endpointId: String) {
        _state.value = TransportState(stage = TransportStage.IDLE)
    }
}
