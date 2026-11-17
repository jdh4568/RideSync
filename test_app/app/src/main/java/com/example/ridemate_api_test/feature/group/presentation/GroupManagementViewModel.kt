package com.example.ridemate_api_test.feature.group.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Stage 1 owns only the form. No API calls or simulated successful mutations. */
class GroupManagementViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(GroupManagementUiState())
    val uiState = mutableState.asStateFlow()

    fun openCreation() {
        mutableState.update { if (it.showCreation) it else it.copy(showCreation = true, form = GroupCreationForm()) }
    }

    fun dismissCreation() {
        mutableState.update {
            if (it.isCreating) it else it.copy(showCreation = false, form = GroupCreationForm())
        }
    }

    fun updateForm(form: GroupCreationForm) {
        mutableState.update { if (it.showCreation && !it.isCreating) it.copy(form = form) else it }
    }

    fun reset() { mutableState.value = GroupManagementUiState() }
}
