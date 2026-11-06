package com.example.ridemate_api_test.feature.connection.presentation

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ridemate_api_test.core.designsystem.theme.RideMate_api_testTheme

@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    onAddressChanged: (String) -> Unit,
    onCheckConnection: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("RideMate", style = MaterialTheme.typography.headlineLarge)
                Text("앱 실행 및 서버 연결 테스트", style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("앱 실행 정상", style = MaterialTheme.typography.titleMedium)
                        Text("이 화면이 보이면 앱 설치와 Compose 화면 실행이 완료된 상태입니다.")
                        Text("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                    }
                }
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = onAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("서버 주소") },
                    supportingText = { Text("예: http://127.0.0.1:8080 (USB 포트 연결 필요)") },
                    enabled = !state.isChecking,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Button(onClick = onCheckConnection, enabled = !state.isChecking && state.baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isChecking) "연결 확인 중…" else "서버 연결 테스트")
                }
                if (state.isChecking) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let { message ->
                    Text(message, color = if (state.isSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
                Text("USB 연결 방법", style = MaterialTheme.typography.titleMedium)
                Text("1. PC에서 서버를 실행합니다.\n2. USB 디버깅을 허용하고 앱을 실행합니다.\n3. PC 터미널에서 아래 명령을 실행합니다.")
                Text("adb reverse tcp:8080 tcp:8080", style = MaterialTheme.typography.bodyLarge)
                Text("4. 위 주소를 유지하고 서버 연결 테스트를 누릅니다.\n\nUSB를 다시 연결하면 명령을 다시 실행해야 할 수 있습니다. 서버 포트가 다르면 주소와 명령의 포트를 함께 변경하세요.")
                Text("공개 아이디 중복 확인 API를 조회합니다. 계정이나 서버 데이터를 생성·변경하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ConnectionScreenPreview() {
    RideMate_api_testTheme { ConnectionScreen(ConnectionUiState(), {}, {}) }
}
