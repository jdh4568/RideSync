package com.example.ridemate_api_test.feature.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeDesign
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.core.designsystem.theme.LoginDesign
import com.example.ridemate_api_test.feature.auth.domain.model.AuthProblem
import com.example.ridemate_api_test.feature.auth.presentation.login.messageResource

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNotice: (HomeNotice) -> Unit,
    onDismissNotice: () -> Unit,
    onRetryConnection: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.login_landscape), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        // This is the sole system-inset owner. All content, including logout, scrolls together.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val fontScale = LocalDensity.current.fontScale
            val stack = maxWidth < 350.dp || fontScale > 1.3f
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 510.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNotice(HomeNotice.NOTIFICATIONS) }) {
                        Image(painterResource(R.drawable.home_notification),
                            stringResource(R.string.home_notifications), Modifier.size(30.dp))
                    }
                }
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.Black)) { append(stringResource(R.string.login_brand_ride)) }
                    append('\u200B')
                    withStyle(SpanStyle(color = HomeDesign.Brand)) { append(stringResource(R.string.login_brand_mate)) }
                }, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 36.dp).semantics { heading() },
                    style = LoginDesign.Logo, textAlign = TextAlign.Center)
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(true, Modifier.fillMaxWidth()) { onNotice(HomeNotice.START_RIDE) }
                        ActionCard(false, Modifier.fillMaxWidth()) { onNotice(HomeNotice.GROUPS) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionCard(true, Modifier.weight(1f)) { onNotice(HomeNotice.START_RIDE) }
                        ActionCard(false, Modifier.weight(1f)) { onNotice(HomeNotice.GROUPS) }
                    }
                }
                Spacer(Modifier.height(34.dp))
                StatisticsPanel(state.statistics, stack)
                Spacer(Modifier.height(16.dp))
                RecentRidesPanel(state.recentRides, stack)
                Text(stringResource(R.string.home_sample_data), Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = Color.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
                SessionControls(state.connectionStatus, state.connectionProblem, onRetryConnection, onLogout)
            }
        }
    }
    state.notice?.takeUnless { it == HomeNotice.NOTIFICATIONS }?.let { notice ->
        val title = when (notice) {
            HomeNotice.NOTIFICATIONS -> R.string.home_notifications
            HomeNotice.START_RIDE -> R.string.home_start_ride
            HomeNotice.GROUPS -> R.string.home_groups
        }
        AlertDialog(onDismissRequest = onDismissNotice,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(R.string.home_preparing)) },
            confirmButton = { TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.home_ok)) } })
    }
}

@Composable
private fun ActionCard(isRide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val background = if (isRide) Brush.linearGradient(listOf(HomeDesign.GradientStart, HomeDesign.GradientEnd))
        else Brush.linearGradient(listOf(Color.White, Color.White))
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = Color.Transparent, border = BorderStroke(1.dp, HomeDesign.Outline), shadowElevation = 6.dp) {
        Column(Modifier.background(background).heightIn(min = 123.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(if (isRide) R.drawable.home_bicycle else R.drawable.home_users), null,
                    if (isRide) Modifier.size(38.dp, 26.5.dp) else Modifier.size(30.dp))
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(if (isRide) R.drawable.home_ride_circle else R.drawable.home_group_circle), null,
                        Modifier.matchParentSize())
                    Image(painterResource(if (isRide) R.drawable.home_ride_arrow else R.drawable.home_group_arrow), null,
                        Modifier.size(6.6.dp, 12.7.dp))
                }
            }
            Text(stringResource(if (isRide) R.string.home_start_ride else R.string.home_groups),
                color = if (isRide) Color.White else Color.Black, fontSize = 20.sp,
                lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White,
        border = BorderStroke(1.dp, HomeDesign.Outline), shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
    }
}

@Composable
private fun StatisticsPanel(statistics: HomeStatistics, stack: Boolean) {
    val entries = listOf(
        stringResource(R.string.home_total_distance) to stringResource(R.string.home_distance, statistics.distance),
        stringResource(R.string.home_average_speed) to stringResource(R.string.home_speed, statistics.averageSpeed),
        stringResource(R.string.home_total_rides) to stringResource(R.string.home_count, statistics.rideCount),
        stringResource(R.string.home_last_ride) to statistics.lastRideDate,
    )
    Panel {
        Text(stringResource(R.string.home_statistics), Modifier.semantics { heading() },
            fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.chunked(if (stack) 1 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) -> StatisticTile(label, value, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatisticTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = HomeDesign.Tile,
        border = BorderStroke(0.5.dp, HomeDesign.Outline)) {
        Column(Modifier.heightIn(min = 72.dp).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = HomeDesign.SecondaryText, fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.Black, fontSize = 17.sp, lineHeight = 23.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RecentRidesPanel(rides: List<HomeRecentRide>, stack: Boolean) {
    Panel {
        Text(stringResource(R.string.home_recent_rides), Modifier.fillMaxWidth().semantics { heading() },
            fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
            color = Color.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        if (rides.isEmpty()) Text(stringResource(R.string.home_no_recent_rides), color = HomeDesign.SecondaryText)
        rides.forEach { ride ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                if (stack) {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ride.groupName, fontSize = 18.sp, color = Color.Black)
                        Text(ride.date, fontSize = 12.sp, color = HomeDesign.SecondaryText)
                        Text(stringResource(R.string.home_distance, ride.distance), fontSize = 14.sp, color = Color.Black)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ride.groupName, Modifier.weight(1f), fontSize = 18.sp, color = Color.Black)
                        Text(ride.date, fontSize = 12.sp, color = HomeDesign.SecondaryText)
                        Text(stringResource(R.string.home_distance, ride.distance), Modifier.weight(1f),
                            fontSize = 14.sp, color = Color.Black, textAlign = TextAlign.End)
                    }
                }
                Image(painterResource(R.drawable.home_divider), null,
                    Modifier.fillMaxWidth().height(0.5.dp), contentScale = ContentScale.FillBounds)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SessionControls(
    status: HomeConnectionStatus?, problem: AuthProblem?, onRetry: () -> Unit, onLogout: () -> Unit,
) {
    status?.let {
        val message = when (it) {
            HomeConnectionStatus.CONNECTED -> R.string.home_connected
            HomeConnectionStatus.RECONNECTING -> R.string.home_reconnecting
            HomeConnectionStatus.DISCONNECTED -> R.string.home_disconnected
        }
        Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(message))
                problem?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
                if (it == HomeConnectionStatus.RECONNECTING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (it == HomeConnectionStatus.DISCONNECTED) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.auth_retry)) }
                }
            }
        }
    }
    // The app-owned session determines availability; a Preview does not perform logout.
    TextButton(onClick = onLogout, enabled = status != null,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(R.string.auth_logout))
    }
}

@Preview(name = "Home", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    // Sample connection state only; no repository, login, or WebSocket in Preview.
    var state by remember { mutableStateOf(HomeUiState(connectionStatus = HomeConnectionStatus.CONNECTED)) }
    HomeTheme {
        HomeScreen(state, { state = state.copy(notice = it) },
            { state = state.copy(notice = null) }, {}, {})
    }
}
