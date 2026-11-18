package com.example.ridemate_api_test.feature.invitation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import com.example.ridemate_api_test.feature.invitation.domain.repository.InvitationRepository

/** Authenticated-area owner for creation, status and member response presentation. */
class InvitationViewModel(
    private val auth: AuthRepository,
    testScope: CoroutineScope? = null,
    initialState: InvitationUiState = InvitationUiState(),
    private val repository: InvitationRepository? = null,
) : ViewModel() {
    // The application factory always uses empty state; fixtures are confined to tests/Preview.
    private val mutableState = MutableStateFlow(
        if (auth.state.value.enteredSession) initialState else InvitationUiState(),
    )
    val uiState = mutableState.asStateFlow()
    private var ownerId = auth.state.value.user?.id
    private val scope = testScope ?: viewModelScope
    private var createJob: Job? = null
    private val actionJobs = mutableSetOf<Job>()
    private var generation = 0L

    init {
        scope.launch {
            auth.state.collect { current ->
                if (!current.enteredSession || current.user?.id != ownerId) {
                    generation++
                    createJob?.cancel()
                    actionJobs.toList().forEach { it.cancel() }
                    actionJobs.clear()
                    mutableState.value = InvitationUiState()
                }
                ownerId = current.user?.id
            }
        }
        repository?.let { repo ->
            scope.launch {
                repo.state.collect { result ->
                    val current = auth.state.value
                    if (!current.enteredSession || current.user?.id != result.userId || current.user?.id != ownerId) return@collect
                    val leaderId = current.user?.id ?: return@collect
                    val leaders = result.leaders.map { leader ->
                        val members = leader.status?.participants?.filter { it.userId != leaderId }
                        val counts = members?.let { list -> InvitationCounts(list.size,
                            list.count { it.status == Participation.ACCEPTED }, list.count { it.status == Participation.PENDING },
                            list.count { it.status == Participation.REJECTED }) }
                        InvitationPanel.Leader(leader.invitationId, counts, canStart = leader.status?.canStart == true,
                            busy = leader.busy, statusConnected = true, fresh = leader.fresh, socketError = result.socketError,
                            actionsConnected = result.connected,
                            actionProblem = leader.actionProblem, actionUncertain = leader.actionUncertain,
                            startContextKnown = leader.groupId != null)
                    }
                    val blocked = result.members.any { it.response in setOf(MemberResponse.SENDING,
                        MemberResponse.ACCEPT_SENT, MemberResponse.REJECT_SENT, MemberResponse.UNCERTAIN) }
                    val members = result.members.map { member -> InvitationPanel.Member(
                        member.invitation.invitationId, member.invitation.groupName, member.invitation.leaderNickname,
                        InvitationResponseUi.valueOf(member.response.name), member.choice, member.fresh, blocked,
                        result.socketErrorCode,
                    ) }
                    mutableState.update { previous ->
                        val incoming = leaders + members
                        val byId = incoming.associateBy { it.invitationId }
                        val kept = previous.panels.mapNotNull { byId[it.invitationId] }
                        previous.copy(panels = kept + incoming.filter { next -> kept.none { it.invitationId == next.invitationId } },
                            hiddenIds = previous.hiddenIds.intersect(byId.keys),
                            ride = result.ride, rideOpen = result.ride != null &&
                                (previous.ride?.rideSessionId != result.ride.rideSessionId || previous.rideOpen),
                            notice = result.notice)
                    }
                }
            }
        }
    }

    private fun update(change: (InvitationUiState) -> InvitationUiState) {
        val current = auth.state.value
        if (!current.enteredSession || current.user?.id == null || current.user.id != ownerId) return
        mutableState.update(change)
    }

    fun hidePanel() = update { it.hidePanel() }
    fun reopen() = update { it.reopen() }
    fun openRide() = update { it.openRide() }
    fun backHome() = update { it.backHome() }
    fun respond(invitationId: String, accepted: Boolean) {
        val current = auth.state.value
        if (!current.enteredSession || current.user?.id != ownerId ||
            uiState.value.notice != null || uiState.value.visiblePanel?.invitationId != invitationId) return
        repository?.respond(invitationId, accepted)
    }
    fun dismissNotice() { repository?.dismissNotice() }
    fun start(invitationId: String) = action(invitationId, start = true)
    fun cancel(invitationId: String) = action(invitationId, start = false)
    private fun action(invitationId: String, start: Boolean) {
        val repo = repository ?: return
        val current = auth.state.value
        if (!current.enteredSession || current.user?.id != ownerId ||
            uiState.value.notice != null || uiState.value.visiblePanel?.invitationId != invitationId) return
        val job = scope.launch { if (start) repo.start(invitationId) else repo.cancel(invitationId) }
        actionJobs += job
        job.invokeOnCompletion { actionJobs -= job }
    }

    fun create(groupId: Long, userIds: Set<Long>) {
        val repo = repository ?: return
        val current = auth.state.value
        if (!current.enteredSession || current.user?.id != ownerId || !uiState.value.canCreate) return
        val request = try { CreateInvitation(groupId, userIds.toSet()) }
            catch (_: IllegalArgumentException) { return }
        val ticket = generation
        mutableState.update { it.copy(creating = true, creationProblem = null) }
        createJob = scope.launch {
            try {
                val created = repo.create(request)
                currentCoroutineContext().ensureActive()
                if (ticket != generation || auth.state.value.user?.id != current.user?.id || !auth.state.value.enteredSession) return@launch
                mutableState.update { it.copy(creating = false, createdInvitationId = created.invitationId,
                    hiddenIds = it.hiddenIds - created.invitationId) }
            } catch (cancelled: CancellationException) {
                if (ticket == generation) mutableState.update { it.copy(creating = false, creationUncertain = true,
                    creationProblem = InvitationProblem.NETWORK) }
                throw cancelled
            } catch (failure: Exception) {
                if (ticket != generation || !auth.state.value.enteredSession || auth.state.value.user?.id != current.user?.id) return@launch
                val problem = (failure as? InvitationFailure)?.problem ?: InvitationProblem.INVALID_RESPONSE
                mutableState.update { it.copy(creating = false, creationProblem = problem,
                    creationUncertain = problem.resultUncertain || problem == InvitationProblem.BUSY) }
            }
        }
    }

    fun consumeNavigation(invitationId: String) = update {
        if (it.createdInvitationId == invitationId) it.copy(createdInvitationId = null) else it
    }

    fun clearCreationFailure() = update {
        if (!it.creating && !it.creationUncertain) it.copy(creationProblem = null) else it
    }
}
