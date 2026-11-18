package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import com.example.ridemate_api_test.feature.invitation.domain.repository.InvitationRepository
import com.example.ridemate_api_test.feature.invitation.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class InvitationCreationViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "같은 이름"), true))
        override fun login(loginId: String, password: String) = error("No auth mutation")
        override fun retryConnection() = error("No new connection")
        override fun logout() = error("No auth mutation")
    }
    private class Repo : InvitationRepository {
        override suspend fun start(invitationId: String) = error("Not used")
        override suspend fun cancel(invitationId: String) = error("Not used")
        override fun dismissNotice() = Unit
        override fun respond(invitationId: String, accepted: Boolean) = error("Not used")
        override val state = MutableStateFlow(InvitationRepositoryState(userId = 7))
        var calls = 0
        var create: suspend (CreateInvitation) -> CreatedInvitation = { CreatedInvitation("created", it.groupId, it.inviteeIds.size) }
        override suspend fun create(request: CreateInvitation): CreatedInvitation { calls++; return create.invoke(request) }
    }
    private fun scenario(block: suspend (Auth, Repo, InvitationViewModel) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val auth = Auth()
            val repo = Repo()
            block(auth, repo, InvitationViewModel(auth, scope, repository = repo))
        } finally { scope.cancel() }
    }

    @Test fun createBlocksDoubleClicksAndNavigatesOnlyAfterConfirmedSuccess() = scenario { _, repo, model ->
        val gate = CompletableDeferred<Unit>()
        repo.create = { gate.await(); CreatedInvitation("created", it.groupId, it.inviteeIds.size) }
        model.create(1, setOf(2))
        model.create(1, setOf(2))
        assertTrue(model.uiState.value.creating)
        assertNull(model.uiState.value.createdInvitationId)
        assertEquals(1, repo.calls)
        gate.complete(Unit)
        yield()
        assertEquals("created", model.uiState.value.createdInvitationId)
        model.create(1, setOf(2))
        assertEquals(1, repo.calls)
        model.consumeNavigation("wrong")
        assertEquals("created", model.uiState.value.createdInvitationId)
        model.consumeNavigation("created")
        assertNull(model.uiState.value.createdInvitationId)
    }

    @Test fun unknownCreationResultNeverNavigatesOrAutomaticallyResends() = scenario { _, repo, model ->
        repo.create = { throw InvitationFailure(InvitationProblem.NETWORK) }
        model.create(1, setOf(2))
        model.clearCreationFailure()
        model.create(1, setOf(2))
        assertTrue(model.uiState.value.creationUncertain)
        assertNull(model.uiState.value.createdInvitationId)
        assertEquals(1, repo.calls)
    }

    @Test fun deterministicFailureKeepsSelectionFlowAndCanBeExplicitlyRetried() = scenario { _, repo, model ->
        repo.create = { throw InvitationFailure(InvitationProblem.USER_OFFLINE) }
        model.create(1, setOf(2))
        assertFalse(model.uiState.value.creationUncertain)
        assertEquals(InvitationProblem.USER_OFFLINE, model.uiState.value.creationProblem)
        assertNull(model.uiState.value.createdInvitationId)
        assertTrue(model.uiState.value.canCreate)
        model.clearCreationFailure()
        assertNull(model.uiState.value.creationProblem)
    }

    @Test fun forbiddenGroupDoesNotRemainSubmittable() = scenario { _, repo, model ->
        repo.create = { throw InvitationFailure(InvitationProblem.FORBIDDEN) }
        model.create(1, setOf(2))
        assertFalse(model.uiState.value.canCreate)
        assertNull(model.uiState.value.createdInvitationId)
    }

    @Test fun leaderIsRemovedByIdRegardlessOfOrderOrMatchingNicknamesAndCanStartUsesServerValue() = scenario { _, repo, model ->
        val participants = listOf(
            InvitationParticipant(2, "같은 이름", Participation.ACCEPTED),
            InvitationParticipant(4, "같은 이름", Participation.REJECTED),
            InvitationParticipant(7, "같은 이름", Participation.ACCEPTED),
            InvitationParticipant(3, "같은 이름", Participation.PENDING),
        )
        repo.state.value = InvitationRepositoryState(7, listOf(LeaderInvitation("real", status =
            InvitationStatus("real", participants, 2, true), fresh = true)))
        val leader = model.uiState.value.visiblePanel as InvitationPanel.Leader
        assertEquals(InvitationCounts(3, 1, 1, 1), leader.counts)
        assertTrue(leader.canStart)
        assertTrue(leader.fresh)
        model.hidePanel()
        repo.state.value = repo.state.value.copy(leaders = repo.state.value.leaders.map { it.copy(fresh = false) })
        assertNull(model.uiState.value.visiblePanel)
        model.reopen()
        assertFalse((model.uiState.value.visiblePanel as InvitationPanel.Leader).fresh)
    }

    @Test fun accountChangeDiscardsLateSuccessAndOldLeaderState() = scenario { auth, repo, model ->
        val gate = CompletableDeferred<Unit>()
        repo.create = { withContext(NonCancellable) { gate.await() }; CreatedInvitation("late", 1, 1) }
        model.create(1, setOf(2))
        auth.state.value = auth.state.value.copy(user = AuthUser(8, "새 사용자"))
        gate.complete(Unit)
        yield()
        assertEquals(InvitationUiState(), model.uiState.value)
        repo.state.value = InvitationRepositoryState(7, listOf(LeaderInvitation("old")))
        assertTrue(model.uiState.value.panels.isEmpty())
    }
}
