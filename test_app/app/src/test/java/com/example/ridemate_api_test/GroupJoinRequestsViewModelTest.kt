package com.example.ridemate_api_test

import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository
import com.example.ridemate_api_test.feature.group.presentation.requests.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class GroupJoinRequestsViewModelTest {
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true))
        override fun login(loginId: String, password: String) = error("Unexpected login")
        override fun retryConnection() = error("Unexpected socket connection")
        override fun logout() { state.value = AuthState(phase = AuthPhase.SIGNED_OUT) }
    }
    private class Repository : GroupRepository {
        var list = listOf(GroupJoinRequest(15, 1, 5, "신청자", "그룹"))
        var loads = 0
        val decisions = mutableListOf<Triple<Long, Long, GroupRequestDecision>>()
        var loadGate: CompletableDeferred<Unit>? = null
        var actionGate: CompletableDeferred<Unit>? = null
        var listProblem: GroupProblem? = null
        var actionProblem: GroupProblem? = null
        var result: GroupRequestResult? = null
        override suspend fun groups(): List<Group> = error("Unexpected groups")
        override suspend fun join(groupId: Long) = error("Unexpected join")
        override suspend fun create(request: CreateGroup): CreatedGroup = error("Unexpected create")
        override suspend fun joinRequests(): List<GroupJoinRequest> {
            loads++
            val snapshot = list; val problem = listProblem; val gate = loadGate
            gate?.let { withContext(NonCancellable) { it.await() } }
            problem?.let { throw GroupFailure(it) }
            return snapshot
        }
        override suspend fun respondToJoinRequest(groupId: Long, userId: Long, decision: GroupRequestDecision): GroupRequestResult {
            decisions += Triple(groupId, userId, decision)
            actionGate?.let { withContext(NonCancellable) { it.await() } }
            actionProblem?.let { throw GroupFailure(it) }
            list = list.filterNot { it.groupId == groupId && it.userId == userId }
            return result ?: GroupRequestResult(groupId, userId, decision.status)
        }
    }
    private class Fixture(scope: CoroutineScope) {
        val auth = Auth(); val repo = Repository()
        val model = GroupJoinRequestsViewModel(repo, auth, scope)
        val state get() = model.uiState.value
        suspend fun start() { model.open(); await { state.status == GroupJoinRequestsStatus.CONTENT && !state.refreshing } }
        fun respond(decision: GroupRequestDecision = GroupRequestDecision.APPROVE) = model.respond(15, decision)
    }
    private fun scenario(block: suspend Fixture.() -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                val fixture = Fixture(scope)
                try { withTimeout(5000) { fixture.block() } }
                finally {
                    fixture.repo.loadGate?.complete(Unit); fixture.repo.actionGate?.complete(Unit); scope.cancel()
                }
            }
        }
    }

    @Test fun openDeduplicatesAndClosedConstructionDoesNotFetch() = scenario {
        yield(); assertEquals(0, repo.loads)
        repo.loadGate = CompletableDeferred()
        repeat(3) { model.open() }; model.refresh()
        await { repo.loads == 1 }
        repo.loadGate!!.complete(Unit)
        await { !state.refreshing }
        assertTrue(state.canRespond)
        model.close(); model.open()
        await { repo.loads == 2 && !state.refreshing }
    }

    @Test fun emptyAndNonLeaderAreDifferentStates() = scenario {
        repo.list = emptyList(); model.open()
        await { state.status == GroupJoinRequestsStatus.EMPTY }
        model.close(); repo.listProblem = GroupProblem.FORBIDDEN; model.open()
        await { state.status == GroupJoinRequestsStatus.NON_LEADER }
        assertNull(state.listProblem); assertFalse(state.canRespond)
    }

    @Test fun initialFailuresNeverBecomeEmptyAndRetryRecovers() = scenario {
        for (problem in listOf(GroupProblem.NETWORK, GroupProblem.SERVER, GroupProblem.INVALID_RESPONSE, GroupProblem.UNAUTHORIZED)) {
            repo.listProblem = problem; model.open()
            await { state.status == GroupJoinRequestsStatus.ERROR }
            assertEquals(problem, state.listProblem)
            assertFalse(state.canRespond); model.close()
        }
        repo.listProblem = null; model.open()
        await { state.status == GroupJoinRequestsStatus.CONTENT }
    }

    @Test fun successRemovesOnlyConfirmedItemAndKeepsEmptyPopupAfterRefreshFailure() = scenario {
        start(); repo.listProblem = GroupProblem.NETWORK
        respond(); await { state.listProblem != null }
        assertTrue(state.visible); assertTrue(state.requests.isEmpty())
        assertEquals(GroupJoinRequestsStatus.EMPTY, state.status)
        assertEquals(GroupRequestsNotice.APPROVED, state.notice)
        assertTrue(state.needsVerification)
        assertEquals(listOf(Triple(1L, 5L, GroupRequestDecision.APPROVE)), repo.decisions)
    }

    @Test fun rejectionAllowsReapplicationEvenWithSameRecordId() = scenario {
        start(); val original = repo.list
        respond(GroupRequestDecision.REJECT)
        await { state.notice == GroupRequestsNotice.REJECTED && !state.refreshing }
        assertTrue(state.requests.isEmpty())
        repo.list = original; model.close(); model.open()
        await { state.canRespond }
        respond(GroupRequestDecision.REJECT)
        await { repo.decisions.size == 2 && !state.refreshing && state.processingId == null }
    }

    @Test fun fullGroupKeepsRequestDisablesApprovalAndStillAllowsRejection() = scenario {
        start(); repo.actionProblem = GroupProblem.FULL
        respond(); await { state.actionProblem == GroupProblem.FULL }
        assertEquals(1, state.requests.size); assertTrue(state.canRespond)
        assertTrue(1L in state.fullGroupIds)
        respond(); assertEquals(1, repo.decisions.size)
        repo.actionProblem = null; respond(GroupRequestDecision.REJECT)
        await { state.notice == GroupRequestsNotice.REJECTED && !state.refreshing }
        assertEquals(2, repo.decisions.size)
    }

    @Test fun freshOpenClearsOldFullErrorAndPermitsApprovalAfterCapacityChanges() = scenario {
        start(); repo.actionProblem = GroupProblem.FULL
        respond(); await { state.actionProblem == GroupProblem.FULL }
        model.close(); repo.actionProblem = null; model.open()
        await { !state.refreshing }
        assertNull(state.actionProblem); assertTrue(state.fullGroupIds.isEmpty())
        respond(); await { state.notice == GroupRequestsNotice.APPROVED && !state.refreshing }
        assertEquals(2, repo.decisions.size)
    }

    @Test fun duplicatesOppositeActionAndOtherRowsAreBlockedDuringPost() = scenario {
        repo.list = repo.list + GroupJoinRequest(18, 3, 8, "다른 신청자", "다른 그룹")
        start(); repo.actionGate = CompletableDeferred()
        respond(); respond(); respond(GroupRequestDecision.REJECT); model.respond(18, GroupRequestDecision.REJECT)
        await { repo.decisions.size == 1 }
        assertEquals(15L, state.processingId)
        repo.actionGate!!.complete(Unit)
        await { state.processingId == null && !state.refreshing }
        assertEquals(listOf(18L), state.requests.map { it.id })
    }

    @Test fun mutationForbiddenIsPermissionErrorNotNonLeaderOrSuccess() = scenario {
        start(); repo.actionProblem = GroupProblem.FORBIDDEN
        respond(); await { state.actionProblem == GroupProblem.FORBIDDEN && !state.refreshing }
        assertEquals(GroupJoinRequestsStatus.CONTENT, state.status)
        assertNull(state.notice); assertEquals(1, state.requests.size)
        assertEquals(2, repo.loads)
    }

    @Test fun conflictsAndMissingRequestsReconcileWithoutClaimingSuccess() = scenario {
        for (problem in listOf(GroupProblem.CONFLICT, GroupProblem.NOT_FOUND)) {
            repo.list = listOf(GroupJoinRequest(15, 1, 5, "신청자", "그룹"))
            model.close(); start(); repo.actionProblem = problem
            repo.actionGate = CompletableDeferred(); respond()
            await { state.processingId != null }
            repo.list = emptyList(); repo.actionGate!!.complete(Unit)
            await { state.notice == GroupRequestsNotice.CHANGED && !state.refreshing }
            assertEquals(GroupJoinRequestsStatus.EMPTY, state.status)
        }
    }

    @Test fun uncertainPostIsNeverResentAndRemainsBlockedUntilSuccessfulGet() = scenario {
        for (problem in listOf(GroupProblem.NETWORK, GroupProblem.SERVER, GroupProblem.INVALID_RESPONSE)) {
            model.close(); repo.listProblem = null
            repo.list = listOf(GroupJoinRequest(15, 1, 5, "신청자", "그룹")); start()
            repo.listProblem = GroupProblem.NETWORK; repo.actionProblem = problem
            val before = repo.decisions.size
            respond(); await { state.listProblem != null }
            assertEquals(GroupRequestsNotice.UNCERTAIN, state.notice)
            assertTrue(state.needsVerification); assertEquals(1, state.requests.size)
            respond(); assertEquals(before + 1, repo.decisions.size)
            repo.listProblem = null; repo.list = emptyList(); model.refresh()
            await { !state.refreshing && state.status == GroupJoinRequestsStatus.EMPTY }
            assertEquals(GroupRequestsNotice.UNCERTAIN, state.notice)
            assertFalse(state.needsVerification)
        }
    }

    @Test fun wrongSuccessTargetIsUncertainAndDoesNotRemoveItemBeforeVerification() = scenario {
        start(); repo.result = GroupRequestResult(99, 5, Membership.APPROVED); repo.listProblem = GroupProblem.NETWORK
        respond(); await { state.listProblem != null }
        assertEquals(GroupRequestsNotice.UNCERTAIN, state.notice)
        assertEquals(GroupProblem.INVALID_RESPONSE, state.actionProblem)
        assertEquals(1, state.requests.size)
    }

    @Test fun closeDuringPostDoesNotCancelOrFetchInBackground() = scenario {
        start(); repo.actionGate = CompletableDeferred()
        respond(); await { repo.decisions.size == 1 }
        model.close(); assertFalse(state.visible)
        repo.actionGate!!.complete(Unit)
        await { state.processingId == null }
        assertEquals(1, repo.loads); assertEquals(GroupRequestsNotice.APPROVED, state.notice)
        model.open(); await { repo.loads == 2 && !state.refreshing }
        assertTrue(state.visible); assertEquals(GroupJoinRequestsStatus.EMPTY, state.status)
    }

    @Test fun reopenDuringPostRetainsProgressAndWaitsForCompletionBeforeFreshGet() = scenario {
        start(); repo.actionGate = CompletableDeferred()
        respond(); await { repo.decisions.size == 1 }
        model.close(); model.open(); model.open(); respond(GroupRequestDecision.REJECT); model.refresh()
        assertEquals(15L, state.processingId); assertEquals(1, repo.loads)
        repo.actionGate!!.complete(Unit)
        await { repo.loads == 2 && !state.refreshing }
        assertEquals(1, repo.decisions.size); assertEquals(GroupJoinRequestsStatus.EMPTY, state.status)
    }

    @Test fun cancelledOldGetCannotOverwriteReopenedFreshList() = scenario {
        repo.loadGate = CompletableDeferred(); val oldGate = repo.loadGate!!
        model.open(); await { repo.loads == 1 }
        model.close(); repo.loadGate = null; repo.list = emptyList(); model.open()
        await { state.status == GroupJoinRequestsStatus.EMPTY }
        oldGate.complete(Unit); delay(20)
        assertTrue(state.requests.isEmpty()); assertEquals(GroupJoinRequestsStatus.EMPTY, state.status)
    }

    @Test fun logoutAndUserChangeDiscardLateMutationReplies() = scenario {
        for (logout in listOf(true, false)) {
            repo.list = listOf(GroupJoinRequest(15, 1, 5, "신청자", "그룹")); start()
            repo.actionGate = CompletableDeferred(); val gate = repo.actionGate!!
            respond(); await { state.processingId != null }
            if (logout) auth.logout() else auth.state.value = auth.state.value.copy(user = AuthUser(8, "다른 사용자"))
            await { !state.visible && state.processingId == null }
            gate.complete(Unit); delay(20)
            assertTrue(state.requests.isEmpty()); assertNull(state.notice)
            assertEquals(1 + if (logout) 0 else 1, repo.loads)
            auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "테스트"), true)
            yield()
        }
    }

    @Test fun logoutDiscardsLateGetAndSignedOutOpenDoesNothing() = scenario {
        repo.loadGate = CompletableDeferred(); val gate = repo.loadGate!!
        model.open(); await { repo.loads == 1 }
        auth.logout(); await { !state.visible }
        gate.complete(Unit); delay(20); model.open()
        assertEquals(1, repo.loads); assertFalse(state.visible); assertTrue(state.requests.isEmpty())
    }

    companion object {
        private suspend fun await(condition: () -> Boolean) { while (!condition()) delay(1) }
    }
}
