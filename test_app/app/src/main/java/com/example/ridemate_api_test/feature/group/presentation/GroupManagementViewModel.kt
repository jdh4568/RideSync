package com.example.ridemate_api_test.feature.group.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GroupManagementViewModel(
    private val repository: GroupRepository,
    private val auth: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupManagementUiState())
    val uiState = mutableState.asStateFlow()
    private val scope get() = testScope ?: viewModelScope
    private var active = false
    private var ownerId: Long? = null
    private var generation = 0
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private fun ready() = active && auth.state.value.enteredSession && auth.state.value.user?.id == ownerId
    private fun current(ticket: Int) = ticket == generation && ready()

    init {
        scope.launch {
            auth.state.collect { state ->
                if (!state.enteredSession || (active && ownerId != state.user?.id)) reset()
            }
        }
    }

    fun enter() {
        if (active || !auth.state.value.enteredSession) return
        active = true
        ownerId = auth.state.value.user?.id
        refresh()
    }

    fun refresh() {
        if (!ready() || loadJob?.isActive == true ||
            uiState.value.isCreating || uiState.value.joiningGroupId != null) return
        val ticket = generation
        val initial = uiState.value.listStatus != GroupListStatus.CONTENT
        mutableState.update { it.copy(listStatus = if (initial) GroupListStatus.LOADING else it.listStatus,
            isRefreshing = !initial, listProblem = null) }
        loadJob = scope.launch {
            try {
                val groups = repository.groups()
                ensureActive()
                if (current(ticket)) mutableState.update { it.copy(listStatus = GroupListStatus.CONTENT,
                    groups = groups.map { group -> GroupCardUi(group.id, group.name, group.description, group.memberCount,
                        group.maxMembers, GroupMembership.valueOf(group.membership.name)) },
                    isRefreshing = false, listProblem = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (current(ticket)) mutableState.update { it.copy(
                    listStatus = if (initial) GroupListStatus.ERROR else it.listStatus,
                    isRefreshing = false, listProblem = failure.problem()) }
            }
        }
    }

    fun join(groupId: Long) {
        val state = uiState.value
        if (!ready() || state.listStatus != GroupListStatus.CONTENT ||
            state.showCreation || state.isRefreshing || state.listProblem != null || state.joiningGroupId != null || state.isCreating ||
            state.groups.find { it.id == groupId }?.joinAction != GroupJoinAction.APPLY) return
        val ticket = generation
        mutableState.update { it.copy(joiningGroupId = groupId, actionProblem = null, notice = null) }
        mutationJob = scope.launch {
            try {
                repository.join(groupId)
                ensureActive()
                if (!current(ticket)) return@launch
                mutableState.update { it.copy(joiningGroupId = null, notice = GroupNotice.JOINED,
                    groups = it.groups.map { group -> if (group.id == groupId) group.copy(membership = GroupMembership.PENDING) else group }) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (!current(ticket)) return@launch
                val problem = failure.problem()
                mutableState.update { it.copy(joiningGroupId = null, actionProblem = problem,
                    notice = if (problem.uncertain()) GroupNotice.JOIN_UNCERTAIN else null) }
                if (problem.uncertain() || problem in setOf(GroupProblem.CONFLICT, GroupProblem.FULL, GroupProblem.NOT_FOUND)) refresh()
            }
        }
    }

    fun openCreation() {
        if (!ready() || uiState.value.joiningGroupId != null) return
        mutableState.update { if (it.showCreation) it else it.copy(showCreation = true, form = GroupCreationForm(),
            formErrors = GroupFormErrors(), formProblem = null, creationUncertain = false) }
    }

    fun dismissCreation() {
        mutableState.update {
            if (it.isCreating) it else it.copy(showCreation = false, form = GroupCreationForm(),
                formErrors = GroupFormErrors(), formProblem = null, creationUncertain = false)
        }
    }

    fun updateForm(form: GroupCreationForm) {
        mutableState.update { if (it.showCreation && !it.isCreating) it.copy(form = form, formErrors = GroupFormErrors(),
            formProblem = if (it.creationUncertain) it.formProblem else null) else it }
    }

    fun create() {
        val state = uiState.value
        if (!ready() || !state.showCreation || state.isCreating ||
            state.creationUncertain || state.joiningGroupId != null) return
        val errors = state.form.errors()
        mutableState.update { it.copy(formErrors = errors) }
        if (errors.hasErrors) return
        loadJob?.cancel()
        loadJob = null
        val ticket = generation
        mutableState.update { it.copy(isCreating = true, isRefreshing = false, formProblem = null, notice = null, actionProblem = null) }
        mutationJob = scope.launch {
            try {
                repository.create(CreateGroup(state.form.name, state.form.description, state.form.maxMembers.toInt()))
                ensureActive()
                if (!current(ticket)) return@launch
                mutableState.update { it.copy(isCreating = false, showCreation = false, form = GroupCreationForm(),
                    formErrors = GroupFormErrors(), notice = GroupNotice.CREATED) }
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (!current(ticket)) return@launch
                val problem = failure.problem()
                mutableState.update { it.copy(isCreating = false, formProblem = problem, creationUncertain = problem.uncertain()) }
                if (problem.uncertain() || uiState.value.listStatus == GroupListStatus.LOADING) refresh()
            }
        }
    }

    fun reset() {
        generation++
        active = false
        ownerId = null
        loadJob?.cancel()
        mutationJob?.cancel()
        loadJob = null
        mutationJob = null
        mutableState.value = GroupManagementUiState()
    }
}

private fun Exception.problem() = (this as? GroupFailure)?.problem ?: GroupProblem.NETWORK
private fun GroupProblem.uncertain() = this in setOf(GroupProblem.NETWORK, GroupProblem.INVALID_RESPONSE, GroupProblem.SERVER)
