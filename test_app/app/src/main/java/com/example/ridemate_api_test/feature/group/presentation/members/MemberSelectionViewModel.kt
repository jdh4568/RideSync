package com.example.ridemate_api_test.feature.group.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.Group
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemberSelectionViewModel(
    private val auth: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemberSelectionUiState())
    val uiState = mutableState.asStateFlow()
    private var active = false
    private var userId: Long? = null
    private val scope get() = testScope ?: viewModelScope

    init {
        scope.launch {
            auth.state.collect { state ->
                if (!state.enteredSession || (active && userId != state.user?.id)) reset()
            }
        }
    }

    fun enter(group: Group) {
        val state = auth.state.value
        if (!state.enteredSession || state.user == null || group.id <= 0) return
        if (active && userId == state.user.id && uiState.value.group?.id == group.id) return
        userId = state.user.id
        active = true
        // Stage 1 deliberately has no API or sample success path.
        mutableState.value = MemberSelectionUiState(group = group)
    }

    private fun ready() = active && auth.state.value.enteredSession && auth.state.value.user?.id == userId

    fun toggle(userId: Long) { if (ready()) mutableState.update { it.toggle(userId) } }
    fun complete() { if (ready()) mutableState.update { it.complete() } }
    fun dismissDeveloping() { mutableState.update { it.copy(showDeveloping = false) } }

    fun reset() {
        active = false
        userId = null
        mutableState.value = MemberSelectionUiState()
    }
}
