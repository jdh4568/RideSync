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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.DialogButton
import com.example.ridemate_api_test.core.designsystem.component.RideCard
import com.example.ridemate_api_test.core.designsystem.component.RideMateBackground
import com.example.ridemate_api_test.core.designsystem.component.RidePrimaryButton
import com.example.ridemate_api_test.core.designsystem.component.RideTopBar
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.DangerRed
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.PrimaryBlue
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary
import com.example.ridemate_api_test.feature.group.domain.model.GroupUiModel
import com.example.ridemate_api_test.feature.group.domain.model.MembershipStatus
import kotlinx.coroutines.delay

private sealed interface GroupListUiState {
    data object Loading : GroupListUiState
    data class Loaded(val groups: List<GroupUiModel>) : GroupListUiState
    data class Failed(val messageRes: Int, val unauthorized: Boolean) : GroupListUiState
}

@Composable
fun GroupSelectScreen(
    onBack: () -> Unit,
    onNext: (Long) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    // TODO: GET /api/v1/groups/owned → myRole 이 OWNER 인 그룹만 노출한다.
    //  401 UNAUTHORIZED/INVALID_TOKEN → 로그인 화면 이동, 그 외 오류 → 조회 실패 안내 + 재시도.
    //  빈 배열([]) 과 조회 실패는 서로 다른 문구로 구분한다.
    var reloadKey by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<GroupListUiState>(GroupListUiState.Loading) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(reloadKey) {
        state = GroupListUiState.Loading
        selectedId = null
        delay(250) // DEMO: 조회 지연 흉내
        state = GroupListUiState.Loaded(demoOwnedGroups())
    }

    val groups = (state as? GroupListUiState.Loaded)?.groups.orEmpty()
    val ready = state is GroupListUiState.Loaded && groups.isNotEmpty()

    RideMateBackground {
        Column(Modifier.fillMaxSize()) {
            RideTopBar(title = stringResource(R.string.select_group_title), onBack = onBack)
            Text(
                text = stringResource(R.string.select_group_hint),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            when (val current = state) {
                is GroupListUiState.Loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }

                is GroupListUiState.Failed -> LoadFailedNotice(
                    messageRes = current.messageRes,
                    actionTextRes = if (current.unauthorized) R.string.login_button else R.string.retry,
                    onAction = {
                        if (current.unauthorized) {
                            onNavigateLogin()
                        } else {
                            reloadKey += 1
                        }
                    },
                )

                is GroupListUiState.Loaded -> if (current.groups.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.select_group_empty),
                            color = TextSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(current.groups, key = { it.groupId }) { group ->
                            SelectableGroupCard(
                                group = group,
                                selected = group.groupId == selectedId,
                                onClick = {
                                    // 한 번에 하나만 선택한다. 서버 요청 없이 선택 상태만 갱신.
                                    selectedId = group.groupId
                                },
                            )
                        }
                    }
                }
            }

            RidePrimaryButton(
                text = stringResource(R.string.select_done),
                onClick = { selectedId?.let(onNext) },
                enabled = ready && selectedId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            )
        }
    }
}

/** DEMO: `GET /api/v1/groups/owned` 응답을 흉내 낸 데이터. */
private fun demoOwnedGroups(): List<GroupUiModel> = listOf(
    GroupUiModel(
        groupId = 1,
        groupName = "한강 모임",
        description = "한강 중심 라이딩",
        memberCount = 13,
        maxMembers = 20,
        membershipStatus = MembershipStatus.APPROVED,
        myRole = "OWNER",
    ),
    GroupUiModel(
        groupId = 2,
        groupName = "B그룹",
        description = "샘플용 그룹",
        memberCount = 8,
        maxMembers = 10,
        membershipStatus = MembershipStatus.APPROVED,
        myRole = "OWNER",
    ),
)

@Composable
private fun LoadFailedNotice(
    messageRes: Int,
    actionTextRes: Int,
    onAction: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(messageRes),
                color = TextPrimary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            DialogButton(
                text = stringResource(actionTextRes),
                container = PrimaryBlue,
                contentColor = SurfaceWhite,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun SelectableGroupCard(
    group: GroupUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RideCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (selected) AccentBlue else LightBorder,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.groupName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    Icon(AppIcons.Group, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.group_ratio_format,
                            group.memberCount,
                            group.maxMembers,
                        ),
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                }
                if (group.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(group.description, fontSize = 14.sp, color = TextSecondary)
                }
            }
            RadioCircle(selected = selected)
        }
    }
}

@Composable
private fun RadioCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) AccentBlue else SurfaceWhite)
            .border(1.5.dp, if (selected) AccentBlue else TextSecondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(AppIcons.Check, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
        }
    }
}

/** 선택 카드 테두리 색상 등 공통 토큰은 테마에서 가져온다. */
