package com.example.ridemate_api_test.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ridemate_api_test.feature.auth.presentation.signup.SignupRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ridemate_api_test.feature.home.presentation.HomeRoute
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginRoute
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ridemate_api_test.feature.group.presentation.GroupManagementRoute
import com.example.ridemate_api_test.feature.group.presentation.selection.GroupSelectionRoute

private enum class AuthenticatedDestination { HOME, GROUPS, GROUP_SELECTION }

@Composable
fun RideMateApp(container: AppContainer) {
    val state by container.authRepository.state.collectAsStateWithLifecycle()
    var showSignup by rememberSaveable { mutableStateOf(false) }
    val loginModel: LoginViewModel = viewModel(factory = container.loginViewModelFactory)
    // No authenticated back stack remains after logout/expiry.
    if (state.enteredSession) {
        // This state belongs only to the current authenticated composition/user.
        var destination by rememberSaveable(state.user?.id) { mutableStateOf(AuthenticatedDestination.HOME) }
        val backHome = { destination = AuthenticatedDestination.HOME }
        val open = { target: AuthenticatedDestination ->
            if (container.authRepository.state.value.enteredSession &&
                container.authRepository.state.value.user?.id == state.user?.id &&
                destination == AuthenticatedDestination.HOME) destination = target
        }
        when (destination) {
            AuthenticatedDestination.GROUPS -> GroupManagementRoute(container, onBack = backHome)
            AuthenticatedDestination.GROUP_SELECTION -> GroupSelectionRoute(container, onBack = backHome)
            AuthenticatedDestination.HOME -> HomeRoute(container,
                onOpenGroups = { open(AuthenticatedDestination.GROUPS) },
                onStartRide = { open(AuthenticatedDestination.GROUP_SELECTION) })
        }
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
