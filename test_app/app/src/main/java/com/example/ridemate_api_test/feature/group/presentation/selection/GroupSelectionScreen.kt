package com.example.ridemate_api_test.feature.group.presentation.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeDesign
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.group.domain.model.Membership
import com.example.ridemate_api_test.feature.group.presentation.GroupDesign
import com.example.ridemate_api_test.feature.group.presentation.GroupPrimaryButton
import com.example.ridemate_api_test.feature.group.presentation.messageResource

@Composable
fun GroupSelectionScreen(
    state: GroupSelectionUiState,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onComplete: () -> Unit,
    onDismissDeveloping: () -> Unit,
    onRetry: () -> Unit,
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
                Text(stringResource(R.string.group_selection_title), Modifier.weight(1f).semantics { heading() },
                    fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            LazyColumn(Modifier.weight(1f).widthIn(max = 510.dp).fillMaxWidth().selectableGroup(),
                contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(stringResource(R.string.group_selection_prompt), Modifier.padding(start = 12.dp, bottom = 20.dp),
                        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                }
                when (state.status) {
                    GroupSelectionStatus.LOADING -> item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.group_loading), Modifier.padding(top = 12.dp))
                        }
                    }
                    GroupSelectionStatus.ERROR -> item {
                        SelectionMessage(stringResource(state.problem?.messageResource() ?: R.string.group_list_error))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.group_retry)) }
                    }
                    GroupSelectionStatus.CONTENT -> {
                        if (state.groups.isEmpty()) item {
                            SelectionMessage(stringResource(R.string.group_selection_empty))
                        }
                        items(state.groups, key = { it.id }) { group ->
                            SelectionCard(group, selected = group.id == state.selectedGroupId,
                                enabled = state.canSelect, onSelect = { onSelect(group.id) })
                        }
                    }
                }
            }
            Column(Modifier.widthIn(max = 510.dp).fillMaxWidth().padding(start = 23.dp, end = 23.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.status == GroupSelectionStatus.CONTENT && state.groups.isNotEmpty() &&
                    state.selectedGroupId == null) {
                    Text(stringResource(R.string.group_selection_required), Modifier.padding(bottom = 8.dp),
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                GroupPrimaryButton(onComplete, Modifier.fillMaxWidth(), enabled = state.canComplete) {
                    Text(stringResource(R.string.group_selection_complete), fontSize = 15.sp)
                }
            }
        }
    }
    if (state.showDeveloping) {
        AlertDialog(onDismissRequest = onDismissDeveloping,
            title = { Text(stringResource(R.string.group_selection_complete)) },
            text = { Text(stringResource(R.string.group_selection_developing)) },
            confirmButton = {
                TextButton(onClick = onDismissDeveloping) { Text(stringResource(R.string.home_ok)) }
            })
    }
}

@Composable
private fun SelectionMessage(message: String) {
    Surface(shape = GroupDesign.Shape, color = Color.White) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = Color.Black, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionCard(group: Group, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(shape = GroupDesign.Shape,
        color = if (selected) Color(0xFFDDEFFF) else Color.White,
        border = BorderStroke(1.dp, if (selected) GroupDesign.Primary else HomeDesign.Outline)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 93.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 24.dp, end = 13.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.name, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
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
            // The entire card is the single radio target; these images are decorative.
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(if (selected) R.drawable.group_selection_checked else R.drawable.group_selection_unchecked),
                    null, Modifier.size(24.dp), contentScale = ContentScale.Fit)
                if (selected) Image(painterResource(R.drawable.group_selection_dot), null, Modifier.size(10.dp),
                    contentScale = ContentScale.Fit)
            }
        }
    }
}

/** Figma sample groups are used only by this Preview, never by the runtime ViewModel. */
@Preview(name = "Group selection", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GroupSelectionPreview() {
    HomeTheme {
        GroupSelectionScreen(GroupSelectionUiState(status = GroupSelectionStatus.CONTENT,
            groups = listOf(Group(1, "한강 모임", "한강 중심 라이딩", 13, 20, Membership.APPROVED),
                Group(2, "B그룹", "샘플용 그룹", 8, 10, Membership.APPROVED)), selectedGroupId = 1),
            {}, {}, {}, {}, {})
    }
}
