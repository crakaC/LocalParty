package com.crakac.localparty

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

class ConnectionManager(context: Context) {
    enum class ConnectionState { Connected, Connecting, Disconnected }
    data class EndpointState(val endpointName: String, val connectionState: ConnectionState)

    fun interface Callback {
        fun onInputStreamAvailable(stream: InputStream)
    }

    companion object {
        private val TAG = ConnectionManager::class.java.simpleName
        private val CODE = Build.DEVICE
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    }

    private val inputQueue = PipedInputStream(8 * 1024)
    private val outputQueue = PipedOutputStream(inputQueue)

    private var callback: Callback? = null

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun start() {
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
        }
    }

    fun stopEndpoints() {
        connectionsClient.stopAllEndpoints()
    }

    @WorkerThread
    fun send(data: ByteArray) {
        outputQueue.write(data)
    }

    fun startSending() {
        val sendTo =
            endpointStatus.filter { it.value.connectionState == ConnectionState.Connected }.keys.toList()
        connectionsClient.sendPayload(
            sendTo,
            Payload.fromStream(inputQueue)
        )
        Log.d(TAG, "startSending to $sendTo")
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val packageName = context.packageName
    private val endpointStatus = mutableStateMapOf<String, EndpointState>()
    val endpoints: List<Pair<String, EndpointState>> by derivedStateOf { endpointStatus.toList() }
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val state = EndpointState(
                info.endpointName,
                ConnectionState.Disconnected
            )
            endpointStatus[endpointId] = state
            // If endpoint found, request automatically
            requestConnection(endpointId, state)
            Log.d(TAG, "onEndpointFound($info)")
        }

        override fun onEndpointLost(endpointId: String) {
            endpointStatus.remove(endpointId)
            Log.d(TAG, "onEndpointLost($endpointId)")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "onPayloadReceived()")
            if (payload.type == Payload.Type.STREAM) {
                val stream = payload.asStream()!!.asInputStream()
                callback?.onInputStreamAvailable(stream)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {}
                PayloadTransferUpdate.Status.IN_PROGRESS -> {}
                PayloadTransferUpdate.Status.CANCELED -> {}
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.w(TAG, "PayloadTransferUpdate failure")
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointStatus[endpointId] =
                EndpointState(
                    info.endpointName,
                    ConnectionState.Connecting
                )
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            Log.d(TAG, "onConnectionInitiated $endpointId")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val currentStatus =
                    endpointStatus[endpointId]?.copy(connectionState = ConnectionState.Connected)
                        ?: return
                endpointStatus[endpointId] = currentStatus
            }
            Log.d(TAG, "onConnectionResult $endpointId")
        }

        override fun onDisconnected(endpointId: String) {
            val currentStatus =
                endpointStatus[endpointId]?.copy(connectionState = ConnectionState.Disconnected)
                    ?: return
            endpointStatus[endpointId] = currentStatus
            Log.d(TAG, "onDisconnected $endpointId")
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(CODE, packageName, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(packageName, endpointDiscoveryCallback, options)
    }

    fun requestConnection(endpoint: String, state: EndpointState) {
        connectionsClient.requestConnection(CODE, endpoint, connectionLifecycleCallback)
        endpointStatus[endpoint] = state.copy(
            connectionState = ConnectionState.Connecting
        )
    }

    fun String.toPayload() = Payload.fromBytes(toByteArray(StandardCharsets.UTF_8))
    fun Payload.decodeToString() = String(asBytes() ?: ByteArray(0), StandardCharsets.UTF_8)
}