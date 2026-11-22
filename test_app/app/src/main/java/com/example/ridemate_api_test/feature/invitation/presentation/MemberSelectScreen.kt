package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ridemate_api_test.core.designsystem.component.DialogButton
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.component.RideMateBackground
import com.example.ridemate_api_test.core.designsystem.component.RidePrimaryButton
import com.example.ridemate_api_test.core.designsystem.component.RideTopBar
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.OfflineGrey
import com.example.ridemate_api_test.core.designsystem.theme.OnlineGreen
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.group.domain.model.GroupMemberRole
import com.example.ridemate_api_test.feature.group.domain.model.GroupMemberUiModel
import com.example.ridemate_api_test.feature.group.domain.model.GroupMembersUiModel
import com.example.ridemate_api_test.feature.invitation.domain.model.InviteParticipant
import com.example.ridemate_api_test.feature.invitation.domain.model.ParticipantStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface MembersUiState {
    data object Loading : MembersUiState
    data class Loaded(val group: GroupMembersUiModel) : MembersUiState
    data class Failed(val messageRes: Int, val navigateLogin: Boolean) : MembersUiState
}

@Composable
fun MemberSelectScreen(
    groupId: Long,
    selfUserId: Long,
    onBack: () -> Unit,
    onInvite: (List<InviteParticipant>) -> Unit,
    onNavigateLogin: () -> Unit,
    onOpenGroupSelect: () -> Unit,
) {
    // TODO: GET /api/v1/groups/{groupId}/members (body 없음).
    //  - 응답은 객체이며 members 배열에서 목록을 읽는다.
    //  - 401 → 로그인 화면, 403 → 조회 권한 안내, 404 GROUP_NOT_FOUND → 그룹 선택 화면.
    var reloadKey by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<MembersUiState>(MembersUiState.Loading) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var creating by remember { mutableStateOf(false) }
    var inviteErrorRes by remember { mutableStateOf<Int?>(null) }
    var continueToInvite by remember { mutableStateOf<List<InviteParticipant>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId, reloadKey) {
        state = MembersUiState.Loading
        selectedIds = emptySet()
        delay(250) // DEMO: 조회 지연 흉내
        state = MembersUiState.Loaded(demoGroupMembers(groupId))
    }

    LaunchedEffect(continueToInvite) {
        continueToInvite?.let { invitees ->
            continueToInvite = null
            onInvite(invitees)
        }
    }

    val group = (state as? MembersUiState.Loaded)?.group
    val selectable = group?.selectableMembers(selfUserId).orEmpty()
    val doneEnabled = group != null && selectedIds.isNotEmpty() && !creating

    fun submitInvite() {
        if (creating || group == null || selectedIds.isEmpty()) return
        creating = true
        inviteErrorRes = null
        val invitees = selectable
            .filter { it.userId in selectedIds && it.userId != selfUserId }
            .distinctBy { it.userId }
            .map {
                InviteParticipant(
                    userId = it.userId,
                    nickname = it.nickname,
                    status = ParticipantStatus.PENDING,
                )
            }
        scope.launch {
            // TODO: POST /api/v1/ride-invitations, body {"groupId":…, "inviteeIds":[…]}
            //  - inviteeIds 는 본인 ID 와 중복 ID 를 제외한다.
            //  - 성공 201: invitationId 저장 → 참여 현황 화면 이동
            //    (RIDE_INVITE_STATUS 는 화면 이동 전부터 수신 대기해야 한다)
            //  - 실패: resolveCreateInviteError(status, code) 매핑
            //    reloadMembers → reloadKey += 1, openGroupSelect → onOpenGroupSelect(),
            //    navigateLogin → onNavigateLogin(), 그 외 → 안내 문구만 표시
            //  - 네트워크 오류 시 자동 재요청 금지(중복 초대 생성 위험)
            delay(700) // DEMO: 요청 지연 흉내
            creating = false
            continueToInvite = invitees
        }
    }

    RideMateBackground {
        Column(Modifier.fillMaxSize()) {
            RideTopBar(title = stringResource(R.string.select_member_title), onBack = onBack)

            when (val current = state) {
                is MembersUiState.Loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }

                is MembersUiState.Failed -> Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            AppIcons.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(current.messageRes),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        DialogButton(
                            text = stringResource(
                                if (current.navigateLogin) R.string.login_button else R.string.retry,
                            ),
                            container = PrimaryBlue,
                            contentColor = SurfaceWhite,
                            onClick = {
                                if (current.navigateLogin) onNavigateLogin() else reloadKey += 1
                            },
                        )
                    }
                }

                is MembersUiState.Loaded -> {
                    val info = current.group
                    RideCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = info.groupName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    AppIcons.Group,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.member_count_format, info.memberCount),
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                )
                            }
                            // description 이 null/빈 문자열이면 설명 영역을 숨긴다.
                            if (!info.description.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(info.description, fontSize = 14.sp, color = TextSecondary)
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.select_member_hint),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )

                    inviteErrorRes?.let { messageRes ->
                        Text(
                            text = stringResource(messageRes),
                            color = DangerRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }

                    RideCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                    ) {
                        if (selectable.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.select_member_empty),
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                )
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(selectable, key = { it.userId }) { member ->
                                    val checked = member.userId in selectedIds
                                    MemberRow(
                                        member = member,
                                        checked = checked,
                                        enabled = member.online && !creating,
                                        onToggle = {
                                            // 서버 요청 없이 선택 상태만 갱신한다. 오프라인 멤버는 선택 불가.
                                            if (!member.online || creating) return@MemberRow
                                            selectedIds = if (checked) {
                                                selectedIds - member.userId
                                            } else {
                                                selectedIds + member.userId
                                            }
                                            inviteErrorRes = null
                                        },
                                    )
                                    HorizontalDivider(
                                        color = LightBorder,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            RidePrimaryButton(
                text = if (creating) {
                    stringResource(R.string.waiting_processing)
                } else {
                    stringResource(R.string.select_done)
                },
                onClick = { submitInvite() },
                enabled = doneEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            )
        }
    }
}

