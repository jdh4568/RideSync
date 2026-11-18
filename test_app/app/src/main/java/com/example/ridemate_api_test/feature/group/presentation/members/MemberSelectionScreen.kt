package com.example.ridemate_api_test.feature.group.presentation.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeDesign
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.group.domain.model.GroupProblem
import com.example.ridemate_api_test.feature.group.domain.model.Membership
import com.example.ridemate_api_test.feature.group.presentation.GroupDesign
import com.example.ridemate_api_test.feature.group.presentation.GroupPrimaryButton
import com.example.ridemate_api_test.feature.group.presentation.messageResource

@Composable
fun MemberSelectionScreen(
    state: MemberSelectionUiState,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onComplete: () -> Unit,
    onDismissDeveloping: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.login_landscape), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 510.dp).fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.showDeveloping) {
                    Image(painterResource(R.drawable.group_back), stringResource(R.string.group_back),
                        Modifier.size(18.3715.dp, 10.1213.dp).rotate(90f))
                }
                Text(stringResource(R.string.member_selection_title), Modifier.weight(1f).semantics { heading() },
                    fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(Modifier.weight(1f).widthIn(max = 510.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 16.dp)) {
                state.group?.let { group -> item(key = "group") { GroupSummary(group) } }
                item(key = "prompt") {
                    Text(stringResource(R.string.member_selection_prompt),
                        Modifier.padding(start = 12.dp, top = 36.dp, bottom = 12.dp).semantics { heading() },
                        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                }
                if (state.status != MemberSelectionStatus.NOT_CONNECTED) item(key = "refresh") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.member_selection_snapshot), Modifier.weight(1f),
                            fontSize = 12.sp, lineHeight = 18.sp)
                        TextButton(onClick = onRefresh, enabled = state.canRefresh) {
                            Text(stringResource(R.string.member_selection_refresh))
                        }
                    }
                }
                if (state.status in setOf(MemberSelectionStatus.CONTENT, MemberSelectionStatus.ERROR) &&
                    !state.connected) item(key = "uncertain") {
                    MemberMessage(stringResource(R.string.member_selection_uncertain))
                }
                when (state.status) {
                    MemberSelectionStatus.NOT_CONNECTED -> item(key = "not-connected") {
                        MemberMessage(stringResource(R.string.member_selection_not_connected))
                    }
                    MemberSelectionStatus.LOADING -> item(key = "loading") {
                        Surface(shape = GroupDesign.Shape, color = Color.White) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.member_selection_loading), Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    MemberSelectionStatus.ERROR -> item(key = "error") {
                        Column {
                            MemberMessage(stringResource(state.problem?.messageResource() ?: R.string.group_list_error))
                            if (state.problem in setOf(GroupProblem.FORBIDDEN, GroupProblem.NOT_FOUND)) {
                                TextButton(onClick = onBack) { Text(stringResource(R.string.member_selection_back_to_groups)) }
                            } else TextButton(onClick = onRefresh, enabled = state.canRefresh) {
                                Text(stringResource(R.string.group_retry))
                            }
                        }
                    }
                    MemberSelectionStatus.CONTENT -> {
                        if (state.members.isEmpty()) item(key = "empty") {
                            MemberMessage(stringResource(R.string.member_selection_empty))
                        } else {
                            if (state.connected && state.snapshotConfirmed && state.members.none { it.online }) item(key = "offline") {
                                MemberMessage(stringResource(R.string.member_selection_all_offline))
                            }
                            itemsIndexed(state.members, key = { _, member -> member.userId }) { index, member ->
                                MemberRow(member, selected = member.userId in state.selectedUserIds,
                                    enabled = state.canSelect && member.online,
                                    confirmed = state.connected && state.snapshotConfirmed,
                                    first = index == 0, last = index == state.members.lastIndex,
                                    onToggle = { onToggle(member.userId) })
                            }
                        }
                    }
                }
                if (state.selectionLimitReached) item(key = "limit") {
                    MemberMessage(stringResource(R.string.member_selection_limit))
                }
            }
            Column(Modifier.widthIn(max = 510.dp).fillMaxWidth().padding(start = 23.dp, end = 23.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.canSelect && state.members.any { it.online } && state.selectedUserIds.isEmpty()) {
                    Text(stringResource(R.string.member_selection_required), Modifier.padding(bottom = 8.dp),
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                GroupPrimaryButton(onComplete, Modifier.fillMaxWidth(), enabled = state.canComplete) {
                    Text(stringResource(R.string.group_selection_complete), fontSize = 15.sp)
                }
            }
        }
    }
    if (state.showDeveloping) AlertDialog(onDismissRequest = onDismissDeveloping,
        title = { Text(stringResource(R.string.group_selection_complete)) },
        text = { Text(stringResource(R.string.member_selection_developing)) },
        confirmButton = { TextButton(onClick = onDismissDeveloping) { Text(stringResource(R.string.home_ok)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSummary(group: Group) {
    Surface(shape = GroupDesign.Shape, color = Color.White, border = BorderStroke(1.dp, HomeDesign.Outline)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 93.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.group_members), null, Modifier.size(18.6.dp, 15.6.dp))
                    Text(stringResource(R.string.member_selection_group_count, group.memberCount),
                        fontSize = 15.sp, color = GroupDesign.Muted)
                }
            }
            if (group.description.isNotBlank()) Text(group.description, Modifier.padding(top = 10.dp),
                fontSize = 15.sp, lineHeight = 20.sp, color = GroupDesign.Muted)
        }
    }
}

