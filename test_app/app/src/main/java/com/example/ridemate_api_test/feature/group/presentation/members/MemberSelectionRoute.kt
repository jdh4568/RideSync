package com.example.ridemate_api_test.feature.group.presentation.members

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.core.designsystem.theme.HomeTheme
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.group.domain.model.Group
import com.example.ridemate_api_test.feature.invitation.presentation.InvitationUiState

@Composable
fun MemberSelectionRoute(container: AppContainer, group: Group, onBack: () -> Unit,
    onModalVisibilityChanged: (Boolean) -> Unit = {},
    creation: InvitationUiState = InvitationUiState(),
    onCreateInvitation: ((Long, Set<Long>) -> Unit)? = null,
    onClearCreationFailure: () -> Unit = {}) {
    val model: MemberSelectionViewModel = viewModel(factory = container.memberSelectionViewModelFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.showDeveloping) { onModalVisibilityChanged(state.showDeveloping) }
    DisposableEffect(Unit) { onDispose { onModalVisibilityChanged(false) } }
    LaunchedEffect(model, group.id) { model.enter(group) }
    val leave = { if (!creation.creating) { onClearCreationFailure(); model.reset(); onBack() } }
    BackHandler(enabled = !state.showDeveloping) { leave() }
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view, model) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            status?.let { controller.isAppearanceLightStatusBars = it }
            navigation?.let { controller.isAppearanceLightNavigationBars = it }
            if (activity?.isChangingConfigurations != true) model.reset()
        }
    }
    HomeTheme {
        MemberSelectionScreen(state, leave,
            onToggle = { if (!creation.creating) model.toggle(it) },
            onComplete = {
                if (onCreateInvitation == null) model.complete()
                else if (creation.canCreate) model.invitationSelection()?.let { (id, ids) -> onCreateInvitation(id, ids) }
            }, onDismissDeveloping = model::dismissDeveloping,
            onRefresh = { if (!creation.creating) model.refresh() }, creation = creation)
    }
}
