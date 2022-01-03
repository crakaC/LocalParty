package com.crakac.localparty.ui.screen

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crakac.localparty.ConnectionManager
import com.crakac.localparty.R
import com.crakac.localparty.ui.theme.LocalPartyTheme
import com.crakac.localparty.ui.theme.Shapes


@Composable
fun App(content: @Composable ColumnScope.() -> Unit) {
    LocalPartyTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Composable
fun MainContent(
    endpointStatuses: List<Pair<String, ConnectionManager.EndpointState>> = emptyList(),
    onClickEndpoint: (endpointId: String, state: ConnectionManager.EndpointState) -> Unit = { _, _ -> },
    onClickStart: () -> Unit = {},
) {
    LazyColumn(
        state = rememberLazyListState()
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
}

@Composable
fun PermissionRequestContent(
    onClickShowSettings: () -> Unit = {}
) {
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
private fun PreviewMainContent() {
    App {
        MainContent(
            endpointStatuses = listOf(
                "dummy" to ConnectionManager.EndpointState(
                    "Dummy",
                    ConnectionManager.ConnectionState.Connected
                )
            )
        )
        Spacer(Modifier.height(24.dp))
        PermissionRequestContent()
    }
}