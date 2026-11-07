package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.LoginDesign
import com.example.ridemate_api_test.core.designsystem.theme.LoginTheme

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLoginIdChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onSignup: () -> Unit,
    onDismissNotice: () -> Unit,
    onRetryConnection: () -> Unit = {},
    onCancelLogin: () -> Unit = {},
) {
    val passwordFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        if (!state.isSubmitting && !state.hasPendingSession) {
            keyboard?.hide()
            focusManager.clearFocus()
            onLogin()
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        // A single inset owner and scroll area keep the form reachable with the IME open.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val heroHeight = (maxHeight - 407.dp).coerceIn(180.dp, 520.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box {
                    // Crop the original landscape to the same sky/mountain/lake region as Figma.
                    // The extra 30dp sits behind the rounded panel corners.
                    Image(
                        painterResource(R.drawable.login_landscape), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(heroHeight + 30.dp),
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.1f),
                    )
                    Column {
                        Box(Modifier.fillMaxWidth().heightIn(min = heroHeight), contentAlignment = Alignment.TopCenter) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Black)) { append(stringResource(R.string.login_brand_ride)) }
                                    // Allow the brand words to wrap together at enlarged font scales.
                                    append('\u200B')
                                    withStyle(SpanStyle(color = LoginDesign.Brand)) { append(stringResource(R.string.login_brand_mate)) }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .padding(top = (heroHeight * 0.22f).coerceAtMost(96.dp))
                                    .semantics { heading() },
                                style = LoginDesign.Logo,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(), color = Color.White,
                            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                            border = BorderStroke(1.dp, LoginDesign.PanelBorder),
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                Column(
                                    Modifier.widthIn(max = 510.dp).fillMaxWidth()
                                        .padding(horizontal = 45.dp).padding(top = 66.dp, bottom = 30.dp),
                                ) {
                                    val idError = when (state.loginIdError) {
                                        LoginIdError.REQUIRED -> stringResource(R.string.login_id_required)
                                        LoginIdError.TOO_LONG -> stringResource(R.string.login_id_too_long)
                                        null -> null
                                    }
                                    LoginInput(
                                        value = state.loginId, onValueChange = onLoginIdChanged,
                                        label = stringResource(R.string.login_id), icon = R.drawable.login_user,
                                        enabled = !state.isSubmitting && !state.hasPendingSession, error = idError,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next, autoCorrectEnabled = false),
                                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    LoginInput(
                                        value = state.password, onValueChange = onPasswordChanged,
                                        label = stringResource(R.string.login_password), icon = R.drawable.login_lock,
                                        modifier = Modifier.focusRequester(passwordFocus),
                                        enabled = !state.isSubmitting && !state.hasPendingSession,
                                        error = if (state.passwordRequired) stringResource(R.string.login_password_required) else null,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                        keyboardActions = KeyboardActions(onDone = { submit() }),
                                    )
                                    Spacer(Modifier.height(18.dp))
                                    Button(
                                        onClick = if (state.hasPendingSession) onRetryConnection else submit,
                                        enabled = !state.isSubmitting,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 59.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, LoginDesign.Outline),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    ) {
                                        Text(stringResource(if (state.isSubmitting) state.phase.messageResource()
                                            else if (state.hasPendingSession) R.string.auth_retry else R.string.login_submit),
                                            style = LoginDesign.Button, textAlign = TextAlign.Center)
                                    }
                                    if (state.isSubmitting) {
                                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }
                                    state.problem?.let { problem ->
                                        Text(stringResource(problem.messageResource()),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                                    }
                                    if (state.hasPendingSession) {
                                        TextButton(onClick = onCancelLogin, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.auth_cancel_login))
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    ) {
                                        Image(painterResource(R.drawable.login_divider), null,
                                            Modifier.weight(1f).height(1.dp), contentScale = ContentScale.FillBounds)
                                        Text(stringResource(R.string.login_or), style = LoginDesign.Divider, color = LoginDesign.Hint)
                                        Image(painterResource(R.drawable.login_divider), null,
                                            Modifier.weight(1f).height(1.dp), contentScale = ContentScale.FillBounds)
                                    }
                                    OutlinedButton(
                                        onClick = onSignup, enabled = !state.isSubmitting && !state.hasPendingSession,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 59.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, LoginDesign.Outline),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    ) {
                                        Text(stringResource(R.string.login_signup), style = LoginDesign.Button, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = onDismissNotice,
            title = { Text(stringResource(R.string.login_notice_title)) },
            text = { Text(stringResource(when (notice) {
                LoginNotice.SIGNUP_UNAVAILABLE -> R.string.login_signup_unavailable
            })) },
            confirmButton = { TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.login_notice_confirm)) } },
        )
    }
}

@Composable
private fun LoginInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: Int,
    enabled: Boolean,
    error: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 59.dp),
        enabled = enabled, singleLine = true, textStyle = LoginDesign.Input,
        label = { Text(label) },
        leadingIcon = { Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(30.dp)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = LoginDesign.Outline,
            unfocusedLabelColor = LoginDesign.Hint,
            focusedBorderColor = LoginDesign.Brand,
            focusedLabelColor = LoginDesign.Brand,
            cursorColor = LoginDesign.Brand,
        ),
    )
}

@Preview(name = "Figma 390x844", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Small 1.3", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Preview(name = "Large", showBackground = true, widthDp = 600, heightDp = 960)
@Preview(name = "Small 2.0", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Landscape", showBackground = true, widthDp = 844, heightDp = 390)
@Composable
private fun LoginPreview() {
    LoginTheme { LoginScreen(LoginUiState(), {}, {}, {}, {}, {}) }
}

@Preview(name = "Input errors", showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun LoginErrorPreview() {
    LoginTheme { LoginScreen(LoginUiState(loginIdError = LoginIdError.REQUIRED, passwordRequired = true), {}, {}, {}, {}, {}) }
}

@Preview(name = "Loading sample only", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginLoadingPreview() {
    LoginTheme { LoginScreen(LoginUiState(isSubmitting = true,
        phase = com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase.AUTHENTICATING), {}, {}, {}, {}, {}) }
}
