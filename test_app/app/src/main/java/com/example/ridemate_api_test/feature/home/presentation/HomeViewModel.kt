package com.example.ridemate_api_test.feature.home.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Stage 1 owns local presentation state only. Authentication remains app-owned. */
class HomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val uiState = mutableState.asStateFlow()

    fun showNotice(notice: HomeNotice) {
        mutableState.update { if (it.notice == null) it.copy(notice = notice) else it }
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }
}
