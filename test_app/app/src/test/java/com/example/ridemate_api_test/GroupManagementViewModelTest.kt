package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import com.example.ridemate_api_test.feature.group.presentation.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class GroupManagementViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true))
        override fun login(loginId: String, password: String) = Unit
        override fun retryConnection() = Unit
        override fun logout() { state.value = AuthState(phase = AuthPhase.SIGNED_OUT) }
    }
    private class Repository : GroupRepository {
        var list = listOf(Group(1, "그룹", "", 1, 2, Membership.NONE))
        var listCalls = 0; var joins = 0; var creates = 0
        var loadGate: CompletableDeferred<Unit>? = null
        var actionGate: CompletableDeferred<Unit>? = null
        var listProblem: GroupProblem? = null
        var actionProblem: GroupProblem? = null
        override suspend fun groups(): List<Group> {
            listCalls++
            loadGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            listProblem?.let { throw GroupFailure(it) }
            return list
        }
        override suspend fun join(groupId: Long) {
            joins++; actionGate?.await()
            actionProblem?.let { throw GroupFailure(it) }
            list = list.map { if (it.id == groupId) it.copy(membership = Membership.PENDING) else it }
        }
        override suspend fun create(request: CreateGroup): CreatedGroup {
            creates++; actionGate?.await()
            actionProblem?.let { throw GroupFailure(it) }
            return CreatedGroup(2, request.name)
        }
    }
    private class Fixture(scope: CoroutineScope) {
        val auth = Auth(); val repo = Repository()
        val model = GroupManagementViewModel(repo, auth, scope)
        suspend fun start() {
            model.enter()
            await { model.uiState.value.listStatus == GroupListStatus.CONTENT }
        }
    }
    private fun scenario(block: suspend Fixture.() -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                try { withTimeout(5000) { Fixture(scope).block() } } finally { scope.cancel() }
            }
        }
    }

    @Test fun enteringAndRefreshingDeduplicateAndPreserveExistingListOnFailure() = scenario {
        repo.loadGate = CompletableDeferred()
        model.enter(); model.enter(); model.refresh()
        await { repo.listCalls == 1 }
        repo.loadGate!!.complete(Unit)
        await { model.uiState.value.listStatus == GroupListStatus.CONTENT }
        repo.listProblem = GroupProblem.NETWORK
        model.refresh()
        await { model.uiState.value.listProblem != null }
        assertEquals(1, model.uiState.value.groups.size)
        model.join(1)
        assertEquals(0, repo.joins)
        assertFalse(model.uiState.value.isRefreshing)
    }

    @Test fun emptyAndInitialErrorAreDifferentAndRetryLoadsFreshState() = scenario {
        repo.listProblem = GroupProblem.FORBIDDEN
        model.enter()
        await { model.uiState.value.listStatus == GroupListStatus.ERROR }
        assertEquals(GroupProblem.FORBIDDEN, model.uiState.value.listProblem)
        repo.listProblem = null; repo.list = emptyList()
        model.refresh()
        await { model.uiState.value.listStatus == GroupListStatus.CONTENT }
        assertTrue(model.uiState.value.groups.isEmpty())
    }

    @Test fun joinIsSentOnceAndSuccessSurvivesRefreshFailure() = scenario {
        start()
        repo.actionGate = CompletableDeferred()
        model.join(1); model.join(1); model.openCreation()
        await { repo.joins == 1 }
        assertFalse(model.uiState.value.showCreation)
        repo.listProblem = GroupProblem.NETWORK
        repo.actionGate!!.complete(Unit)
        await { model.uiState.value.listProblem != null }
        assertEquals(GroupNotice.JOINED, model.uiState.value.notice)
        assertEquals(GroupMembership.PENDING, model.uiState.value.groups.single().membership)
    }

    @Test fun joinConflictAndUncertainFailureRefreshWithoutRepeatingPost() = scenario {
        start()
        repo.actionProblem = GroupProblem.CONFLICT
        model.join(1)
        await { repo.listCalls == 2 && !model.uiState.value.isRefreshing }
        assertEquals(GroupProblem.CONFLICT, model.uiState.value.actionProblem)
        repo.actionProblem = GroupProblem.NETWORK
        model.join(1)
        await { repo.listCalls == 3 && !model.uiState.value.isRefreshing }
        assertEquals(2, repo.joins)
        assertEquals(GroupNotice.JOIN_UNCERTAIN, model.uiState.value.notice)
    }

    @Test fun capacityAndMembershipBlockRequestsButRejectedUsersCanReapply() = scenario {
        repo.list = listOf(
            Group(1, "가득", "", 2, 2, Membership.NONE),
            Group(2, "가입", "", 1, 2, Membership.APPROVED),
            Group(3, "대기", "", 1, 2, Membership.PENDING),
            Group(4, "거절", "", 1, 2, Membership.REJECTED))
        start()
        (1L..3L).forEach { model.join(it) }
        assertEquals(0, repo.joins)
        model.join(4)
        await { repo.joins == 1 && model.uiState.value.joiningGroupId == null }
    }

    @Test fun creationValidatesAndLocksDraftThenPreservesSuccessWhenRefreshFails() = scenario {
        start(); model.openCreation(); model.create()
        assertTrue(model.uiState.value.formErrors.hasErrors)
        assertEquals(0, repo.creates)
        val draft = GroupCreationForm("내 그룹", "설명", "12")
        model.updateForm(draft); model.openCreation()
        assertEquals(draft, model.uiState.value.form)
        repo.actionGate = CompletableDeferred()
        model.create(); model.create(); model.dismissCreation(); model.updateForm(GroupCreationForm())
        await { repo.creates == 1 }
        assertTrue(model.uiState.value.showCreation)
        assertEquals(draft, model.uiState.value.form)
        repo.listProblem = GroupProblem.NETWORK
        repo.actionGate!!.complete(Unit)
        await { model.uiState.value.listProblem != null }
        assertFalse(model.uiState.value.showCreation)
        assertEquals(GroupNotice.CREATED, model.uiState.value.notice)
    }

    @Test fun uncertainCreationPreservesDraftAndRequiresClosingBeforeAnotherSubmission() = scenario {
        start(); model.openCreation()
        val draft = GroupCreationForm("그룹", "", "2")
        model.updateForm(draft)
        repo.actionProblem = GroupProblem.NETWORK
        model.create()
        await { model.uiState.value.creationUncertain }
        model.create(); model.updateForm(draft.copy(name = "변경")); model.create()
        assertEquals(1, repo.creates)
        assertTrue(model.uiState.value.showCreation)
        assertEquals("변경", model.uiState.value.form.name)
        model.dismissCreation(); model.openCreation()
        assertEquals(GroupCreationForm(), model.uiState.value.form)
        assertFalse(model.uiState.value.creationUncertain)
    }

    @Test fun creationFailureDuringInitialLoadingRestartsTheInterruptedListRequest() = scenario {
        repo.loadGate = CompletableDeferred()
        model.enter()
        await { repo.listCalls == 1 }
        model.openCreation(); model.updateForm(GroupCreationForm("그룹", "", "2"))
        repo.actionProblem = GroupProblem.INVALID_REQUEST
        model.create()
        await { repo.listCalls == 2 }
        repo.loadGate!!.complete(Unit)
        await { model.uiState.value.listStatus == GroupListStatus.CONTENT }
        assertEquals(GroupProblem.INVALID_REQUEST, model.uiState.value.formProblem)
    }

    @Test fun ordinaryCreationFailureKeepsInputAndCancelDiscardsIt() = scenario {
        start(); model.openCreation()
        val draft = GroupCreationForm("그룹", "설명", "2")
        model.updateForm(draft); repo.actionProblem = GroupProblem.INVALID_REQUEST
        model.create()
        await { model.uiState.value.formProblem != null }
        assertEquals(draft, model.uiState.value.form)
        assertFalse(model.uiState.value.creationUncertain)
        model.dismissCreation()
        assertEquals(GroupCreationForm(), model.uiState.value.form)
    }

    @Test fun accountChangeAndLeavingIgnoreEvenUncooperativeLateResults() = scenario {
        start()
        repo.loadGate = CompletableDeferred()
        model.refresh()
        await { repo.listCalls == 2 }
        auth.state.value = auth.state.value.copy(user = AuthUser(8, "다른 사용자"))
        await { model.uiState.value.groups.isEmpty() }
        repo.loadGate!!.complete(Unit)
        yield(); yield()
        assertEquals(GroupManagementUiState(), model.uiState.value)
        repo.loadGate = null; model.enter()
        await { model.uiState.value.groups.isNotEmpty() }
        model.openCreation(); model.updateForm(GroupCreationForm("남은 폼", "", "2"))
        auth.logout()
        await { !model.uiState.value.showCreation }
        assertEquals(GroupCreationForm(), model.uiState.value.form)
        model.enter(); model.refresh(); model.join(1); model.openCreation()
        assertFalse(model.uiState.value.showCreation)
    }

    companion object {
        private suspend fun await(condition: () -> Boolean) {
            withTimeout(3000) { while (!condition()) delay(1) }
        }
    }
}
