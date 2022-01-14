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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /**
         * If application goes to background and this service is killed, no need to restart.
         */
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
    }
}