package com.crakac.localparty

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crakac.localparty.ui.screen.App
import com.crakac.localparty.ui.screen.MainContent
import com.crakac.localparty.ui.screen.PermissionRequestContent

class MainActivity : ComponentActivity() {

    private var isPermissionFullyGranted by mutableStateOf(false)
    private lateinit var connectionManager: ConnectionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isPermissionFullyGranted = result.entries.all { it.value }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionManager = ConnectionManager(this, lifecycle)
        setContent {
            App {
                if (isPermissionFullyGranted) {
                    MainContent(
                        connectionManager.endpoints,
                        onClickEndpoint = { endpoint, state ->
                            connectionManager.requestConnection(endpoint, state)
                        },
                        onClickStart = {
                            startActivity(
                                Intent(this@MainActivity, CaptureActivity::class.java)
                            )
                        },
                    )
                } else {
                    PermissionRequestContent(
                        onClickShowSettings = {
                            showInstalledAppDetails()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (PERMISSIONS.map {
                checkSelfPermission(it)
            }.all { it == PackageManager.PERMISSION_GRANTED }) {
            isPermissionFullyGranted = true
        } else {
            isPermissionFullyGranted = false
            permissionLauncher.launch(PERMISSIONS)
        }
    }

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        private val PERMISSIONS = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.NFC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }.toTypedArray()
    }
}
