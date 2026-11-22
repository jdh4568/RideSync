package com.example.ridemate_api_test.feature.home.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.BrandLogo
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.component.RideMateBackground
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.group.presentation.NotificationDialog
import com.example.ridemate_api_test.feature.invitation.domain.model.RideInviteUiModel
import com.example.ridemate_api_test.feature.invitation.presentation.FollowerWaitingDialog
import com.example.ridemate_api_test.feature.invitation.presentation.RideInviteDialog

@Composable
fun HomeScreen(
    onOpenGroupList: () -> Unit,
    onStartRide: () -> Unit,
    onAcceptInvite: () -> Unit,
) {
    var showNotifications by remember { mutableStateOf(false) }
    // TODO(ws): /ws/login 의 RIDE_INVITE 메시지를 받아 동일한 invitationId 중복 없이 표시한다.
    var invite by remember { mutableStateOf<RideInviteUiModel?>(null) }
    // 수락 후 RIDE_STARTED 를 기다리는 상태.
    var waitingInvite by remember { mutableStateOf<RideInviteUiModel?>(null) }
    var replyInProgress by remember { mutableStateOf(false) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }

    fun replyInvite(accepted: Boolean) {
        val current = invite ?: return
        if (replyInProgress) return
        replyInProgress = true
        // TODO(ws /ws/login): {"type":"RIDE_INVITE_RESPONSE","invitationId":current.invitationId,"accepted":accepted}
        // accepted 는 boolean true/false. 전송 완료가 서버 확인을 의미하지 않으며,
        // 이후 ERROR / RIDE_STARTED / RIDE_INVITE_CANCELLED 를 계속 수신한다.
        replyInProgress = false
        invite = null
        if (accepted) waitingInvite = current
    }

    RideMateBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { showNotifications = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Bell, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DangerRed),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Person, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(24.dp))
                }
            }

            noticeMessage?.let { message ->
                Text(
                    text = message,
                    color = DangerRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            BrandLogo(fontSize = 40)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                item { ActionCards(onStartRide = onStartRide, onOpenGroupList = onOpenGroupList) }
                item { RecordCard() }
                item { RecentRidesCard() }
            }
        }

        if (showNotifications) {
            NotificationDialog(onDismiss = { showNotifications = false })
        }
        invite?.let { current ->
            RideInviteDialog(
                invite = current,
                isReplying = replyInProgress,
                onAccept = { replyInvite(true) },
                onReject = { replyInvite(false) },
                onDismiss = { invite = null },
            )
        }
        waitingInvite?.let { current ->
            // TODO(ws): RIDE_STARTED 수신 시 rideSessionId 저장 후 대기 UI 를 정리하고 onAcceptInvite() 로 이동.
            FollowerWaitingDialog(current)
        }
    }
}

@Composable
private fun ActionCards(onStartRide: () -> Unit, onOpenGroupList: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(130.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(AccentBlue, PrimaryBlue)),
                )
                .clickable(onClick = onStartRide)
                .padding(16.dp),
        ) {
            Icon(AppIcons.Bike, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.home_start_ride),
                color = SurfaceWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
            }
        }

        RideCard(
            modifier = Modifier
                .weight(1f)
                .height(130.dp)
                .clickable(onClick = onOpenGroupList),
        ) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Icon(AppIcons.Group, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(R.string.home_group_manage),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SoftCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordCard() {
    RideCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_record),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_total_distance),
                    value = "50.2km",
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_avg_speed),
                    value = "28.5km/h",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_ride_count),
                    value = "23회",
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.home_last_date),
                    value = "2026.09.20",
                )
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SoftCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private data class RecentRide(val group: String, val date: String, val distance: String)

@Composable
private fun RecentRidesCard() {
    val rides = listOf(
        RecentRide("A그룹", "2026.09.20", "24.3km"),
        RecentRide("B그룹", "2026.09.19", "40.1km"),
        RecentRide("C그룹", "2026.09.18", "25.5km"),
    )
    RideCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_recent),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            rides.forEachIndexed { index, ride ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ride.group, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(ride.date, fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.width(24.dp))
                    Text(ride.distance, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                if (index != rides.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(color = LightBorder)
                }
            }
        }
    }
}
