package com.example.ridemate_api_test.feature.ride.presentation.follower

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.AlertYellowBg
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.SignalYellow
import com.example.ridemate_api_test.core.designsystem.theme.SkyBackground
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.ride.presentation.common.RideHeader
import com.example.ridemate_api_test.feature.ride.presentation.common.RideRole
import com.example.ridemate_api_test.feature.ride.presentation.common.RideSignalType
import com.example.ridemate_api_test.feature.ride.presentation.common.RideStatsCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RecentSignal(val type: RideSignalType, val time: String)

private val signalTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

@Composable
fun FollowerRideScreen(onFinish: () -> Unit) {
    var voiceOn by remember { mutableStateOf(true) }
    var vibrationOn by remember { mutableStateOf(false) }
    // TODO(ws): RIDE_STARTED 의 rideSessionId 로 GET /api/v1/rides/{id}, ws://…/ws/ride/{id} 연결.
    // TODO(ws): RIDE_FINISHED 수신 시 현재 세션인지 확인 → 측정·깜빡임·음성·진동 정지 →
    //  기록 저장 후 onFinish() 로 홈 이동 및 백스택 정리. 저장 실패 시에도 홈 이동, 값은 재시도 보존.
    //  동일 rideSessionId 에 대해 종료 처리는 1회만.
    var connectionError by remember { mutableStateOf<String?>(null) }

    // 최근 신호는 앱 메모리에만 보관, 신규 승차 시 이전 목록을 비운다. 서버 시간이 없어 앱 수신 시각을 기록한다.
    val recentSignals = remember {
        mutableStateListOf(
            RecentSignal(RideSignalType.ROAD_HAZARD, "09:32:10"),
            RecentSignal(RideSignalType.STOP, "09:28:44"),
            RecentSignal(RideSignalType.SLOW, "09:26:03"),
            RecentSignal(RideSignalType.CURVE_CAUTION, "09:25:19"),
            RecentSignal(RideSignalType.ROAD_HAZARD, "09:20:57"),
        )
    }
    var alertSignal by remember { mutableStateOf<RideSignalType?>(RideSignalType.ROAD_HAZARD) }
    var alertTime by remember { mutableStateOf("09:32:10") }
    var blinkYellow by remember { mutableStateOf(true) }

    // 신호 수신 시 회색 ↔ 노란색 0.5초 간격으로 3회 왕복 깜빡인 뒤 노란색 유지.
    // 깜빡이는 중 새 신호가 오면 최신 신호로 교체하고 효과를 처음부터 재생한다.
    LaunchedEffect(alertSignal) {
        if (alertSignal == null) return@LaunchedEffect
        blinkYellow = true
        repeat(3) {
            delay(500)
            blinkYellow = false
            delay(500)
            blinkYellow = true
        }
    }

    fun onSignalReceived(type: RideSignalType) {
        // TODO(ws /ws/ride): RIDE_SIGNAL 수신. 후미는 신호를 전송하지 않는다.
        val now = signalTimeFormat.format(Date())
        alertSignal = type
        alertTime = now
        recentSignals.add(0, RecentSignal(type, now))
        // voiceOn 이면 신호명을 2회 안내, vibrationOn 이면 2초 간격으로 진동. 둘 다 꺼도 박스와 깜빡임은 유지.
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyBackground)
            .padding(bottom = 20.dp),
    ) {
        RideHeader(role = RideRole.FOLLOWER, groupName = "한강 모임", memberCount = 5)

        connectionError?.let { message ->
            Text(
                text = message,
                color = DangerRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        AlertCard(
            signal = alertSignal,
            time = alertTime,
            blinkYellow = blinkYellow,
        )

        Spacer(Modifier.height(16.dp))
        RecentSignalCard(recentSignals)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            ToggleCard(
                modifier = Modifier.weight(1f),
                icon = AppIcons.Volume,
                label = stringResource(R.string.voice_guide),
                enabled = voiceOn,
                onToggle = { voiceOn = !voiceOn },
            )
            ToggleCard(
                modifier = Modifier.weight(1f),
                icon = AppIcons.Vibration,
                label = stringResource(R.string.vibration),
                enabled = vibrationOn,
                onToggle = { vibrationOn = !vibrationOn },
            )
        }

        Spacer(Modifier.height(16.dp))
        // TODO(gps): 현재 속도·누적 거리는 기기 GPS 로 계산. 평균 속도는 종료 시 별도 계산.
        RideStatsCard(
            speed = "15",
            distance = "6.3",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun AlertCard(
    signal: RideSignalType?,
    time: String,
    blinkYellow: Boolean,
) {
    RideCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (blinkYellow) AlertYellowBg else SoftCard)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                signal?.let { SignalIcon(it) }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = signal?.let { stringResource(it.labelRes) } ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
            }
            Text(
                time,
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun RecentSignalCard(signals: List<RecentSignal>) {
    RideCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.recent_signal),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            signals.forEachIndexed { index, signal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(signal.type.labelRes), fontSize = 15.sp, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(signal.time, fontSize = 14.sp, color = TextSecondary)
                }
                if (index != signals.lastIndex) HorizontalDivider(color = LightBorder)
            }
        }
    }
}

@Composable
private fun ToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (enabled) AccentBlue else TextSecondary
    RideCard(
        modifier = modifier
            .clickable(onClick = onToggle),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Text(label, fontSize = 14.sp, color = TextPrimary)
            Text(
                text = stringResource(if (enabled) R.string.on else R.string.off),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = content,
            )
        }
    }
}

@Composable
private fun SignalIcon(type: RideSignalType) {
    when (type) {
        RideSignalType.ROAD_HAZARD -> Icon(AppIcons.Warning, contentDescription = null, tint = SignalYellow, modifier = Modifier.size(36.dp))
        RideSignalType.CURVE_CAUTION -> Icon(AppIcons.Route, contentDescription = null, tint = SignalYellow, modifier = Modifier.size(36.dp))
        RideSignalType.LEFT -> Icon(AppIcons.ArrowLeft, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(36.dp))
        RideSignalType.RIGHT -> Icon(AppIcons.ArrowRight, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(36.dp))
        RideSignalType.SINGLE_FILE -> Icon(AppIcons.ArrowUp, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(36.dp))
        RideSignalType.DOUBLE_FILE -> Icon(AppIcons.DoubleArrowUp, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(36.dp))
        RideSignalType.SLOW -> Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("10", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
        }
        RideSignalType.STOP -> Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DangerRed),
            contentAlignment = Alignment.Center,
        ) {
            Text("STOP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
        }
        RideSignalType.LEADER_CHANGE -> Icon(AppIcons.Crown, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(36.dp))
    }
}
