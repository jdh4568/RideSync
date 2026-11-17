package com.example.ridemate_api_test.feature.group.presentation.requests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.feature.group.presentation.GroupDesign
import com.example.ridemate_api_test.feature.group.presentation.messageResource
import com.example.ridemate_api_test.feature.group.domain.model.*

@Composable
internal fun GroupJoinRequestsDialog(
    state: GroupJoinRequestsUiState, onDismiss: () -> Unit, onRetry: () -> Unit,
    onRespond: (Long, GroupRequestDecision) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        dismissOnBackPress = true, dismissOnClickOutside = true,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        // This full-window modal owns outside taps, including the inset area.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
            GroupJoinRequestsPlacement { modifier -> GroupJoinRequestsContent(state, modifier, onRetry, onRespond) }
        }
    }
}

@Composable
private fun GroupJoinRequestsPlacement(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 34.dp, vertical = 16.dp)) {
        // Figma's 321 x 590 is a reference: width and height follow the available window.
        val topSpace = maxHeight * 0.06f
        val panelHeight = (maxHeight * 0.78f).coerceAtMost(590.dp)
        Box(Modifier.fillMaxSize().padding(top = topSpace), contentAlignment = Alignment.TopCenter) {
            content(Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .height(panelHeight))
        }
    }
}

@Composable
internal fun GroupJoinRequestsContent(
    state: GroupJoinRequestsUiState, modifier: Modifier = Modifier, onRetry: () -> Unit = {},
    onRespond: (Long, GroupRequestDecision) -> Unit = { _, _ -> },
) {
    val title = stringResource(R.string.group_requests_title)
    Surface(modifier.semantics { paneTitle = title }
        // Consume panel taps so only the surrounding modal backdrop closes it.
        .pointerInput(Unit) { detectTapGestures {} },
        shape = RoundedCornerShape(15.dp), color = Color(0xFFFEFEFE), contentColor = Color.Black) {
        Column(Modifier.padding(horizontal = 11.dp)) {
            Text(title, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp)
                .semantics { heading() }, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            HorizontalDivider(color = GroupDesign.InputBorder, thickness = 0.5.dp)
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.status == GroupJoinRequestsStatus.CONTENT && state.requests.isNotEmpty()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item { RequestsFeedback(state, onRetry) }
                    items(state.requests, key = { it.id }) { request ->
                        RequestRow(request, state, onRespond)
                        HorizontalDivider(color = GroupDesign.InputBorder, thickness = 0.5.dp)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        when (state.status) {
                            GroupJoinRequestsStatus.LOADING -> Text(stringResource(R.string.group_requests_loading))
                            GroupJoinRequestsStatus.EMPTY, GroupJoinRequestsStatus.NON_LEADER -> {
                                Text(stringResource(R.string.group_requests_empty), textAlign = TextAlign.Center)
                                if (state.status == GroupJoinRequestsStatus.NON_LEADER) Text(
                                    stringResource(R.string.group_requests_non_leader), Modifier.padding(top = 8.dp),
                                    fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                            }
                            else -> Unit
                        }
                        RequestsFeedback(state, onRetry)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestRow(request: GroupJoinRequest, state: GroupJoinRequestsUiState,
    onRespond: (Long, GroupRequestDecision) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.group_requests_message, request.nickname, request.groupName),
            fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        if (state.processingId == request.id) Text(stringResource(R.string.group_requests_processing),
            Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 20.sp)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RequestAction(R.string.group_requests_reject, Color(0xFFECEDF0), Color.Black, state.canRespond) {
                onRespond(request.id, GroupRequestDecision.REJECT)
            }
            RequestAction(R.string.group_requests_approve, GroupDesign.Primary, Color.White,
                state.canRespond && request.groupId !in state.fullGroupIds) {
                onRespond(request.id, GroupRequestDecision.APPROVE)
            }
        }
    }
}

@Composable
private fun RequestAction(label: Int, background: Color, foreground: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.widthIn(min = 55.dp).heightIn(min = 48.dp),
        shape = RoundedCornerShape(6.dp), border = BorderStroke(0.3.dp, Color(0xFF535353)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground,
            disabledContainerColor = Color(0xFFECEDF0), disabledContentColor = GroupDesign.Muted)) {
        Text(stringResource(label), fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun RequestsFeedback(state: GroupJoinRequestsUiState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val notice = when (state.notice) {
            GroupRequestsNotice.APPROVED -> R.string.group_requests_approved
            GroupRequestsNotice.REJECTED -> R.string.group_requests_rejected
            GroupRequestsNotice.UNCERTAIN -> R.string.group_requests_uncertain
            GroupRequestsNotice.CHANGED -> R.string.group_requests_changed
            null -> null
        }
        notice?.let { Text(stringResource(it), Modifier.padding(vertical = 8.dp),
            fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center) }
        if (state.notice == null) state.actionProblem?.let {
            Text(stringResource(if (it == GroupProblem.FULL) R.string.group_requests_full else it.messageResource()),
                Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        }
        state.listProblem?.let {
            Text(stringResource(R.string.group_requests_load_failed, stringResource(it.messageResource())),
                Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        }
        if (state.listProblem != null || state.needsVerification) TextButton(onClick = onRetry,
            enabled = !state.refreshing && state.processingId == null,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.group_requests_retry)) }
    }
}

@Preview(name = "Group join notifications · sample", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun GroupJoinRequestsPreview() {
    HomeTheme {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
            GroupJoinRequestsPlacement { modifier ->
                GroupJoinRequestsContent(GroupJoinRequestsUiState(GroupJoinRequestsStatus.CONTENT, listOf(
                    GroupJoinRequest(1, 1, 5, "홍길동", "A그룹"),
                    GroupJoinRequest(2, 1, 8, "이순신", "A그룹"),
                ), visible = true), modifier)
            }
        }
    }
}
