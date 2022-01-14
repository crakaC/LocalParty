package com.crakac.localparty.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    Card(
        Modifier
            .animateContentSize()
            .widthIn(min = 64.dp, max = 240.dp),
        border = BorderStroke(Dp.Hairline, SolidColor(Color.Gray))
    ) {
        if (endpointStatuses.isEmpty()) {
            CircularProgressIndicator(
                Modifier
                    .size(64.dp)
                    .padding(12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.wrapContentWidth(),
                state = rememberLazyListState(),
            ) {
                itemsIndexed(endpointStatuses) { index, item ->
                    val (endpoint, state) = item
                    if (index > 0) {
                        Spacer(Modifier.size(8.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clickable { onClickEndpoint(endpoint, state) }
                            .padding(8.dp)
                            .background(Color.Transparent, Shapes.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.endpointName,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        ConnectionIcon(connectionState = state.connectionState)
                    }
                }
            }
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
private fun ConnectionIcon(
    modifier: Modifier = Modifier,
    connectionState: ConnectionManager.ConnectionState
) {
    when (connectionState) {
        ConnectionManager.ConnectionState.Connected -> {
            Icon(
                modifier = modifier
                    .size(24.dp),
                painter = painterResource(id = R.drawable.ic_signal_wifi),
                contentDescription = "connected"
            )
        }
        ConnectionManager.ConnectionState.Connecting -> {
            val transition = rememberInfiniteTransition()
            val alpha by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Icon(
                modifier = modifier
                    .size(24.dp)
                    .alpha(alpha),
                painter = painterResource(id = R.drawable.ic_signal_wifi_null),
                contentDescription = "connected",
            )
        }
        ConnectionManager.ConnectionState.Disconnected -> {
            Icon(
                modifier = modifier
                    .size(24.dp)
                    .alpha(ContentAlpha.disabled),
                painter = painterResource(id = R.drawable.ic_signal_wifi_null),
                contentDescription = "disconnected"
            )
        }
    }
}

@Preview(
    heightDp = 360
)
@Composable
private fun PreviewMainContent() {
    App {
        MainContent(
            endpointStatuses = listOf(
                "connected" to ConnectionManager.EndpointState(
                    "Connected",
                    ConnectionManager.ConnectionState.Connected
                ),
                "connecting" to ConnectionManager.EndpointState(
                    "Connecting",
                    ConnectionManager.ConnectionState.Connecting
                ),
                "disconnected" to ConnectionManager.EndpointState(
                    "Disconnected",
                    ConnectionManager.ConnectionState.Disconnected
                )
            )
        )
        Spacer(Modifier.height(24.dp))
        PermissionRequestContent()
    }
}