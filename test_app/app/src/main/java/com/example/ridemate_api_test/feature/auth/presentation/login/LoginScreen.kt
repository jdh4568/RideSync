package com.example.ridemate_api_test.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.example.ridemate_api_test.core.designsystem.component.RideOutlineButton
import com.example.ridemate_api_test.core.designsystem.component.RideTextField
import com.example.ridemate_api_test.core.designsystem.theme.AccentBlue
import com.example.ridemate_api_test.core.designsystem.theme.LightBorder
import com.example.ridemate_api_test.core.designsystem.theme.SurfaceWhite
import com.example.ridemate_api_test.core.designsystem.theme.TextSecondary

@Composable
fun LoginScreen(
    onNavigateSignup: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    val emptyFieldsMsg = stringResource(R.string.login_empty_fields)
    val invalidCredentialsMsg = stringResource(R.string.auth_invalid_credentials)

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = 407.dp)
                    .verticalScroll(rememberScrollState())
                    .background(SurfaceWhite, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 41.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    RideTextField(
                        value = loginId,
                        onValueChange = { loginId = it; status = "" },
                        placeholder = stringResource(R.string.login_id),
                        leadingIcon = AppIcons.Person,
                        enabled = !loggingIn,
                    )
                    RideTextField(
                        value = password,
                        onValueChange = { password = it; status = "" },
                        placeholder = stringResource(R.string.login_password),
                        leadingIcon = AppIcons.Lock,
                        isPassword = true,
                        enabled = !loggingIn,
                    )
                }

                RideDarkButton(
                    text = if (loggingIn) stringResource(R.string.auth_processing)
                    else stringResource(R.string.login_button),
                    onClick = {
                        if (loggingIn) return@RideDarkButton
                        if (loginId.isBlank() || password.isBlank()) {
                            status = emptyFieldsMsg
                            return@RideDarkButton
                        }
                        loggingIn = true
                        // TODO(REST): POST /api/v1/auth/login { loginId, password }
                        // 성공: accessToken / userId / nickname 저장 후 /ws/login 연결(Authorization: Bearer).
                        //  연결 콜백에서 onNavigateHome() 호출. 연결 실패 시 홈으로 이동하지 않고 재시도 안내.
                        // 401 INVALID_CREDENTIALS: status = invalidCredentialsMsg, 로그인 페이지 유지.
                        onNavigateHome()
                    },
                    enabled = !loggingIn,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .align(Alignment.CenterHorizontally),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = LightBorder)
                    Text(
                        text = stringResource(R.string.or_text),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    HorizontalDivider(Modifier.weight(1f), color = LightBorder)
                }

                RideOutlineButton(
                    text = stringResource(R.string.signup_button),
                    onClick = onNavigateSignup,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                )

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        color = AccentBlue,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
