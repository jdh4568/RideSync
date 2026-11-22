package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.invitation.domain.model.RideInviteUiModel

/**
 * 수락 후 리더가 라이딩을 시작하기까지 기다리는 상태.
 * RIDE_STARTED 수신 시 정리하고 팔로워 라이딩 화면으로 이동하며,
 * RIDE_INVITE_CANCELLED / ERROR 수신 시 해당 초대만 정리한다.
 */
@Composable
fun FollowerWaitingDialog(invite: RideInviteUiModel) {
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
                text = stringResource(R.string.follower_waiting_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.follower_waiting_message, invite.leaderNickname),
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(SoftCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(stringResource(R.string.invite_group), invite.groupName)
                InfoRow(stringResource(R.string.invite_leader), invite.leaderNickname)
            }
        }
    }
}
