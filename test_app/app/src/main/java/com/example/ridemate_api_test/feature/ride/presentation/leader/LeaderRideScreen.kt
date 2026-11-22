package com.example.ridemate_api_test.feature.ride.presentation.leader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.DisabledGrey
import com.example.ridemate_api_test.core.designsystem.theme.SignalYellow
import com.example.ridemate_api_test.core.designsystem.theme.SkyBackground
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.feature.ride.presentation.common.RideHeader
import com.example.ridemate_api_test.feature.ride.presentation.common.RideRole
import com.example.ridemate_api_test.feature.ride.presentation.common.RideStatsCard

private enum class SignalSection(val labelRes: Int, val accent: Color) {
    WARNING(R.string.section_warning, SignalYellow),
    DIRECTION(R.string.section_direction, AccentBlue),
    COLUMNS(R.string.section_columns, Color(0xFF2ECC71)),
}

private enum class RideSignal(
    val labelRes: Int,
    val section: SignalSection,
    val signalValue: String,
) {
    ROAD_HAZARD(R.string.signal_hazard, SignalSection.WARNING, "ROAD_HAZARD"),
    CURVE_CAUTION(R.string.signal_curve, SignalSection.WARNING, "CURVE_CAUTION"),
    LEFT(R.string.signal_left, SignalSection.DIRECTION, "LEFT"),
    RIGHT(R.string.signal_right, SignalSection.DIRECTION, "RIGHT"),
    SINGLE_FILE(R.string.signal_single, SignalSection.COLUMNS, "SINGLE_FILE"),
    DOUBLE_FILE(R.string.signal_double, SignalSection.COLUMNS, "DOUBLE_FILE"),
    SLOW(R.string.signal_slow, SignalSection.COLUMNS, "SLOW"),
    STOP(R.string.signal_stop, SignalSection.COLUMNS, "STOP"),
}

@Composable
fun LeaderRideScreen(onFinish: () -> Unit) {
    // TODO(backend): GET /api/v1/rides/{rideSessionId} → groupName, participantCount.
    // TODO(ws): ws://서버/ws/ride/{rideSessionId} 연결(Authorization Bearer), 기존 /ws/login 유지.
    var finishing by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }

    fun sendSignal(signal: RideSignal) {
        // TODO(ws /ws/ride): {"type":"RIDE_SIGNAL","signal":signal.signalValue} 전송 (HTTP POST 아님).
        // 메시지에 사용자 ID/세션 ID 미포함. 서버가 같은 라이딩 후미에게 전달하며, 선두에게 성공 응답을 보내지 않는다.
    }

    fun finishRide() {
        if (finishing) return // 중복 클릭 방지. REST 응답과 RIDE_FINISHED 중 어느 것을 먼저 받아도 종료는 1회만.
        finishing = true
        finishError = null
        // TODO(rest): POST /api/v1/rides/{rideSessionId}/finish (body 없음, 선두만 호출 가능).
        // TODO(rest): PUT /api/v1/rides/{rideSessionId}/record {distance, averageSpeed}
        //  - distance·averageSpeed ≥0, 정수부 최대 8자·소수부 최대 2자. 저장 실패 시 계산 결과를 유지하고 재시도(중복 저장 아님).
        // RIDE_FINISHED 수신 시 신호 전송과 측정을 중단하고 동일하게 1회만 종료 처리한다.
        onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyBackground)
            .padding(bottom = 20.dp),
    ) {
        RideHeader(role = RideRole.LEADER, groupName = "한강 모임", memberCount = 5)

        RideCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SignalSection(
                    section = SignalSection.WARNING,
                    signals = listOf(RideSignal.ROAD_HAZARD, RideSignal.CURVE_CAUTION),
                    onSignal = ::sendSignal,
                )
                SignalSection(
                    section = SignalSection.DIRECTION,
                    signals = listOf(RideSignal.LEFT, RideSignal.RIGHT),
                    onSignal = ::sendSignal,
                )
                SignalSection(
                    section = SignalSection.COLUMNS,
                    signals = listOf(
                        RideSignal.SINGLE_FILE,
                        RideSignal.DOUBLE_FILE,
                        RideSignal.SLOW,
                        RideSignal.STOP,
                    ),
                    onSignal = ::sendSignal,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // TODO(gps): 현재 속도·누적 거리는 서버 API가 아닌 기기 GPS로 계산. 평균 속도는 종료 시 별도 계산.
        RideStatsCard(
            speed = "15",
            distance = "6.3",
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        finishError?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = DangerRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(59.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (finishing) DisabledGrey else DangerRed)
                .clickable(enabled = !finishing, onClick = ::finishRide),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Stop, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (finishing) stringResource(R.string.ride_finishing) else stringResource(R.string.finish_ride),
                color = SurfaceWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SignalSection(
    section: SignalSection,
    signals: List<RideSignal>,
    onSignal: (RideSignal) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(section.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(section.labelRes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
        }
        signals.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { signal ->
                    SignalCard(
                        signal = signal,
                        onSignal = { onSignal(signal) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignalCard(
    signal: RideSignal,
    onSignal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) SignalYellow.copy(alpha = 0.45f) else SurfaceWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSignal,
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SignalIcon(signal)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(signal.labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun SignalIcon(signal: RideSignal) {
    when (signal) {
        RideSignal.ROAD_HAZARD -> Icon(AppIcons.Warning, contentDescription = null, tint = SignalYellow, modifier = Modifier.size(32.dp))
        RideSignal.CURVE_CAUTION -> Icon(AppIcons.Route, contentDescription = null, tint = SignalYellow, modifier = Modifier.size(32.dp))
        RideSignal.LEFT -> Icon(AppIcons.ArrowLeft, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        RideSignal.RIGHT -> Icon(AppIcons.ArrowRight, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        RideSignal.SINGLE_FILE -> Icon(AppIcons.ArrowUp, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        RideSignal.DOUBLE_FILE -> Icon(AppIcons.DoubleArrowUp, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
        RideSignal.SLOW -> Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("10", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
        }
        RideSignal.STOP -> Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DangerRed),
            contentAlignment = Alignment.Center,
        ) {
            Text("STOP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
        }
    }
}
