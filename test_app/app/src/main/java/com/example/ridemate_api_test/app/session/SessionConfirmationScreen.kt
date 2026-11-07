package com.example.ridemate_api_test.app.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.ridemate_api_test.core.designsystem.theme.RideMate_api_testTheme
import com.example.ridemate_api_test.feature.auth.domain.model.AuthUser
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase
import com.example.ridemate_api_test.feature.auth.domain.model.AuthState
import com.example.ridemate_api_test.feature.auth.presentation.login.messageResource

/** Temporary, explicitly labelled confirmation screen; not the product's home screen. */
@Composable
fun SessionConfirmationScreen(state: AuthState, onRetry: () -> Unit, onLogout: () -> Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.auth_confirmation_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.auth_confirmation_description))
                Text(stringResource(R.string.auth_welcome, state.user?.nickname.orEmpty()), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(state.phase.messageResource()))
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.problem?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
                if (state.phase == AuthPhase.DISCONNECTED) {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_retry)) }
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_logout)) }
            }
        }
    }
}

@Preview(name = "Temporary session confirmation", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SessionConfirmationPreview() {
    RideMate_api_testTheme {
        SessionConfirmationScreen(AuthState(AuthPhase.CONNECTED, AuthUser(1, "미리보기"), enteredSession = true), {}, {})
    }
}
