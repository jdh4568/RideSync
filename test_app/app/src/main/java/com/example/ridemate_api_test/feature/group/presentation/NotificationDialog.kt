package com.example.ridemate_api_test.feature.group.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppDialog
import com.example.ridemate_api_test.core.designsystem.component.DialogButton
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SoftCard
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.group.domain.model.JoinRequestUiModel

@Composable
fun NotificationDialog(onDismiss: () -> Unit) {
    // TODO(backend): GET /api/v1/groups/join-requests (Bearer JWT) 로 목록을 불러온다.
    val requests = remember {
        mutableStateListOf(
            JoinRequestUiModel(15, 1, "A그룹", 5, "홍길동"),
            JoinRequestUiModel(18, 1, "A그룹", 8, "이순신"),
        )
    }
    // 403 FORBIDDEN: 그룹을 소유하지 않은 사용자.
    var forbidden by remember { mutableStateOf(false) }
    // 404 / 409 / 500 등 실패 메시지. GROUP_FULL 인 경우 신청은 목록에 유지된다.
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.notification_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = LightBorder)

            when {
                forbidden -> ForbiddenState(onDismiss = onDismiss)
                requests.isEmpty() -> EmptyState()
                else -> RequestList(
                    requests = requests,
                    onAccept = { request ->
                        // TODO(backend): POST /api/v1/groups/{groupId}/requests/{userId}/approve
                        requests.removeAll { it.groupMemberId == request.groupMemberId }
                        errorMessage = null
                    },
                    onReject = { request ->
                        // TODO(backend): POST /api/v1/groups/{groupId}/requests/{userId}/reject
                        requests.removeAll { it.groupMemberId == request.groupMemberId }
                        errorMessage = null
                    },
                )
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = DangerRed,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RequestList(
    requests: List<JoinRequestUiModel>,
    onAccept: (JoinRequestUiModel) -> Unit,
    onReject: (JoinRequestUiModel) -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        requests.forEach { request ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(
                        R.string.notification_request_format,
                        request.nickname,
                        request.groupName,
                    ),
                    fontSize = 15.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogButton(
                        text = stringResource(R.string.reject),
                        container = SoftCard,
                        contentColor = TextPrimary,
                        onClick = { onReject(request) },
                    )
                    Spacer(Modifier.width(8.dp))
                    DialogButton(
                        text = stringResource(R.string.accept),
                        container = PrimaryBlue,
                        contentColor = SurfaceWhite,
                        onClick = { onAccept(request) },
                    )
                }
            }
            HorizontalDivider(color = LightBorder)
        }
    }
}

@Composable
private fun EmptyState() {
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(R.string.notification_empty),
        color = TextSecondary,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun ForbiddenState(onDismiss: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(R.string.notification_forbidden),
        color = TextSecondary,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        DialogButton(
            text = stringResource(R.string.confirm),
            container = PrimaryBlue,
            contentColor = SurfaceWhite,
            onClick = onDismiss,
        )
    }
    Spacer(Modifier.height(16.dp))
}
