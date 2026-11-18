package com.example.ridemate_api_test.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ridemate_api_test.feature.auth.presentation.signup.SignupRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.feature.home.presentation.HomeRoute
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginRoute
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.feature.group.presentation.GroupManagementRoute
import com.example.ridemate_api_test.feature.group.presentation.selection.GroupSelectionRoute
import com.example.ridemate_api_test.feature.group.presentation.selection.GroupSelectionViewModel
import com.example.ridemate_api_test.feature.group.presentation.members.MemberSelectionRoute
import com.example.ridemate_api_test.feature.invitation.presentation.InvitationViewModel
import com.example.ridemate_api_test.feature.invitation.presentation.InvitationRoute
import com.example.ridemate_api_test.feature.invitation.presentation.InvitationHomeActions
import com.example.ridemate_api_test.feature.ride.presentation.RideRoute
import androidx.compose.runtime.remember

private enum class AuthenticatedDestination { HOME, GROUPS, GROUP_SELECTION, MEMBER_SELECTION }

@Composable
fun RideMateApp(container: AppContainer) {
    val state by container.authRepository.state.collectAsStateWithLifecycle()
    var showSignup by rememberSaveable { mutableStateOf(false) }
    val loginModel: LoginViewModel = viewModel(factory = container.loginViewModelFactory)
    // No authenticated back stack remains after logout/expiry.
    if (state.enteredSession) {
        // This state belongs only to the current authenticated composition/user.
        var destination by rememberSaveable(state.user?.id) { mutableStateOf(AuthenticatedDestination.HOME) }
        val invitationModel: InvitationViewModel = viewModel(factory = container.invitationViewModelFactory)
        val invitations by invitationModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(invitations.createdInvitationId) {
            invitations.createdInvitationId?.let { id ->
                if (container.authRepository.state.value.enteredSession &&
                    container.authRepository.state.value.user?.id == state.user?.id) {
                    destination = AuthenticatedDestination.HOME
                    invitationModel.consumeNavigation(id)
                }
            }
        }
        var localModalOpen by remember(state.user?.id, destination) { mutableStateOf(false) }
        val activeRide = invitations.ride
        if (invitations.rideOpen && activeRide != null) {
            RideRoute(activeRide, onBackHome = {
                invitationModel.backHome()
                destination = AuthenticatedDestination.HOME
            })
            return
        }
        val backHome = { destination = AuthenticatedDestination.HOME }
        val open = { target: AuthenticatedDestination ->
            if (container.authRepository.state.value.enteredSession &&
                container.authRepository.state.value.user?.id == state.user?.id &&
                destination == AuthenticatedDestination.HOME) destination = target
        }
        when (destination) {
            AuthenticatedDestination.GROUPS -> GroupManagementRoute(container, onBack = backHome,
                onModalVisibilityChanged = { localModalOpen = it })
            AuthenticatedDestination.GROUP_SELECTION, AuthenticatedDestination.MEMBER_SELECTION -> {
                // This composition survives group/member navigation, but ends on return home/logout.
                val groupModel: GroupSelectionViewModel = viewModel(factory = container.groupSelectionViewModelFactory)
                val groupState by groupModel.uiState.collectAsStateWithLifecycle()
                val groupListState = rememberLazyListState()
                val activity = LocalActivity.current
                DisposableEffect(groupModel, activity) {
                    onDispose { if (activity?.isChangingConfigurations != true) groupModel.reset() }
                }
                if (destination == AuthenticatedDestination.MEMBER_SELECTION) {
                    val group = groupModel.groupForMembers()
                    if (group != null && groupState.memberSelectionOpen) {
                        MemberSelectionRoute(container, group, onBack = {
                            groupModel.returnFromMembers()
                            destination = AuthenticatedDestination.GROUP_SELECTION
                        }, onModalVisibilityChanged = { localModalOpen = it },
                            creation = invitations, onCreateInvitation = { id, ids ->
                                if (destination == AuthenticatedDestination.MEMBER_SELECTION)
                                    invitationModel.create(id, ids)
                            },
                            onClearCreationFailure = invitationModel::clearCreationFailure)
                    } else {
                        // A process-restored destination cannot restore an old user's selection.
                        LaunchedEffect(Unit) { destination = AuthenticatedDestination.GROUP_SELECTION }
                    }
                } else {
                    GroupSelectionRoute(groupModel, groupListState, onBack = backHome, onOpenMembers = {
                        val current = container.authRepository.state.value
                        if (destination == AuthenticatedDestination.GROUP_SELECTION && current.enteredSession &&
                            current.user?.id == state.user?.id && groupModel.openMembers() != null) {
                            destination = AuthenticatedDestination.MEMBER_SELECTION
                        }
                    })
                }
            }
            AuthenticatedDestination.HOME -> HomeRoute(container,
                onOpenGroups = { open(AuthenticatedDestination.GROUPS) },
                onStartRide = { open(AuthenticatedDestination.GROUP_SELECTION) },
                onModalVisibilityChanged = { localModalOpen = it },
                invitationActions = {
                    InvitationHomeActions(invitations, invitationModel::reopen, invitationModel::openRide)
                })
        }
        InvitationRoute(invitations, blockedByOtherModal = localModalOpen || invitations.creating ||
            (destination == AuthenticatedDestination.MEMBER_SELECTION && invitations.createdInvitationId != null),
            onHide = invitationModel::hidePanel, onRespond = invitationModel::respond,
            onStart = invitationModel::start, onCancel = invitationModel::cancel,
            onDismissNotice = invitationModel::dismissNotice)
    } else if (showSignup && !state.isBusy && state.user == null) {
        SignupRoute(container, onBack = { showSignup = false }, onSuccess = {
            loginModel.onSignupCompleted(it)
            showSignup = false
        })
    } else {
        LoginRoute(loginModel, onSignup = {
            if (!state.isBusy && state.user == null) showSignup = true
        })
    }
}
