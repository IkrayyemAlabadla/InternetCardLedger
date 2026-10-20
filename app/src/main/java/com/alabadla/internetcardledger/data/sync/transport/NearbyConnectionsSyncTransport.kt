package com.alabadla.internetcardledger.data.sync.transport

import android.content.Context
import com.alabadla.internetcardledger.data.sync.SyncBatch
import com.alabadla.internetcardledger.data.sync.SyncJsonCodec
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class NearbyConnectionsSyncTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : SyncTransport {

    private val client = Nearby.getConnectionsClient(context)
    private val _state = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private var batchListener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            scope.launch {
                runCatching {
                    val jsonStr = String(bytes, StandardCharsets.UTF_8)
                    val batch = SyncJsonCodec.batchFromJson(jsonStr)
                    batchListener?.invoke(endpointId, batch)
                }.onFailure {
                    _state.value = TransportState(
                        stage = TransportStage.FAILED,
                        errorMessage = "تلقى النظام حزمة بيانات تالفة أو غير صالحة.",
                    )
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            _state.value = TransportState(
                stage = TransportStage.WAITING_APPROVAL,
                discoveredEndpoint = TransportDiscoveredEndpoint(
                    endpointId = endpointId,
                    deviceName = connectionInfo.endpointName,
                    authDigits = connectionInfo.authenticationDigits ?: "------",
                ),
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                _state.value = _state.value.copy(
                    stage = TransportStage.RECEIVING_DATA,
                    connectedEndpointId = endpointId,
                )
            } else {
                _state.value = TransportState(
                    stage = TransportStage.FAILED,
                    errorMessage = "فشلت عملية الاقتران أو تم رفضها.",
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            _state.value = TransportState(
                stage = TransportStage.FAILED,
                errorMessage = "انقطع الاتصال بالجهاز المجاور.",
            )
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _state.value = TransportState(
                stage = TransportStage.FOUND_DEVICE,
                discoveredEndpoint = TransportDiscoveredEndpoint(
                    endpointId = endpointId,
                    deviceName = info.endpointName,
                    authDigits = "------",
                ),
            )
            // Request connection manually
            client.requestConnection(info.endpointName, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            if (_state.value.connectedEndpointId == null) {
                _state.value = TransportState(stage = TransportStage.SEARCHING)
            }
        }
    }

    override fun startAdvertising(localDeviceName: String) {
        _state.value = TransportState(stage = TransportStage.SEARCHING)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(localDeviceName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnFailureListener {
                _state.value = TransportState(
                    stage = TransportStage.FAILED,
                    errorMessage = "تعذر بدء الإعلان عن الجهاز. تحقق من تشغيل البلوتوث.",
                )
            }
    }

    override fun startDiscovery() {
        _state.value = TransportState(stage = TransportStage.SEARCHING)
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnFailureListener {
                _state.value = TransportState(
                    stage = TransportStage.FAILED,
                    errorMessage = "تعذر بدء البحث عن الأجهزة المجاورة.",
                )
            }
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _state.value = TransportState(stage = TransportStage.IDLE)
    }

    override fun acceptConnection(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
    }

    override fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId)
        _state.value = TransportState(
            stage = TransportStage.FAILED,
            errorMessage = "تم رفض الاتصال.",
        )
    }

    override suspend fun sendBatch(endpointId: String, batch: SyncBatch): Boolean {
        return try {
            _state.value = _state.value.copy(stage = TransportStage.SENDING_DATA)
            val jsonStr = SyncJsonCodec.batchToJson(batch)
            val bytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
            true
        } catch (e: Exception) {
            _state.value = TransportState(
                stage = TransportStage.FAILED,
                errorMessage = "تعذر إرسال بيانات المزامنة: ${e.message}",
            )
            false
        }
    }

    override fun setBatchReceivedListener(listener: (suspend (endpointId: String, batch: SyncBatch) -> Unit)?) {
        batchListener = listener
    }

    override fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        _state.value = TransportState(stage = TransportStage.IDLE)
    }

    private companion object {
        const val SERVICE_ID = "com.alabadla.internetcardledger.sync"
    }
}
