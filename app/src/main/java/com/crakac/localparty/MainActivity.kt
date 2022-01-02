package com.crakac.localparty

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.crakac.localparty.ui.theme.App
import com.crakac.localparty.ui.theme.MainContent
import com.crakac.localparty.ui.theme.PermissionRequestContent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var isPermissionFullyGranted by mutableStateOf(false)
    private lateinit var connectionManager: ConnectionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isPermissionFullyGranted = result.entries.all { it.value }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this@MainActivity, "Not permitted", Toast.LENGTH_SHORT)
                .show()
            return@registerForActivityResult
        }
        startRecordingService(result.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        connectionManager = ConnectionManager(this, lifecycle)
        setContent {
            App {
                val scope = rememberCoroutineScope()
                if (isPermissionFullyGranted) {
                    MainContent(
                        connectionManager.endpoints,
                        onClickEndpoint = { endpoint, state ->
                            scope.launch {
                                connectionManager.requestConnection(endpoint, state)
                            }
                        },
                        onClickStart = {
                            scope.launch {
                                projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            }
                        },
                        onClickStop = {
                            scope.launch {
                                stopRecordingService()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Stop recording",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
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

    private fun startRecordingService(data: Intent) {
        val intent = ScreenRecordService.createIntent(this, data)
        startForegroundService(intent)
    }

    private fun stopRecordingService() {
        stopService(Intent(this, ScreenRecordService::class.java))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }.toTypedArray()
    }
}
