package com.alabadla.internetcardledger.data.sync.transport

import com.alabadla.internetcardledger.data.sync.SyncBatch
import kotlinx.coroutines.flow.StateFlow

enum class TransportStage {
    IDLE,
    SEARCHING,
    FOUND_DEVICE,
    WAITING_APPROVAL,
    SENDING_DATA,
    RECEIVING_DATA,
    SUCCESS,
    FAILED,
}

data class TransportDiscoveredEndpoint(
    val endpointId: String,
    val deviceName: String,
    val authDigits: String,
)

data class TransportState(
    val stage: TransportStage = TransportStage.IDLE,
    val discoveredEndpoint: TransportDiscoveredEndpoint? = null,
    val connectedEndpointId: String? = null,
    val errorMessage: String? = null,
)

interface SyncTransport {
    val state: StateFlow<TransportState>

    fun startAdvertising(localDeviceName: String)
    fun startDiscovery()
    fun stop()

    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)

    suspend fun sendBatch(endpointId: String, batch: SyncBatch): Boolean
    fun setBatchReceivedListener(listener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)?)
    fun disconnect(endpointId: String)
}
