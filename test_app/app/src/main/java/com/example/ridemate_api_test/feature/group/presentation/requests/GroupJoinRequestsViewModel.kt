package com.example.ridemate_api_test.feature.group.presentation.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GroupJoinRequestsViewModel(
    private val repository: GroupRepository,
    private val auth: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GroupJoinRequestsUiState())
    val uiState = mutableState.asStateFlow()
    private val scope get() = testScope ?: viewModelScope
    private var ownerId: Long? = null
    private var sessionGeneration = 0
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private fun current(ticket: Int) = ticket == sessionGeneration && auth.state.value.enteredSession &&
        ownerId != null && ownerId == auth.state.value.user?.id

    init {
        scope.launch {
            auth.state.collect { state ->
                if (!state.enteredSession || (ownerId != null && ownerId != state.user?.id)) reset()
            }
        }
    }

    fun open() {
        if (uiState.value.visible || !auth.state.value.enteredSession) return
        ownerId = auth.state.value.user?.id
        mutableState.update {
            val settled = it.processingId == null && !it.needsVerification
            it.copy(visible = true, actionProblem = if (settled) null else it.actionProblem,
                notice = if (settled) null else it.notice)
        }
        // A sent POST outlives dismissal. Its completion performs the fresh GET if reopened.
        if (uiState.value.processingId == null) refresh()
    }

    fun close() {
        invalidateLoad()
        mutableState.update { it.copy(visible = false, refreshing = false) }
    }

    fun refresh() {
        if (!current(sessionGeneration) || !uiState.value.visible ||
            uiState.value.processingId != null || loadJob?.isActive == true) return
        val sessionTicket = sessionGeneration
        val loadTicket = ++loadGeneration
        val hadContent = uiState.value.status in setOf(GroupJoinRequestsStatus.CONTENT, GroupJoinRequestsStatus.EMPTY)
        mutableState.update { it.copy(status = if (hadContent) it.status else GroupJoinRequestsStatus.LOADING,
            refreshing = true, listProblem = null) }
        loadJob = scope.launch {
            try {
                val requests = repository.joinRequests()
                ensureActive()
                if (!current(sessionTicket) || loadTicket != loadGeneration || !uiState.value.visible) return@launch
                mutableState.update { it.copy(requests = requests,
                    status = if (requests.isEmpty()) GroupJoinRequestsStatus.EMPTY else GroupJoinRequestsStatus.CONTENT,
                    refreshing = false, listProblem = null, needsVerification = false, fullGroupIds = emptySet()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (!current(sessionTicket) || loadTicket != loadGeneration || !uiState.value.visible) return@launch
                val problem = failure.problem()
                mutableState.update {
                    if (problem == GroupProblem.FORBIDDEN) it.copy(status = GroupJoinRequestsStatus.NON_LEADER,
                        requests = emptyList(), refreshing = false, listProblem = null)
                    else it.copy(status = if (hadContent) it.status else GroupJoinRequestsStatus.ERROR,
                        refreshing = false, listProblem = problem)
                }
            }
        }
    }

    fun respond(id: Long, decision: GroupRequestDecision) {
        val state = uiState.value
        if (!current(sessionGeneration) || !state.canRespond) return
        val request = state.requests.find { it.id == id } ?: return
        if (decision == GroupRequestDecision.APPROVE && request.groupId in state.fullGroupIds) return
        invalidateLoad()
        val ticket = sessionGeneration
        mutableState.update { it.copy(processingId = id, actionProblem = null, notice = null) }
        mutationJob = scope.launch {
            try {
                val result = repository.respondToJoinRequest(request.groupId, request.userId, decision)
                ensureActive()
                if (!current(ticket)) return@launch
                if (result.groupId != request.groupId || result.userId != request.userId || result.status != decision.status)
                    throw GroupFailure(GroupProblem.INVALID_RESPONSE)
                mutableState.update {
                    val remaining = it.requests.filterNot { item -> item.id == id }
                    it.copy(processingId = null, requests = remaining,
                        status = if (remaining.isEmpty()) GroupJoinRequestsStatus.EMPTY else GroupJoinRequestsStatus.CONTENT,
                        needsVerification = true,
                        notice = if (decision == GroupRequestDecision.APPROVE) GroupRequestsNotice.APPROVED else GroupRequestsNotice.REJECTED)
                }
                if (uiState.value.visible) refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (!current(ticket)) return@launch
                val problem = failure.problem()
                val uncertain = problem in setOf(GroupProblem.NETWORK, GroupProblem.SERVER, GroupProblem.INVALID_RESPONSE)
                val changed = problem in setOf(GroupProblem.NOT_FOUND, GroupProblem.CONFLICT)
                val verify = uncertain || changed || problem == GroupProblem.FORBIDDEN
                mutableState.update { it.copy(processingId = null, actionProblem = problem, needsVerification = verify,
                    notice = when { uncertain -> GroupRequestsNotice.UNCERTAIN; changed -> GroupRequestsNotice.CHANGED; else -> null },
                    fullGroupIds = if (problem == GroupProblem.FULL) it.fullGroupIds + request.groupId else it.fullGroupIds) }
                if (verify && uiState.value.visible) refresh()
            }
        }
    }

    private fun invalidateLoad() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
    }

    private fun reset() {
        sessionGeneration++
        invalidateLoad()
        mutationJob?.cancel()
        mutationJob = null
        ownerId = null
        mutableState.value = GroupJoinRequestsUiState()
    }
}

private fun Exception.problem() = (this as? GroupFailure)?.problem ?: GroupProblem.NETWORK
