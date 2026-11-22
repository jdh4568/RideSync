package com.example.ridemate_api_test.feature.auth.presentation.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.core.designsystem.component.AppIcons
import com.example.ridemate_api_test.core.designsystem.component.BrandLogo
import com.example.ridemate_api_test.core.designsystem.component.RideDarkButton
import com.example.ridemate_api_test.core.designsystem.component.RideMateBackground
import com.example.ridemate_api_test.core.designsystem.component.RideTextField
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextPrimary

private const val MAX_NAME_LENGTH = 50
private const val MAX_PASSWORD_BYTES = 72

@Composable
fun SignupScreen(
    onBack: () -> Unit,
    onSignedUp: () -> Unit,
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var checkedId by remember { mutableStateOf("") }
    var idAvailable by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    val msgIdAvailable = stringResource(R.string.id_available)
    val msgIdUnavailable = stringResource(R.string.id_unavailable)
    val msgAllRequired = stringResource(R.string.all_fields_required)
    val msgCheckFirst = stringResource(R.string.check_id_first)
    val msgPwMismatch = stringResource(R.string.password_mismatch)
    val msgIdTooLong = stringResource(R.string.auth_id_too_long)
    val msgNicknameTooLong = stringResource(R.string.auth_nickname_too_long)
    val msgPwTooLong = stringResource(R.string.auth_password_too_long)

    fun resetIdCheck() {
        checkedId = ""
        idAvailable = false
        status = ""
    }

    RideMateBackground {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo(fontSize = 40)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = 586.dp)
                    .verticalScroll(rememberScrollState())
                    .background(SurfaceWhite, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 13.dp, top = 20.dp)
                        .size(28.dp)
                        .clickable(enabled = !submitting, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Back, contentDescription = null, tint = TextPrimary)
                }

                Text(
                    text = stringResource(R.string.signup_title),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp),
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )

                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp, start = 24.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    RideTextField(
                        value = loginId,
                        onValueChange = { loginId = it; resetIdCheck() },
                        placeholder = stringResource(R.string.login_id),
                        leadingIcon = AppIcons.Person,
                        enabled = !submitting,
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .height(37.dp)
                                    .padding(start = 8.dp)
                                    .border(1.dp, AccentBlue, RoundedCornerShape(10.dp))
                                    .background(SurfaceWhite, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !checking && !submitting) {
                                        checking = true
                                        // TODO(REST): GET /api/v1/auth/check-login-id?loginId=입력값 → { available: Boolean }
                                        checkedId = loginId.trim()
                                        idAvailable = true // 임시 목업: available=false 이면 msgIdUnavailable
                                        status = if (idAvailable) msgIdAvailable else msgIdUnavailable
                                        checking = false
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (checking) stringResource(R.string.checking_id)
                                    else stringResource(R.string.check_id),
                                    color = AccentBlue,
                                    fontSize = 15.sp,
                                )
                            }
                        },
                    )
                    RideTextField(
                        value = password,
                        onValueChange = { password = it; status = "" },
                        placeholder = stringResource(R.string.login_password),
                        leadingIcon = AppIcons.Lock,
                        isPassword = true,
                        enabled = !submitting,
                    )
                    RideTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it; status = "" },
                        placeholder = stringResource(R.string.login_password_confirm),
                        leadingIcon = AppIcons.Lock,
                        isPassword = true,
                        enabled = !submitting,
                    )
                    RideTextField(
                        value = nickname,
                        onValueChange = { nickname = it; status = "" },
                        placeholder = stringResource(R.string.login_nickname),
                        leadingIcon = AppIcons.Person,
                        enabled = !submitting,
                    )

                    Box(modifier = Modifier.height(40.dp)) {
                        if (status.isNotBlank()) {
                            Text(
                                text = status,
                                color = AccentBlue,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    RideDarkButton(
                        text = if (submitting) stringResource(R.string.auth_processing)
                        else stringResource(R.string.join_submit),
                        onClick = {
                            if (submitting) return@RideDarkButton
                            val id = loginId.trim()
                            val nick = nickname.trim()
                            when {
                                id.isBlank() || password.isBlank() ||
                                    passwordConfirm.isBlank() || nick.isBlank() ->
                                    status = msgAllRequired

                                id.length > MAX_NAME_LENGTH -> status = msgIdTooLong
                                nick.length > MAX_NAME_LENGTH -> status = msgNicknameTooLong
                                password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES ->
                                    status = msgPwTooLong

                                password != passwordConfirm -> status = msgPwMismatch
                                checkedId != id -> status = msgCheckFirst
                                !idAvailable -> status = msgIdUnavailable

                                else -> {
                                    submitting = true
                                    // TODO(REST): POST /api/v1/auth/signup
                                    // body: { loginId, password, passwordConfirm, nickname }
                                    // 오류 코드: PASSWORD_MISMATCH / INVALID_PASSWORD /
                                    // DUPLICATE_LOGIN_ID / DUPLICATE_NICKNAME / DUPLICATE_USER 매핑.
                                    // 성공 시 로그인 페이지로 이동.
                                    onSignedUp()
                                }
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
