package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.theme.LoginDesign
import com.example.ridemate_api_test.core.designsystem.theme.LoginTheme
import com.example.ridemate_api_test.feature.auth.domain.repository.SignupProblem
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

@Composable
fun SignupScreen(
    state: SignupUiState,
    onValueChange: (SignupField, String) -> Unit,
    onCheck: (SignupField) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { List(4) { FocusRequester() } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = { keyboard?.hide(); focusManager.clearFocus(); onSubmit() }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val heroHeight = (maxHeight - 583.dp).coerceIn(160.dp, 320.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box {
                    // Same original image as Figma; show the sky/sunset above the signup panel.
                    Image(painterResource(R.drawable.login_landscape), null,
                        Modifier.fillMaxWidth().height(heroHeight + 30.dp),
                        contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.39f))
                    Column {
                        Box(Modifier.fillMaxWidth().heightIn(min = heroHeight),
                            contentAlignment = Alignment.TopCenter) {
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.Black)) { append(stringResource(R.string.login_brand_ride)) }
                                append('\u200B')
                                withStyle(SpanStyle(color = LoginDesign.Brand)) { append(stringResource(R.string.login_brand_mate)) }
                            }, Modifier.padding(horizontal = 16.dp).padding(top = heroHeight * 0.37f),
                                style = LoginDesign.Logo, textAlign = TextAlign.Center)
                        }
                        Surface(Modifier.fillMaxWidth(), color = Color.White,
                            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                            border = BorderStroke(1.dp, LoginDesign.PanelBorder)) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    IconButton(onClick = onBack, enabled = !state.isSubmitting, modifier = Modifier.padding(start = 5.dp)) {
                                        Image(painterResource(R.drawable.signup_back),
                                            stringResource(R.string.signup_back),
                                            Modifier.size(width = 12.dp, height = 20.dp),
                                            contentScale = ContentScale.Fit)
                                    }
                                    Text(stringResource(R.string.login_signup),
                                        Modifier.align(Alignment.TopCenter).padding(top = 30.dp, start = 56.dp, end = 56.dp)
                                            .semantics { heading() },
                                        style = LoginDesign.Input.copy(fontSize = 35.sp, lineHeight = 44.sp),
                                        textAlign = TextAlign.Center)
                                }
                                Spacer(Modifier.height(18.dp))
                                BoxWithConstraints(Modifier.widthIn(max = 510.dp).fillMaxWidth()) {
                                    val horizontal = if (maxWidth < 360.dp) 24.dp else 45.dp
                                    val separateCheck = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
                                    Column(Modifier.fillMaxWidth().padding(horizontal = horizontal),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SignupField.entries.forEachIndexed { index, field ->
                                            val password = field == SignupField.PASSWORD || field == SignupField.PASSWORD_CONFIRM
                                            val value = when (field) {
                                                SignupField.LOGIN_ID -> state.loginId
                                                SignupField.PASSWORD -> state.password
                                                SignupField.PASSWORD_CONFIRM -> state.passwordConfirm
                                                SignupField.NICKNAME -> state.nickname
                                            }
                                            val label = stringResource(when (field) {
                                                SignupField.LOGIN_ID -> R.string.login_id
                                                SignupField.PASSWORD -> R.string.login_password
                                                SignupField.PASSWORD_CONFIRM -> R.string.signup_password_confirm
                                                SignupField.NICKNAME -> R.string.signup_nickname
                                            })
                                            val error = state.errors[field]?.let {
                                                when (it) {
                                                    SignupError.REQUIRED -> stringResource(R.string.signup_required, label)
                                                    SignupError.TOO_LONG -> stringResource(R.string.signup_too_long, label)
                                                    SignupError.PASSWORD_TOO_LONG -> stringResource(R.string.signup_password_too_long)
                                                    SignupError.PASSWORD_MISMATCH -> stringResource(R.string.signup_password_mismatch)
                                                }
                                            }
                                            val check = state.checks[field] ?: AvailabilityState()
                                            val checkEnabled = !state.isSubmitting && check.status != Availability.CHECKING
                                            Column {
                                                OutlinedTextField(value, { onValueChange(field, it) },
                                                    Modifier.fillMaxWidth().heightIn(min = 59.dp).focusRequester(focus[index]),
                                                    label = { Text(label) }, singleLine = true,
                                                    enabled = !state.isSubmitting,
                                                    textStyle = LoginDesign.Input,
                                                    leadingIcon = { Image(painterResource(if (password) R.drawable.login_lock else R.drawable.login_user),
                                                        null, Modifier.size(30.dp)) },
                                                    trailingIcon = if (!password && !separateCheck) {
                                                        { CheckButton(checkEnabled, check.status == Availability.CHECKING) { onCheck(field) } }
                                                    } else null,
                                                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                                                        imeAction = if (index == 3) ImeAction.Done else ImeAction.Next,
                                                        autoCorrectEnabled = false),
                                                    keyboardActions = KeyboardActions(
                                                        onNext = { if (index < 3) focus[index + 1].requestFocus() },
                                                        onDone = { submit() }),
                                                    isError = error != null,
                                                    supportingText = error?.let { { Text(it) } },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = LoginDesign.Outline,
                                                        unfocusedLabelColor = LoginDesign.Hint,
                                                        focusedBorderColor = LoginDesign.Brand,
                                                        focusedLabelColor = LoginDesign.Brand,
                                                        cursorColor = LoginDesign.Brand))
                                                if (!password && separateCheck) {
                                                    Box(Modifier.align(Alignment.End)) {
                                                        CheckButton(checkEnabled, check.status == Availability.CHECKING) { onCheck(field) }
                                                    }
                                                }
                                                if (!password && check.status != Availability.UNCHECKED) {
                                                    Text(stringResource(when (check.status) {
                                                        Availability.CHECKING -> R.string.signup_checking
                                                        Availability.AVAILABLE -> R.string.signup_available
                                                        Availability.TAKEN -> R.string.signup_taken
                                                        Availability.ERROR -> (check.problem ?: SignupProblem.NETWORK).messageResource()
                                                        Availability.UNCHECKED -> R.string.signup_check
                                                    }), color = if (check.status == Availability.TAKEN || check.status == Availability.ERROR)
                                                        MaterialTheme.colorScheme.error else LoginDesign.Brand,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Button(submit, Modifier.fillMaxWidth().heightIn(min = 59.dp),
                                            enabled = state.canSubmit,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, LoginDesign.Outline),
                                            contentPadding = PaddingValues(12.dp)) {
                                            Text(stringResource(if (state.isSubmitting) R.string.signup_submitting else R.string.signup_submit), style = LoginDesign.Button,
                                                textAlign = TextAlign.Center)
                                        }
                                        if (state.isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        if (!state.isSubmitting && !state.canSubmit) {
                                            Text(stringResource(R.string.signup_require_checks),
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        state.problem?.let {
                                            Text(stringResource(if (state.outcomeUncertain) R.string.signup_uncertain else it.messageResource()),
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckButton(enabled: Boolean, checking: Boolean, onClick: () -> Unit) {
    TextButton(onClick, Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF4F7CFF)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4F7CFF))) {
        Text(stringResource(if (checking) R.string.signup_checking else R.string.signup_check), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Preview(name = "Signup 390x844", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignupPreview() {
    LoginTheme { SignupScreen(SignupUiState(), { _, _ -> }, {}, {}, {}) }
}

private fun SignupProblem.messageResource(): Int = when (this) {
    SignupProblem.INVALID_REQUEST -> R.string.signup_invalid_request
    SignupProblem.PASSWORD_MISMATCH -> R.string.signup_password_mismatch
    SignupProblem.INVALID_PASSWORD -> R.string.signup_password_too_long
    SignupProblem.DUPLICATE_LOGIN_ID -> R.string.signup_duplicate_id
    SignupProblem.DUPLICATE_NICKNAME -> R.string.signup_duplicate_nickname
    SignupProblem.DUPLICATE_USER -> R.string.signup_duplicate_user
    SignupProblem.UNAUTHORIZED -> R.string.signup_unauthorized
    SignupProblem.FORBIDDEN -> R.string.auth_forbidden
    SignupProblem.NETWORK -> R.string.auth_network_error
    SignupProblem.SERVER -> R.string.auth_server_error
    SignupProblem.INVALID_RESPONSE -> R.string.auth_invalid_response
}