@Composable
private fun MemberMessage(message: String) {
    Surface(shape = GroupDesign.Shape, color = Color.White) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = Color.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MemberRow(member: MemberChoiceUi, selected: Boolean, enabled: Boolean,
    confirmed: Boolean, first: Boolean, last: Boolean, onToggle: () -> Unit) {
    val status = stringResource(if (!confirmed) R.string.member_selection_status_unknown
        else if (member.online) R.string.member_selection_online else R.string.member_selection_offline)
    val accessibility = if (confirmed && !member.online) stringResource(R.string.member_selection_offline_unselectable) else status
    Surface(color = Color.White, shape = RoundedCornerShape(
        topStart = if (first) 10.dp else 0.dp, topEnd = if (first) 10.dp else 0.dp,
        bottomStart = if (last) 10.dp else 0.dp, bottomEnd = if (last) 10.dp else 0.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { stateDescription = accessibility }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Image(painterResource(if (selected) R.drawable.member_selected else R.drawable.member_unselected),
                        null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
                    if (selected) Image(painterResource(R.drawable.member_check), null, Modifier.size(15.dp, 10.dp),
                        contentScale = ContentScale.Fit)
                }
                Text(member.nickname, Modifier.weight(1f), fontSize = 20.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (confirmed) Image(painterResource(if (member.online) R.drawable.member_online else R.drawable.member_offline),
                        null, Modifier.size(9.dp), contentScale = ContentScale.Fit)
                    Text(status, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            if (!last) Image(painterResource(R.drawable.member_divider), null, Modifier.fillMaxWidth().height(1.dp),
                contentScale = ContentScale.FillBounds)
        }
    }
}

/** Figma sample names and online states are confined to this Preview. */
@Preview(name = "Member selection", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MemberSelectionPreview() {
    HomeTheme {
        MemberSelectionScreen(MemberSelectionUiState(
            group = Group(1, "한강 모임", "한강에서 즐기는 라이딩 동호회", 13, 20, Membership.APPROVED),
            status = MemberSelectionStatus.CONTENT, connected = true, snapshotConfirmed = true,
            members = listOf(MemberChoiceUi(2, "홍길동", true), MemberChoiceUi(3, "이순신", false),
                MemberChoiceUi(4, "장발장", true), MemberChoiceUi(5, "김철수", true)), selectedUserIds = setOf(2)),
            {}, {}, {}, {}, {})
    }
}
