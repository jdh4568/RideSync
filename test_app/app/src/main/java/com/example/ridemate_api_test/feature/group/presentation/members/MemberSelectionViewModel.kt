package com.example.ridemate_api_test.feature.group.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.model.AuthPhase
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemberSelectionViewModel(
    private val repository: GroupRepository,
    private val auth: AuthRepository,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemberSelectionUiState())
    val uiState = mutableState.asStateFlow()
    private var active = false
    private var userId: Long? = null
    private var generation = 0L
    private var loadJob: Job? = null
    private val scope get() = testScope ?: viewModelScope

    init {
        scope.launch {
            auth.state.collect { state ->
                if (!state.enteredSession || (active && userId != state.user?.id)) reset()
                else if (active) {
                    val connected = state.phase == AuthPhase.CONNECTED
                    if (connected != uiState.value.connected) {
                        if (!connected) {
                            cancelLoad()
                            mutableState.update { it.copy(connected = false, snapshotConfirmed = false,
                                status = if (it.problem != null) MemberSelectionStatus.ERROR
                                    else if (it.hasSnapshot) MemberSelectionStatus.CONTENT else MemberSelectionStatus.WAITING_CONNECTION,
                                showDeveloping = false, selectionLimitReached = false) }
                        } else {
                            mutableState.update { it.copy(connected = true) }
                            // One query per observed recovery, never per repeated state emission.
                            if (uiState.value.problem !in setOf(GroupProblem.FORBIDDEN, GroupProblem.NOT_FOUND)) load()
                        }
                    }
                }
            }
        }
    }

    fun enter(group: Group) {
        val state = auth.state.value
        if (!state.enteredSession || state.user == null || group.id <= 0) return
        if (active && userId == state.user.id && uiState.value.group?.id == group.id) return
        reset()
        userId = state.user.id
        active = true
        val connected = state.phase == AuthPhase.CONNECTED
        mutableState.value = MemberSelectionUiState(group = group, connected = connected,
            status = if (connected) MemberSelectionStatus.LOADING else MemberSelectionStatus.WAITING_CONNECTION)
        if (connected) load()
    }

    fun refresh() { if (uiState.value.canRefresh) load() }

    private fun ready() = active && auth.state.value.enteredSession && auth.state.value.user?.id == userId
    private fun connected() = ready() && auth.state.value.phase == AuthPhase.CONNECTED

    private fun load() {
        val group = uiState.value.group ?: return
        if (!connected() || loadJob?.isActive == true) return
        val ticket = ++generation
        val expectedUser = userId
        mutableState.update { it.copy(status = MemberSelectionStatus.LOADING, snapshotConfirmed = false,
            problem = null, selectionLimitReached = false, selectionChanged = false) }
        loadJob = scope.launch {
            try {
                val result = repository.members(group.id)
                ensureActive()
                if (ticket != generation || !connected() || uiState.value.group?.id != group.id) return@launch
                if (result.groupId != group.id) throw GroupFailure(GroupProblem.INVALID_RESPONSE)
                if (result.members.singleOrNull { it.userId == expectedUser }?.role != GroupRole.OWNER)
                    throw GroupFailure(GroupProblem.FORBIDDEN)
                val choices = result.members.filter { it.role != GroupRole.OWNER && it.userId != expectedUser }
                    .map { MemberChoiceUi(it.userId, it.nickname, it.online) }
                val availableIds = choices.filter { it.online }.map { it.userId }.toSet()
                mutableState.update { previous ->
                    val selected = previous.selectedUserIds.intersect(availableIds)
                    previous.copy(group = group.copy(name = result.groupName, description = result.description,
                        memberCount = result.memberCount), status = MemberSelectionStatus.CONTENT,
                        members = choices, selectedUserIds = selected, connected = true, snapshotConfirmed = true,
                        hasSnapshot = true, problem = null, selectionChanged = selected != previous.selectedUserIds)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ensureActive()
                if (ticket == generation && connected() && uiState.value.group?.id == group.id) {
                    val problem = (failure as? GroupFailure)?.problem ?: GroupProblem.NETWORK
                    val denied = problem in setOf(GroupProblem.FORBIDDEN, GroupProblem.NOT_FOUND, GroupProblem.UNAUTHORIZED)
                    mutableState.update { it.copy(status = MemberSelectionStatus.ERROR, snapshotConfirmed = false,
                        problem = problem, members = if (denied) emptyList() else it.members,
                        selectedUserIds = if (denied) emptySet() else it.selectedUserIds,
                        hasSnapshot = !denied && it.hasSnapshot, showDeveloping = false) }
                }
            }
        }
    }

    fun toggle(userId: Long) { if (connected()) mutableState.update { it.toggle(userId) } }
    fun complete() { if (connected()) mutableState.update { it.complete() } }
    fun invitationSelection(): Pair<Long, Set<Long>>? {
        val current = uiState.value
        if (!connected() || !current.canComplete) return null
        return (current.group?.id ?: return null) to current.selectedUserIds.toSet()
    }
    fun dismissDeveloping() { mutableState.update { it.copy(showDeveloping = false) } }

    private fun cancelLoad() {
        generation++
        loadJob?.cancel()
        loadJob = null
    }

    fun reset() {
        cancelLoad()
        active = false
        userId = null
        mutableState.value = MemberSelectionUiState()
    }
}
