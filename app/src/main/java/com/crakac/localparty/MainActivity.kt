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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crakac.localparty.ui.theme.LocalPartyTheme
import com.crakac.localparty.ui.theme.Shapes
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
                MainContent(isPermissionFullyGranted,
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
                            Toast.makeText(this@MainActivity, "Stop recording", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    onClickShowSettings = {
                        scope.launch {
                            showInstalledAppDetails()
                        }
                    }
                )
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


@Composable
private fun App(content: @Composable ColumnScope.() -> Unit) {
    LocalPartyTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ColumnScope.MainContent(
    isPermissionGranted: Boolean = false,
    endpointStatuses: List<Pair<String, ConnectionManager.EndpointState>> = emptyList(),
    onClickEndpoint: (endpointId: String, state: ConnectionManager.EndpointState) -> Unit = { _, _ -> },
    onClickStart: () -> Unit = {},
    onClickStop: () -> Unit = {},
    onClickShowSettings: () -> Unit = {}
) {
    if (isPermissionGranted) {
        val scrollState = rememberLazyListState()
        LazyColumn(
            state = scrollState
        ) {
            items(endpointStatuses) { item ->
                val (endpoint, state) = item
                Row(
                    modifier = Modifier
                        .clickable { onClickEndpoint(endpoint, state) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(Color.Transparent, Shapes.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.endpointName,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    ConnectionIcon(state.connectionState)
                }
                Spacer(Modifier.size(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.width(120.dp),
            onClick = onClickStart
        ) {
            Text("Start")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.width(120.dp),
            onClick = onClickStop
        ) {
            Text("Stop")
        }
    } else {
        Text(
            "This app needs to access fine location to search other devices.",
        )
        Button(
            modifier = Modifier.wrapContentSize(),
            onClick = onClickShowSettings
        ) {
            Text("Open settings")
        }
    }
}

@Composable
private fun ConnectionIcon(connectionState: ConnectionManager.ConnectionState) {
    if (connectionState == ConnectionManager.ConnectionState.Connected) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.ic_signal_wifi),
            contentDescription = "connected"
        )
    } else {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.ic_signal_wifi_null),
            contentDescription = "disconnected"
        )
    }
}

@Preview(
    heightDp = 320
)
@Composable
fun PreviewMainContent() {
    App {
        MainContent(
            isPermissionGranted = true,
            endpointStatuses = listOf(
                "dummy" to ConnectionManager.EndpointState(
                    "Dummy",
                    ConnectionManager.ConnectionState.Connected
                )
            )
        )
        Spacer(Modifier.height(24.dp))
        MainContent(isPermissionGranted = false)
    }
}