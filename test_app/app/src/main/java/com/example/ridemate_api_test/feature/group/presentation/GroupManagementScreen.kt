package com.example.ridemate_api_test.feature.group.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeDesign
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme

internal object GroupDesign {
    val Primary = Color(0xFF0683EA)
    val Disabled = Color(0xFF808080)
    val Muted = Color(0xFF808080)
    val InputBorder = Color(0xFFCDCDCD)
    val Shape = RoundedCornerShape(10.dp)
}

@Composable
fun GroupManagementScreen(
    state: GroupManagementUiState,
    onBack: () -> Unit,
    onOpenCreation: () -> Unit,
    onDismissCreation: () -> Unit,
    onFormChange: (GroupCreationForm) -> Unit,
    onRefresh: () -> Unit,
    onJoin: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.login_landscape), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 510.dp).fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Image(painterResource(R.drawable.group_back), stringResource(R.string.group_back),
                        Modifier.size(18.3715.dp, 10.1213.dp).rotate(90f))
                }
                Text(stringResource(R.string.home_groups), Modifier.weight(1f).semantics { heading() },
                    fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                TextButton(onClick = onRefresh,
                    enabled = state.listStatus != GroupListStatus.PREPARING &&
                        state.listStatus != GroupListStatus.LOADING && !state.isRefreshing && !state.isCreating &&
                        state.joiningGroupId == null,
                    contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.widthIn(min = 48.dp)) {
                    Text(stringResource(R.string.group_refresh), fontSize = 12.sp, color = Color.Black)
                }
            }
            LazyColumn(Modifier.weight(1f).widthIn(max = 510.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.isRefreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                when (state.listStatus) {
                    GroupListStatus.PREPARING -> item { GroupMessage(R.string.group_api_preparing) }
                    GroupListStatus.LOADING -> item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.group_loading), Modifier.padding(top = 12.dp))
                        }
                    }
                    GroupListStatus.ERROR -> item {
                        GroupMessage(R.string.group_list_error)
                        TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                            Text(stringResource(R.string.group_retry))
                        }
                    }
                    GroupListStatus.CONTENT -> if (state.groups.isEmpty()) item { GroupMessage(R.string.group_empty) }
                }
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(group, isJoining = state.joiningGroupId == group.id,
                        canJoin = state.listStatus == GroupListStatus.CONTENT && !state.isRefreshing &&
                            state.joiningGroupId == null && !state.isCreating,
                        onJoin = { onJoin(group.id) })
                }
            }
            GroupPrimaryButton(onClick = onOpenCreation,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).widthIn(min = 118.dp)) {
                Text(stringResource(R.string.group_create), fontSize = 15.sp)
            }
        }
    }
    if (state.showCreation) {
        GroupCreationDialog(state.form, state.isCreating, state.listStatus != GroupListStatus.PREPARING,
            onFormChange, onDismissCreation, onCreate)
    }
}

@Composable
private fun GroupMessage(message: Int) {
    Surface(shape = GroupDesign.Shape, color = Color.White) {
        Text(stringResource(message), Modifier.fillMaxWidth().padding(20.dp),
            color = Color.Black, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(group: GroupCardUi, isJoining: Boolean, canJoin: Boolean, onJoin: () -> Unit) {
    Surface(shape = GroupDesign.Shape, color = Color.White,
        border = BorderStroke(1.dp, HomeDesign.Outline)) {
        val stack = LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxWidth().heightIn(min = 93.dp).padding(horizontal = 24.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.name, Modifier.widthIn(min = 84.dp), fontSize = 20.sp,
                            lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                        Row(Modifier.heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.group_members), null, Modifier.size(18.6.dp, 15.6.dp))
                            Text(stringResource(R.string.group_member_count, group.memberCount, group.maxMembers),
                                fontSize = 15.sp, color = GroupDesign.Muted)
                        }
                    }
                    if (group.description.isNotEmpty()) Text(group.description, Modifier.padding(top = 10.dp),
                        fontSize = 15.sp, lineHeight = 20.sp, color = GroupDesign.Muted)
                }
                if (!stack) GroupJoinButton(group.joinAction, isJoining, canJoin, onJoin)
            }
            if (stack) Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.CenterEnd) {
                GroupJoinButton(group.joinAction, isJoining, canJoin, onJoin)
            }
        }
    }
}

@Composable
private fun GroupJoinButton(action: GroupJoinAction, isJoining: Boolean, canJoin: Boolean, onJoin: () -> Unit) {
    val label = when {
        isJoining -> R.string.group_joining
        action == GroupJoinAction.APPLY -> R.string.group_join
        action == GroupJoinAction.PENDING -> R.string.group_pending
        action == GroupJoinAction.JOINED -> R.string.group_joined
        action == GroupJoinAction.FULL -> R.string.group_full
        else -> R.string.group_unknown
    }
    GroupPrimaryButton(onJoin, enabled = canJoin && action == GroupJoinAction.APPLY && !isJoining,
        modifier = Modifier.widthIn(min = 92.dp)) { Text(stringResource(label), fontSize = 13.sp) }
}

@Composable
internal fun GroupPrimaryButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = GroupDesign.Shape,
        border = BorderStroke(1.dp, HomeDesign.Outline),
        colors = ButtonDefaults.buttonColors(containerColor = GroupDesign.Primary, contentColor = Color.White,
            disabledContainerColor = GroupDesign.Disabled, disabledContentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content)
}

/** Figma examples are confined to Preview; never returned by a repository. */
internal fun previewGroups() = listOf(
    GroupCardUi(1, "B그룹", "한강 중심 라이딩", 8, 10, GroupMembership.NONE),
    GroupCardUi(2, "C그룹", "한강 중심 라이딩", 8, 10, GroupMembership.PENDING),
    GroupCardUi(3, "한강 모임", "한강 중심 라이딩", 8, 10, GroupMembership.APPROVED),
    GroupCardUi(4, "주말 라이딩", "정원이 가득 찬 그룹", 10, 10, GroupMembership.NONE),
)

@Preview(name = "Group management", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GroupManagementPreview() {
    HomeTheme {
        GroupManagementScreen(GroupManagementUiState(listStatus = GroupListStatus.CONTENT, groups = previewGroups()),
            {}, {}, {}, {}, {}, {}, {})
    }
}
