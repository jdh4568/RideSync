package com.example.ridemate_api_test.feature.connection.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionViewModel(private val repository: ConnectionRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectionUiState())
    val uiState = mutableState.asStateFlow()

    fun updateBaseUrl(value: String) {
        if (!mutableState.value.isChecking) {
            mutableState.update { it.copy(baseUrl = value, message = null, isSuccess = false) }
        }
    }

    fun checkConnection() {
        if (mutableState.value.isChecking) return
        val address = mutableState.value.baseUrl
        mutableState.update { it.copy(isChecking = true, message = null, isSuccess = false) }
        viewModelScope.launch {
            try {
                val status = repository.checkConnection(address)
                mutableState.update {
                    it.copy(isChecking = false, isSuccess = true,
                        message = "연결 성공 · HTTP $status\nRideMate API 응답을 확인했습니다.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is SocketTimeoutException -> "연결 시간이 초과되었습니다. 서버 실행 상태와 네트워크를 확인해 주세요."
                    is ConnectException -> "서버에 연결할 수 없습니다. 서버 실행과 USB 포트 연결(adb reverse)을 확인해 주세요."
                    is UnknownHostException -> "서버 주소를 찾을 수 없습니다. 주소를 확인해 주세요."
                    else -> error.message ?: "연결에 실패했습니다. 서버 주소와 네트워크를 확인해 주세요."
                }
                mutableState.update { it.copy(isChecking = false, message = message, isSuccess = false) }
            }
        }
    }
}
