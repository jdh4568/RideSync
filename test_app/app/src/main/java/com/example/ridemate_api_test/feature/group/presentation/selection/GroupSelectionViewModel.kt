package com.example.ridemate_api_test.feature.group.presentation.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stage 1 only: no repository query or runtime sample groups. */
class GroupSelectionViewModel(
    private val auth: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupSelectionUiState())
    val uiState = mutableState.asStateFlow()
    private var active = false
    private var userId: Long? = null

    init {
        (testScope ?: viewModelScope).launch {
            auth.state.collect { state ->
                if (!state.enteredSession || (active && userId != state.user?.id)) reset()
            }
        }
    }

    fun enter() {
        if (active || !auth.state.value.enteredSession) return
        active = true
        userId = auth.state.value.user?.id
        mutableState.value = GroupSelectionUiState()
    }

    private fun ready() = active && auth.state.value.enteredSession && auth.state.value.user?.id == userId

    fun select(groupId: Long) {
        if (ready()) mutableState.update { it.select(groupId) }
    }

    fun complete() {
        if (ready()) mutableState.update { it.complete() }
    }

    fun dismissDeveloping() {
        mutableState.update { it.copy(showDeveloping = false) }
    }

    fun reset() {
        active = false
        userId = null
        mutableState.value = GroupSelectionUiState()
    }
}
