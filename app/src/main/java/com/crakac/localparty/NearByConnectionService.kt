package com.crakac.localparty

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class NearByConnectionService : Service() {
    companion object {
        private val TAG = NearByConnectionService::class.java.simpleName
    }
    private val binder = NearByConnectionServiceBinder()
    private lateinit var connectionManager: ConnectionManager

    inner class NearByConnectionServiceBinder : Binder() {
        fun getConnectionManager() = connectionManager
    }

    override fun onCreate() {
        connectionManager = ConnectionManager(this)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }
}