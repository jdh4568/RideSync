package com.example.ridemate_api_test.feature.group.presentation

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ridemate_api_test.core.designsystem.component.RideMateBackground
import com.example.ridemate_api_test.core.designsystem.component.RidePrimaryButton
import com.example.ridemate_api_test.core.designsystem.component.RideTopBar
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.DisabledGrey
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.group.domain.model.GroupUiModel
import com.example.ridemate_api_test.feature.group.domain.model.MembershipStatus

@Composable
fun GroupListScreen(onBack: () -> Unit) {
    var showCreateDialog by remember { mutableStateOf(false) }
    // TODO(backend): GET /api/v1/groups (Bearer JWT) 로 목록을 불러온다.
    val groups = remember {
        mutableStateListOf(
            GroupUiModel(2, "B그룹", "한강 중심 라이딩", 8, 10, MembershipStatus.NONE),
            GroupUiModel(3, "C그룹", "한강 중심 라이딩", 8, 10, MembershipStatus.PENDING),
            GroupUiModel(4, "한강 모임", "한강 중심 라이딩", 8, 10, MembershipStatus.APPROVED),
            GroupUiModel(5, "D그룹", "한강 아침 라이딩", 6, 10, MembershipStatus.REJECTED),
        )
    }
    // 가입 신청 중인 그룹 id (중복 클릭 방지용).
    var joiningGroupId by remember { mutableStateOf<Long?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }

    fun requestJoin(group: GroupUiModel) {
        if (joiningGroupId != null) return
        joiningGroupId = group.groupId
        joinError = null
        // TODO(backend): POST /api/v1/groups/{groupId}/join (Bearer JWT), body 없음.
        // 성공 200 { status: "PENDING" } — 현재 페이지 유지, 버튼을 신청중으로 전환, 인원 수는 증가하지 않음.
        // 401 UNAUTHORIZED/INVALID_TOKEN → 로그인 화면으로 이동.
        // 404 GROUP_NOT_FOUND → 그룹이 없다는 안내 후 목록 재조회.
        // 409 GROUP_JOIN_CONFLICT → 이미 가입했거나 신청 중이라는 안내 후 목록 재조회.
        val index = groups.indexOfFirst { it.groupId == group.groupId }
        if (index >= 0) {
            groups[index] = group.copy(membershipStatus = MembershipStatus.PENDING)
        }
        joiningGroupId = null
    }

    RideMateBackground {
        Column(Modifier.fillMaxSize()) {
            RideTopBar(title = stringResource(R.string.group_management), onBack = onBack)

            joinError?.let { message ->
                Text(
                    text = message,
                    color = DangerRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.group_empty), color = TextSecondary, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    items(groups) { group ->
                        GroupCard(
                            group = group,
                            isJoining = joiningGroupId == group.groupId,
                            onJoin = { requestJoin(group) },
                        )
                    }
                }
            }

            RidePrimaryButton(
                text = stringResource(R.string.group_create),
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            )
        }

        if (showCreateDialog) {
            CreateGroupDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { groupName, description, maxMembers ->
                    val nextId = (groups.maxOfOrNull { it.groupId } ?: 0) + 1
                    groups.add(
                        GroupUiModel(
                            groupId = nextId,
                            groupName = groupName,
                            description = description,
                            memberCount = 1,
                            maxMembers = maxMembers,
                            membershipStatus = MembershipStatus.APPROVED,
                        ),
                    )
                    showCreateDialog = false
                },
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupUiModel,
    isJoining: Boolean,
    onJoin: () -> Unit,
) {
    RideCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.groupName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(AppIcons.Group, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${group.memberCount} / ${group.maxMembers}",
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(group.description, fontSize = 14.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            StatusButton(
                status = group.membershipStatus,
                isJoining = isJoining,
                onClick = onJoin,
            )
        }
    }
}

@Composable
private fun StatusButton(
    status: MembershipStatus,
    isJoining: Boolean,
    onClick: () -> Unit,
) {
    val (text, enabled, container) = when (status) {
        MembershipStatus.NONE, MembershipStatus.REJECTED ->
            Triple(stringResource(R.string.group_join_request), true, PrimaryBlue)
        MembershipStatus.PENDING ->
            Triple(stringResource(R.string.group_pending), false, DisabledGrey)
        MembershipStatus.APPROVED ->
            Triple(stringResource(R.string.group_joined), false, DisabledGrey)
    }
    val clickable = enabled && !isJoining
    Box(
        modifier = Modifier
            .height(37.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = SurfaceWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
