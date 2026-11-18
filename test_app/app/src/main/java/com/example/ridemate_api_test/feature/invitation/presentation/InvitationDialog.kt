package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ridemate_api_test.R

private val ActionBlue = Color(0xFF0683EA)
private val PanelTile = Color(0xFFF5F5F5)

@Composable
internal fun InvitationDialog(panel: InvitationPanel, onHide: () -> Unit) {
    Dialog(onDismissRequest = onHide, properties = DialogProperties(
        dismissOnBackPress = true, dismissOnClickOutside = false,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        InvitationPlacement {
            InvitationContent(panel, onHide = onHide)
        }
    }
}

@Composable
internal fun InvitationPlacement(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 321.dp).fillMaxWidth().wrapContentHeight()) { content() }
    }
}

/** No networking. Stage 1 Route leaves actions unavailable; Preview may render enabled buttons. */
@Composable
internal fun InvitationContent(
    panel: InvitationPanel,
    onHide: () -> Unit,
    actionsAvailable: Boolean = false,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onStart: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val leader = panel as? InvitationPanel.Leader
    val member = panel as? InvitationPanel.Member
    val title = stringResource(if (leader != null) R.string.invitation_leader_title else R.string.invitation_member_title)
    Surface(Modifier.fillMaxWidth().semantics { paneTitle = title },
        shape = RoundedCornerShape(20.dp), color = Color(0xFFFEFEFE), contentColor = Color.Black) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 19.dp).padding(top = 28.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.invitation_riding), null, Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, Modifier.fillMaxWidth().semantics { heading() }, fontSize = 20.sp,
                lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (leader != null) {
                if (leader.counts == null) {
                    Text(stringResource(R.string.invitation_status_loading), textAlign = TextAlign.Center)
                } else {
                    Counts(leader.counts)
                }
            } else if (member != null) {
                Text(stringResource(R.string.invitation_question, member.groupName),
                    color = Color(0xFF8A9197), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = PanelTile) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Detail(stringResource(R.string.invitation_group), member.groupName)
                        Detail(stringResource(R.string.invitation_leader), member.leaderNickname)
                    }
                }
            }
            member?.response?.takeUnless { it == InvitationResponseUi.READY }?.let { response ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(when (response) {
                    InvitationResponseUi.SENDING -> R.string.invitation_sending
                    InvitationResponseUi.ACCEPT_SENT -> R.string.invitation_accept_sent
                    InvitationResponseUi.REJECT_SENT -> R.string.invitation_reject_sent
                    InvitationResponseUi.FAILED -> R.string.invitation_send_failed
                    InvitationResponseUi.UNCERTAIN -> R.string.invitation_response_uncertain
                    InvitationResponseUi.READY -> R.string.invitation_response_uncertain
                }), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                if (response in setOf(InvitationResponseUi.ACCEPT_SENT, InvitationResponseUi.REJECT_SENT)) {
                    Text(stringResource(R.string.invitation_response_uncertain),
                        color = Color(0xFF666262), fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                }
            }
            if (!actionsAvailable) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.invitation_not_connected), fontSize = 13.sp, lineHeight = 19.sp,
                    color = Color(0xFF666262), textAlign = TextAlign.Center)
            }
            if (leader?.busy == true || member?.response == InvitationResponseUi.SENDING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            val responding = member?.response == InvitationResponseUi.READY || member?.response == InvitationResponseUi.FAILED
            val secondaryLabel = stringResource(if (leader != null) R.string.invitation_cancel else R.string.invitation_reject)
            val primaryLabel = stringResource(if (leader != null) R.string.invitation_start else R.string.invitation_accept)
            ActionButtons(
                secondaryLabel, primaryLabel,
                secondaryEnabled = actionsAvailable && if (leader != null) !leader.busy else responding,
                primaryEnabled = actionsAvailable && if (leader != null) !leader.busy && leader.canStart && leader.counts != null else responding,
                onSecondary = if (leader != null) onCancel else onReject,
                onPrimary = if (leader != null) onStart else onAccept,
            )
            TextButton(onClick = onHide, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.invitation_later), color = Color(0xFF666262), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.8f), color = Color(0xFF8A9197), fontSize = 14.sp, lineHeight = 20.sp)
        Text(value, Modifier.weight(1.2f), color = Color(0xFF151515), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Counts(counts: InvitationCounts) {
    val entries = listOf(R.string.invitation_requested to counts.requested, R.string.invitation_accepted to counts.accepted,
        R.string.invitation_pending to counts.pending, R.string.invitation_rejected to counts.rejected)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = PanelTile) {
        BoxWithConstraints(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            val columns = if (maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (label, count) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(label), color = Color(0xFF666262), fontSize = 17.sp, lineHeight = 24.sp)
                                Text(stringResource(R.string.invitation_count, count), color = Color.Black,
                                    fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    secondary: String, primary: String, secondaryEnabled: Boolean, primaryEnabled: Boolean,
    onSecondary: () -> Unit, onPrimary: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
        val secondaryContent: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = onSecondary, enabled = secondaryEnabled, modifier = modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEDF0), contentColor = Color.Black)) {
                Text(secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        val primaryContent: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White)) {
                Text(primary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryContent(Modifier.fillMaxWidth()); primaryContent(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                secondaryContent(Modifier.weight(1f)); primaryContent(Modifier.weight(1f))
            }
        }
    }
}
