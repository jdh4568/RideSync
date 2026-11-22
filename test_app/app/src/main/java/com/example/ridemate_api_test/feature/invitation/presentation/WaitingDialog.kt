package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppDialog
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.DialogButton
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.AlertYellowBg
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.invitation.domain.model.RideInviteStatusUiModel
import com.example.ridemate_api_test.feature.invitation.domain.model.counts

/**
 * 라이딩 참여 현황(리더 대기) 다이얼로그.
 *
 * - 유효한 참여 현황을 받기 전(`status == null`)에는 시작 버튼을 비활성화한다.
 * - 시작은 서버 `canStart` 가 true 일 때만 가능하다. (수락 인원 2명 이상)
 * - 시작/취소 요청을 보내는 동안([processing]) 두 버튼을 동시에 비활성화한다.
 * - [errorMessage] 가 있으면 다이얼로그 안에 경고 배너로 노출한다.
 *
 * WS(`RIDE_INVITE_STATUS`) 연동 위치: [status] 를 새 목록으로 **교체**해서 넘기면 된다.
 * TODO: `GET /api/v1/ride-invitations/{id}` 대신 `/ws/login` 메시지를 구독해 상태를 갱신한다.
 */
@Composable
fun WaitingDialog(
    status: RideInviteStatusUiModel?,
    selfUserId: Long,
    processing: Boolean,
    errorMessage: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    val counts = status?.counts(selfUserId)
    val startEnabled = status?.canStart == true && !processing
    val cancelEnabled = !processing

    AppDialog(onDismiss = {}) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Bike, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.waiting_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.waiting_subtitle),
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SoftCard)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(stringResource(R.string.stat_invited), counts?.invited)
                StatColumn(stringResource(R.string.stat_accepted), counts?.accepted, highlight = counts != null)
                StatColumn(stringResource(R.string.stat_pending), counts?.pending)
                StatColumn(stringResource(R.string.stat_rejected), counts?.rejected)
            }

            if (counts == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.waiting_status_empty),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.waiting_valid_until),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = errorMessage, onDismiss = onDismissError)
            }

            Spacer(Modifier.height(20.dp))
            DialogButton(
                text = if (processing) {
                    stringResource(R.string.waiting_processing)
                } else {
                    stringResource(R.string.start_ride)
                },
                container = PrimaryBlue,
                contentColor = SurfaceWhite,
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cancel_ride),
                color = if (cancelEnabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = cancelEnabled) { showConfirm = true }
                    .padding(8.dp),
            )
        }
    }

    if (showConfirm) {
        AppDialog(onDismiss = { showConfirm = false }) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.cancel_confirm_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.cancel_confirm_message),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        container = SoftCard,
                        contentColor = TextPrimary,
                        onClick = { showConfirm = false },
                        enabled = !processing,
                        modifier = Modifier.weight(1f),
                    )
                    DialogButton(
                        text = stringResource(R.string.confirm),
                        container = PrimaryBlue,
                        contentColor = SurfaceWhite,
                        onClick = {
                            showConfirm = false
                            onCancel()
                        },
                        enabled = !processing,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int?, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value?.let { "${it}명" } ?: "–",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (highlight && (value ?: 0) > 0) PrimaryBlue else TextPrimary,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AlertYellowBg)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            AppIcons.Close,
            contentDescription = stringResource(R.string.confirm),
            tint = TextSecondary,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(1.dp),
        )
    }
}
