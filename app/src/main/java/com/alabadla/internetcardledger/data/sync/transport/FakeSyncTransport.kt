package com.alabadla.internetcardledger.data.sync.transport

import com.alabadla.internetcardledger.data.sync.SyncBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncTransport : SyncTransport {

    private val _state = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var batchListener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)? = null
    var simulateSendFailure: Boolean = false
    var simulateCorruptedBatchOnReceive: Boolean = false

    val sentBatches = mutableListOf<Pair<String, SyncBatch>>()

    override fun startAdvertising(localDeviceName: String) {
        _state.value = TransportState(
            stage = TransportStage.WAITING_APPROVAL,
            discoveredEndpoint = TransportDiscoveredEndpoint("endpoint-fake-1", "هاتف العم الرئيسي", "123456"),
        )
    }

    override fun startDiscovery() {
        _state.value = TransportState(
            stage = TransportStage.SEARCHING,
        )
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
        val currentDiscovered = _state.value.discoveredEndpoint
        _state.value = TransportState(
            stage = TransportStage.RECEIVING_DATA,
            discoveredEndpoint = currentDiscovered,
            connectedEndpointId = endpointId,
        )
    }

    override fun rejectConnection(endpointId: String) {
        _state.value = TransportState(
            stage = TransportStage.FAILED,
            errorMessage = "تم رفض الاتصال من قبل المستخدم",
        )
    }

    override suspend fun sendBatch(endpointId: String, batch: SyncBatch): Boolean {
        if (simulateSendFailure) {
            _state.value = TransportState(
                stage = TransportStage.FAILED,
                errorMessage = "فشل إرسال حزمة البيانات",
            )
            return false
        }
        sentBatches.add(endpointId to batch)
        _state.value = _state.value.copy(stage = TransportStage.SENDING_DATA)
        return true
    }

    suspend fun simulateReceiveBatch(endpointId: String, batch: SyncBatch) {
        val listener = batchListener ?: return
        if (simulateCorruptedBatchOnReceive) {
            val corruptBatch = batch.copy(
                customers = batch.customers + com.alabadla.internetcardledger.data.local.entity.CustomerEntity(
                    id = "corrupt-id",
                    name = "", // Corrupted blank name
                    phone = null,
                    notes = null,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    createdByDeviceId = "corrupt",
                    syncStatus = com.alabadla.internetcardledger.data.local.SyncStatus.PENDING,
                    isDeleted = false,
                ),
            )
            listener(endpointId, corruptBatch)
        } else {
            listener(endpointId, batch)
        }
    }

    override fun setBatchReceivedListener(listener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)?) {
        batchListener = listener
    }

    override fun disconnect(endpointId: String) {
        _state.value = TransportState(stage = TransportStage.IDLE)
    }
}