/** DEMO: `GET /api/v1/groups/{groupId}/members` 응답을 흉내 낸 데이터(본인 포함). */
private fun demoGroupMembers(groupId: Long): GroupMembersUiModel = when (groupId) {
    2L -> GroupMembersUiModel(
        groupId = 2,
        groupName = "B그룹",
        memberCount = 3,
        description = "샘플용 그룹",
        members = listOf(
            GroupMemberUiModel(1, "홍길동", GroupMemberRole.OWNER, online = true),
            GroupMemberUiModel(3, "장발장", GroupMemberRole.MEMBER, online = true),
            GroupMemberUiModel(4, "김철수", GroupMemberRole.MEMBER, online = false),
        ),
    )

    else -> GroupMembersUiModel(
        groupId = 1,
        groupName = "한강 모임",
        memberCount = 4,
        description = "한강에서 즐기는 라이딩 동호회",
        members = listOf(
            GroupMemberUiModel(1, "홍길동", GroupMemberRole.OWNER, online = true),
            GroupMemberUiModel(2, "이순신", GroupMemberRole.MEMBER, online = false),
            GroupMemberUiModel(3, "장발장", GroupMemberRole.MEMBER, online = true),
            GroupMemberUiModel(4, "김철수", GroupMemberRole.MEMBER, online = true),
        ),
    )
}

@Composable
private fun MemberRow(
    member: GroupMemberUiModel,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) AccentBlue else SurfaceWhite)
                .border(1.5.dp, if (checked) AccentBlue else TextSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(AppIcons.Check, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            member.nickname,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (member.online) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (member.online) OnlineGreen else OfflineGrey),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (member.online) stringResource(R.string.online) else stringResource(R.string.offline),
            fontSize = 14.sp,
            color = TextSecondary,
        )
    }
}
