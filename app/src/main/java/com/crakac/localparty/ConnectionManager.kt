package com.crakac.localparty

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class ConnectionManager(context: Context, lifecycle: Lifecycle) :
    DefaultLifecycleObserver {
    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        startAdvertising()
        startDiscovery()
    }

    override fun onStop(owner: LifecycleOwner) {
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        connectionsClient.stopAllEndpoints()
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val packageName = context.packageName
    private val endpointStatus = mutableStateMapOf<String, EndpointState>()
    val endpoints: List<Pair<String, EndpointState>>
        get() = endpointStatus.toList()
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointStatus[endpointId] =
                EndpointState(
                    info.endpointName,
                    ConnectionState.Disconnected
                )
            // If endpoint found, request automatically
            connectionsClient.requestConnection(CODE, packageName, connectionLifecycleCallback)
            Log.d("endpointDiscoveryCallback", "onEndpointFound($info)")
        }

        override fun onEndpointLost(endpointId: String) {
            endpointStatus.remove(endpointId)
            Log.d("endpointDiscoveryCallback", "onEndpointLost()")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("PayloadCallback", "onPayloadReceived()")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {}
                PayloadTransferUpdate.Status.IN_PROGRESS -> {}
                PayloadTransferUpdate.Status.CANCELED -> {}
                PayloadTransferUpdate.Status.FAILURE -> {}
            }
            Log.d("PayloadCallback", "onPayloadTransferUpdate(${update.status})")
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
            Log.d("ConnectionLifecycleCallback", "onConnectionInitiated $endpointId")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val currentStatus =
                    endpointStatus[endpointId]?.copy(connectionState = ConnectionState.Connected)
                        ?: return
                endpointStatus[endpointId] = currentStatus
            }
            Log.d("ConnectionLifecycleCallback", "onConnectionResult $endpointId")
        }

        override fun onDisconnected(endpointId: String) {
            val currentStatus =
                endpointStatus[endpointId]?.copy(connectionState = ConnectionState.Disconnected)
                    ?: return
            endpointStatus[endpointId] = currentStatus
            Log.d("ConnectionLifecycleCallback", "onDisconnected $endpointId")
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            CODE,
            packageName,
            connectionLifecycleCallback,
            options
        )
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(packageName, endpointDiscoveryCallback, options)
    }

    fun requestConnection(endpoint: String, state: EndpointState) {
        connectionsClient.requestConnection(
            Build.DEVICE,
            endpoint,
            connectionLifecycleCallback
        )
        endpointStatus[endpoint] = state.copy(
            connectionState = ConnectionState.Connecting
        )
    }

    companion object {
        private val CODE = Build.DEVICE
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    }

    enum class ConnectionState { Connected, Connecting, Disconnected }
    data class EndpointState(val endpointName: String, val connectionState: ConnectionState)
}