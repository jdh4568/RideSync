package com.example.ridemate_api_test

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.core.websocket.*
import com.example.ridemate_api_test.feature.auth.domain.model.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.invitation.data.remote.*
import com.example.ridemate_api_test.feature.invitation.data.repository.InvitationRepositoryImpl
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import com.example.ridemate_api_test.feature.invitation.presentation.*
import com.example.ridemate_api_test.feature.ride.domain.model.RideRole
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

/** Runs with the existing bundled JSON runtime, not Android's JVM JSON stubs. */
class InvitationContractTest {
    private val status = """{"type":"RIDE_INVITE_STATUS","invitationId":"inv","participants":[{"userId":2,"nickname":"같은 이름","status":"PENDING"},{"userId":7,"nickname":"같은 이름","status":"ACCEPTED"}],"acceptedCount":1,"canStart":false}"""
    private class Auth : AuthRepository {
        override val state = MutableStateFlow(AuthState(AuthPhase.CONNECTED, AuthUser(7, "리더"), true))
        override fun login(loginId: String, password: String) = error("Not used")
        override fun retryConnection() = error("No extra connection")
        override fun logout() = error("Not used")
    }
    private class Channel(private val auth: Auth) : LoginMessageChannel {
        override var sessionVersion = 1L
        var connectionVersion = 1L
        override val identity get() = auth.state.value.takeIf { it.phase == AuthPhase.CONNECTED && it.enteredSession }
            ?.user?.let { LoginSocketIdentity(it.id, sessionVersion, connectionVersion) }
        override val messages = MutableSharedFlow<LoginTextMessage>()
        val sent = mutableListOf<String>()
        var sendResult = true
        var sendFailure = false
        var onSend: () -> Unit = {}
        override fun send(text: String): Boolean {
            sent += text
            onSend()
            if (sendFailure) throw IllegalStateException("Transport failure")
            return sendResult
        }
        suspend fun emit(text: String) { messages.emit(LoginTextMessage(identity!!, text)) }
    }
    private class Api : InvitationApi {
        var starts = 0
        var cancels = 0
        var start: suspend () -> StartedRide = { StartedRide(10, 1) }
        var cancel: suspend () -> Unit = {}
        override suspend fun start(token: String, invitationId: String): StartedRide {
            assertEquals("fixture-token", token)
            starts++
            return start.invoke()
        }
        override suspend fun cancel(token: String, invitationId: String) {
            assertEquals("fixture-token", token)
            cancels++
            cancel.invoke()
        }
        var calls = 0
        var create: suspend () -> CreatedInvitation = { CreatedInvitation("inv", 1, 1) }
        override suspend fun create(token: String, request: CreateInvitation): CreatedInvitation {
            assertEquals("fixture-token", token)
            calls++
            return create.invoke()
        }
    }
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val auth = Auth()
        val channel = Channel(auth)
        val api = Api()
        val session = object : AuthenticatedSession {
            override suspend fun <T> authenticated(request: suspend (String) -> T): T = try { request("fixture-token") }
            catch (e: UnauthorizedRequest) {
                channel.sessionVersion++
                auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
                throw e
            }
        }
        val repository = InvitationRepositoryImpl(api, session, auth, channel, scope)
        val model = InvitationViewModel(auth, scope, repository = repository)
    }
    private fun scenario(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture()
        try { withTimeout(5000) { fixture.block() } } finally { fixture.scope.cancel() }
    }

    private fun invite(id: String = "member") = """{"type":"RIDE_INVITE","invitationId":"$id","groupId":1,"groupName":"한강 모임","leaderId":2,"leaderNickname":"리더"}"""
    private val started = """{"type":"RIDE_STARTED","rideSessionId":10,"groupId":1}"""
    private fun cancelled(id: String = "inv") = """{"type":"RIDE_INVITE_CANCELLED","invitationId":"$id","groupId":1}"""
    private suspend fun Fixture.readyLeader() {
        repository.create(CreateInvitation(1, setOf(2)))
        channel.emit(status.replace("PENDING", "ACCEPTED").replace("\"acceptedCount\":1", "\"acceptedCount\":2")
            .replace("\"canStart\":false", "\"canStart\":true"))
    }

    @Test fun startRestOpensLeaderOnlyOnceAndBackHomeDoesNotFinishRide() = scenario {
        readyLeader()
        model.start("inv")
        yield()
        assertEquals(RideRole.LEADER, model.uiState.value.ride?.role)
        assertTrue(model.uiState.value.rideOpen)
        assertTrue(repository.state.value.leaders.isEmpty())
        model.backHome()
        channel.emit(started)
        channel.emit(status)
        channel.emit(cancelled())
        assertFalse(model.uiState.value.rideOpen)
        assertEquals(10L, model.uiState.value.ride?.rideSessionId)
        model.openRide()
        assertTrue(model.uiState.value.rideOpen)
        assertEquals(1, api.starts)
        assertEquals(0, api.cancels)
    }

    @Test fun startedNotificationWinsOverLateRestFailureWithoutDuplicateNavigation() = scenario {
        readyLeader()
        val gate = CompletableDeferred<Unit>()
        api.start = { gate.await(); throw InvitationFailure(InvitationProblem.NETWORK) }
        model.start("inv")
        model.start("inv")
        model.cancel("inv")
        assertTrue(repository.state.value.leaders.single().busy)
        assertEquals(1, api.starts)
        assertEquals(0, api.cancels)
        channel.emit(started)
        model.backHome()
        gate.complete(Unit)
        yield()
        assertFalse(model.uiState.value.rideOpen)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertEquals(RideRole.LEADER, model.uiState.value.ride?.role)
    }

    @Test fun acceptedMemberMovesOnlyWithUnambiguousStartedNotification() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        channel.emit(started)
        assertEquals(RideRole.MEMBER, model.uiState.value.ride?.role)
        assertEquals(1L, model.uiState.value.ride?.groupId)
        assertTrue(repository.state.value.members.isEmpty())
        channel.emit(invite())
        channel.emit(cancelled("member"))
        assertTrue(repository.state.value.members.isEmpty())
        assertEquals(10L, model.uiState.value.ride?.rideSessionId)
    }

    @Test fun sameGroupInvitationsAndRejectedOrPendingMemberNeverGuessStartedRole() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        channel.emit(invite("second"))
        channel.emit(started)
        assertNull(model.uiState.value.ride)
        assertEquals(InvitationNotice.START_AMBIGUOUS, model.uiState.value.notice)
        assertEquals(2, repository.state.value.members.size)
        channel.emit(cancelled("second"))
        channel.emit(started)
        assertNull(model.uiState.value.ride)
        assertEquals(1, repository.state.value.members.size)
    }

    @Test fun pendingAndRejectedContextAreNotAcceptedByStartedMessage() = scenario {
        channel.emit(invite())
        channel.emit(started)
        assertNull(model.uiState.value.ride)
        model.dismissNotice()
        model.respond("member", false)
        assertEquals(MemberResponse.REJECT_SENT, repository.state.value.members.single().response)
        channel.emit(started.replace("10", "11"))
        assertNull(model.uiState.value.ride)
        assertEquals(1, repository.state.value.members.size)
    }

    @Test fun ambiguousLeaderNotificationCanBeResolvedByExactStartRestContext() = scenario {
        readyLeader()
        channel.emit(invite()) // Same group, opposing roles: do not guess from the group ID.
        val gate = CompletableDeferred<Unit>()
        api.start = { gate.await(); StartedRide(10, 1) }
        model.start("inv")
        channel.emit(started)
        assertNull(model.uiState.value.ride)
        gate.complete(Unit)
        yield()
        assertEquals(RideRole.LEADER, model.uiState.value.ride?.role)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertEquals(1, repository.state.value.members.size)
    }

    @Test fun cancellationOnlyRemovesMatchingMemberAndUnblocksOtherResponse() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        channel.emit(invite("second"))
        channel.emit(cancelled("member"))
        assertEquals(listOf("second"), repository.state.value.members.map { it.invitation.invitationId })
        assertEquals(InvitationNotice.CANCELLED, model.uiState.value.notice)
        model.dismissNotice()
        model.respond("second", false)
        assertEquals(2, channel.sent.size)
        channel.emit(cancelled("member"))
        assertNull(model.uiState.value.notice)
        channel.emit(invite())
        assertEquals(1, repository.state.value.members.size)
    }

    @Test fun cancelRestAndNotificationAreIdempotentAndLateFailureCannotRestorePanel() = scenario {
        readyLeader()
        val gate = CompletableDeferred<Unit>()
        api.cancel = { gate.await(); throw InvitationFailure(InvitationProblem.NETWORK) }
        model.cancel("inv")
        model.start("inv")
        model.cancel("inv")
        assertEquals(1, api.cancels)
        assertEquals(0, api.starts)
        channel.emit(cancelled())
        gate.complete(Unit)
        yield()
        channel.emit(status)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertNull(model.uiState.value.ride)
    }

    @Test fun successfulCancelRestWorksWithoutNotificationAnd404IsNeverCancellationSuccess() = scenario {
        readyLeader()
        api.cancel = { throw InvitationFailure(InvitationProblem.INVITATION_NOT_FOUND) }
        repository.cancel("inv")
        assertEquals(InvitationProblem.INVITATION_NOT_FOUND, repository.state.value.leaders.single().actionProblem)
        repository.cancel("inv")
        repository.start("inv")
        assertEquals(1, api.cancels)
        assertEquals(0, api.starts)
        assertNull(model.uiState.value.ride)
        channel.emit(cancelled())
        assertTrue(repository.state.value.leaders.isEmpty())
    }

    @Test fun unknownActionResultIsLatchedAcrossReconnectAndNewStatus() = scenario {
        readyLeader()
        api.start = { throw InvitationFailure(InvitationProblem.NETWORK) }
        repository.start("inv")
        assertTrue(repository.state.value.leaders.single().actionUncertain)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        channel.emit(status)
        repository.start("inv")
        repository.cancel("inv")
        assertEquals(1, api.starts)
        assertEquals(0, api.cancels)
        assertTrue(repository.state.value.leaders.single().actionUncertain)
        channel.emit(started)
        assertEquals(RideRole.LEADER, model.uiState.value.ride?.role)
    }

    @Test fun insufficientParticipantsNeedsFreshStatusButAllowsCancellation() = scenario {
        readyLeader()
        api.start = { throw InvitationFailure(InvitationProblem.INSUFFICIENT_PARTICIPANTS) }
        repository.start("inv")
        assertFalse(repository.state.value.leaders.single().fresh)
        repository.start("inv")
        assertEquals(1, api.starts)
        repository.cancel("inv")
        assertEquals(1, api.cancels)
        assertTrue(repository.state.value.leaders.isEmpty())
    }

    @Test fun wrongStartGroupDoesNotNavigateAndOldSessionActionCannotRepopulateState() = scenario {
        readyLeader()
        api.start = { StartedRide(10, 2) }
        repository.start("inv")
        assertTrue(repository.state.value.leaders.single().actionUncertain)
        assertNull(model.uiState.value.ride)
        channel.sessionVersion++
        auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "리더"), true)
        readyLeader()
        val gate = CompletableDeferred<Unit>()
        api.start = { withContext(NonCancellable) { gate.await() }; StartedRide(10, 1) }
        model.start("inv")
        channel.sessionVersion++
        auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        gate.complete(Unit)
        yield()
        assertTrue(repository.state.value.leaders.isEmpty())
        assertNull(model.uiState.value.ride)
    }

    @Test fun terminalNotificationBeforeCreateResponseCannotRestoreOrNavigate() = scenario {
        api.create = { channel.emit(cancelled()); CreatedInvitation("inv", 1, 1) }
        model.create(1, setOf(2))
        yield()
        assertNull(model.uiState.value.createdInvitationId)
        assertEquals(InvitationProblem.CLOSED, model.uiState.value.creationProblem)
        channel.emit(status)
        assertTrue(repository.state.value.leaders.isEmpty())
    }

    @Test fun cancelWithWrongGroupAndMalformedTerminalMessagesAreIgnored() = scenario {
        channel.emit(invite())
        channel.emit(cancelled("member").replace("\"groupId\":1", "\"groupId\":2"))
        for (raw in listOf(started.replace("\"rideSessionId\":10", "\"rideSessionId\":\"10\""),
            started.replace("\"groupId\":1", "\"groupId\":0"), cancelled().replace("\"inv\"", "\" \""),
            cancelled() + " trailing")) assertNull(parseInvitationMessage(raw))
        assertEquals(1, repository.state.value.members.size)
        assertNull(model.uiState.value.ride)
    }

    @Test fun cancellationWinsBeforeLateSuccessfulStartResponseAndDoesNotRestoreRide() = scenario {
        readyLeader()
        val gate = CompletableDeferred<Unit>()
        api.start = { gate.await(); StartedRide(10, 1) }
        model.start("inv")
        channel.emit(cancelled())
        gate.complete(Unit)
        yield()
        channel.emit(started)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertNull(model.uiState.value.ride)
        assertEquals(InvitationNotice.START_AMBIGUOUS, model.uiState.value.notice)
    }

    @Test fun replayWithoutGroupNeverStartsByGuessingAndConnectedCancelRemainsPossible() = scenario {
        channel.emit(status.replace("PENDING", "ACCEPTED").replace("\"acceptedCount\":1", "\"acceptedCount\":2")
            .replace("\"canStart\":false", "\"canStart\":true"))
        repository.start("inv")
        channel.emit(started)
        assertEquals(0, api.starts)
        assertNull(model.uiState.value.ride)
        model.dismissNotice()
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        repository.cancel("inv")
        assertEquals(0, api.cancels)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        assertTrue((model.uiState.value.visiblePanel as InvitationPanel.Leader).actionsConnected)
        model.cancel("inv")
        yield()
        assertEquals(1, api.cancels)
        assertTrue(repository.state.value.leaders.isEmpty())
    }

    @Test fun invalidParticipantBlocksStartEvenWithNewCountsAnd401CleansSession() = scenario {
        readyLeader()
        api.start = { throw InvitationFailure(InvitationProblem.INVALID_PARTICIPANT) }
        repository.start("inv")
        channel.emit(status.replace("PENDING", "ACCEPTED").replace("\"acceptedCount\":1", "\"acceptedCount\":2")
            .replace("\"canStart\":false", "\"canStart\":true"))
        repository.start("inv")
        assertEquals(1, api.starts)
        api.cancel = { throw UnauthorizedRequest() }
        repository.cancel("inv")
        assertFalse(auth.state.value.enteredSession)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertNull(model.uiState.value.ride)
    }

    @Test fun secondConfirmedRidePreservesExistingRideAndStillClosesItsExactInvitation() = scenario {
        readyLeader()
        repository.start("inv")
        model.backHome()
        api.create = { CreatedInvitation("second", 2, 1) }
        repository.create(CreateInvitation(2, setOf(2)))
        channel.emit(status.replace("\"invitationId\":\"inv\"", "\"invitationId\":\"second\"")
            .replace("PENDING", "ACCEPTED").replace("\"acceptedCount\":1", "\"acceptedCount\":2")
            .replace("\"canStart\":false", "\"canStart\":true"))
        api.start = { StartedRide(11, 2) }
        repository.start("second")
        assertEquals(10L, model.uiState.value.ride?.rideSessionId)
        assertFalse(model.uiState.value.rideOpen)
        assertTrue(repository.state.value.leaders.isEmpty())
        assertEquals(InvitationNotice.START_AMBIGUOUS, model.uiState.value.notice)
    }

    @Test fun sessionChangeClearsTerminalIdsAndOldTerminalCallbacksAreIgnored() = scenario {
        readyLeader()
        repository.cancel("inv")
        val old = channel.identity!!
        channel.sessionVersion++
        auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "리더"), true)
        readyLeader()
        channel.messages.emit(LoginTextMessage(old, cancelled()))
        assertEquals(1, repository.state.value.leaders.size)
        channel.emit(started)
        assertEquals(10L, model.uiState.value.ride?.rideSessionId)
    }

    @Test fun inFlightOrUncertainCreationIsNotMistakenForAnOlderSameGroupInvitation() = scenario {
        readyLeader()
        api.create = { channel.emit(started); throw InvitationFailure(InvitationProblem.NETWORK) }
        try { repository.create(CreateInvitation(1, setOf(2))); fail() } catch (_: InvitationFailure) { }
        assertNull(model.uiState.value.ride)
        assertEquals("inv", repository.state.value.leaders.single().invitationId)
        channel.emit(started.replace("10", "11"))
        assertNull(model.uiState.value.ride)
    }

    @Test fun memberReceiptIsEagerDeduplicatedAndKeepsVisibleOrderAcrossLeaderUpdates() = scenario {
        channel.emit(invite())
        channel.emit(invite())
        channel.emit(invite("second"))
        channel.emit(status)
        assertEquals(2, repository.state.value.members.size)
        assertEquals("member", model.uiState.value.visiblePanel?.invitationId)
        model.hidePanel()
        assertEquals("second", model.uiState.value.visiblePanel?.invitationId)
        model.reopen()
        assertEquals("member", model.uiState.value.visiblePanel?.invitationId)
        assertTrue(channel.sent.isEmpty())
        assertNull(model.uiState.value.ride)
    }

    @Test fun acceptIsBooleanQueuedOnlyAndNeverAckedByDuplicatesErrorsOrElapsedTime() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        repeat(2) { model.respond("member", true); model.respond("member", false); channel.emit(invite()) }
        delay(10)
        val payload = JSONObject(channel.sent.single())
        assertEquals("RIDE_INVITE_RESPONSE", payload.getString("type"))
        assertEquals("member", payload.getString("invitationId"))
        assertEquals(true, payload.get("accepted"))
        assertEquals(MemberResponse.ACCEPT_SENT, repository.state.value.members.single().response)
        channel.emit("""{"type":"ERROR","code":"INVITATION_NOT_FOUND","message":"fixture"}""")
        assertEquals(1, repository.state.value.members.size)
        assertEquals("INVITATION_NOT_FOUND", (model.uiState.value.visiblePanel as InvitationPanel.Member).socketErrorCode)
        assertEquals(MemberResponse.ACCEPT_SENT, repository.state.value.members.single().response)
        assertEquals(AuthPhase.CONNECTED, auth.state.value.phase)
    }

    @Test fun rejectIsNotConfirmedAndBlocksOtherInvitationResponsesEvenAfterHide() = scenario {
        channel.emit(invite())
        channel.emit(invite("second"))
        model.respond("member", false)
        assertEquals(false, JSONObject(channel.sent.single()).get("accepted"))
        assertEquals(InvitationResponseUi.REJECT_SENT, (model.uiState.value.visiblePanel as InvitationPanel.Member).response)
        model.hidePanel()
        model.respond("second", true)
        assertTrue((model.uiState.value.visiblePanel as InvitationPanel.Member).responseBlocked)
        assertEquals(1, channel.sent.size)
    }

    @Test fun localSendFailureAllowsOnlyExplicitSameChoiceRetry() = scenario {
        channel.emit(invite())
        channel.sendResult = false
        model.respond("member", false)
        assertEquals(MemberResponse.FAILED, repository.state.value.members.single().response)
        model.respond("member", true)
        assertEquals(1, channel.sent.size)
        channel.sendResult = true
        model.respond("member", false)
        assertEquals(2, channel.sent.size)
        assertEquals(MemberResponse.REJECT_SENT, repository.state.value.members.single().response)
    }

    @Test fun reconnectRequiresActualPendingReplayAndKeepsOriginalChoice() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        assertEquals(MemberResponse.UNCERTAIN, repository.state.value.members.single().response)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        model.respond("member", true)
        assertEquals(1, channel.sent.size)
        assertFalse(repository.state.value.members.single().fresh)
        channel.emit(invite())
        assertEquals(MemberResponse.READY, repository.state.value.members.single().response)
        model.respond("member", false)
        assertEquals(1, channel.sent.size)
        model.respond("member", true)
        assertEquals(2, channel.sent.size)
    }

    @Test fun pendingWithoutPriorChoiceIsDisabledUntilReplayAndOldConnectionIsIgnored() = scenario {
        channel.emit(invite())
        val old = channel.identity!!
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        channel.messages.emit(LoginTextMessage(old, invite()))
        model.respond("member", false)
        assertTrue(channel.sent.isEmpty())
        channel.emit(invite())
        model.respond("member", false)
        assertEquals(1, channel.sent.size)
    }

    @Test fun malformedSelfAndConflictingMetadataDoNotReplaceMemberContext() = scenario {
        for (raw in listOf(invite().replace("\"groupId\":1", "\"groupId\":\"1\""),
            invite().replace("\"leaderId\":2", "\"leaderId\":0"), invite().replace("한강 모임", " "),
            invite() + " trailing")) assertNull(parseInvitationMessage(raw))
        channel.emit(invite().replace("\"leaderId\":2", "\"leaderId\":7"))
        assertTrue(repository.state.value.members.isEmpty())
        channel.emit(invite())
        channel.emit(invite().replace("\"groupId\":1", "\"groupId\":8"))
        channel.emit(invite().replace("\"leaderId\":2", "\"leaderId\":8"))
        assertEquals(1L, repository.state.value.members.single().invitation.groupId)
        assertEquals(2L, repository.state.value.members.single().invitation.leaderId)
    }

    @Test fun sendExceptionIsUncertainAndReentrantClickDoesNotSendTwice() = scenario {
        channel.emit(invite())
        channel.onSend = { model.respond("member", false) }
        channel.sendFailure = true
        model.respond("member", true)
        model.respond("member", true)
        assertEquals(1, channel.sent.size)
        assertEquals(MemberResponse.UNCERTAIN, repository.state.value.members.single().response)
    }

    @Test fun logoutDuringSendCannotRestoreOldMemberOrResponseLock() = scenario {
        channel.emit(invite())
        val old = channel.identity!!
        channel.onSend = {
            channel.sessionVersion++
            auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        }
        model.respond("member", true)
        assertTrue(repository.state.value.members.isEmpty())
        assertTrue(model.uiState.value.panels.isEmpty())
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "리더"), true)
        channel.messages.emit(LoginTextMessage(old, invite()))
        assertTrue(repository.state.value.members.isEmpty())
        channel.onSend = {}
        channel.emit(invite())
        model.respond("member", false)
        assertEquals(MemberResponse.REJECT_SENT, repository.state.value.members.single().response)
    }

    @Test fun lateUnattributedErrorNeverGetsAssignedToNewPendingReplay() = scenario {
        channel.emit(invite())
        model.respond("member", true)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        channel.emit(invite())
        channel.emit("""{"type":"ERROR","code":"INVITATION_RESPONSE_CONFLICT","message":"late"}""")
        assertEquals(MemberResponse.READY, repository.state.value.members.single().response)
        assertEquals(true, repository.state.value.members.single().choice)
        assertNotNull(channel.identity)
        model.respond("member", false)
        assertEquals(1, channel.sent.size)
    }

    @Test fun disconnectedDuringSendDoesNotClaimDeliveryOrAutomaticallyRetry() = scenario {
        channel.emit(invite())
        channel.onSend = { auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED) }
        model.respond("member", true)
        assertEquals(MemberResponse.UNCERTAIN, repository.state.value.members.single().response)
        assertFalse(repository.state.value.members.single().fresh)
        model.respond("member", true)
        assertEquals(1, channel.sent.size)
    }

    @Test fun laterPresentationReadsReceivedStateAndStaleHiddenCallbackCannotRespond() = scenario {
        channel.emit(invite())
        val later = InvitationViewModel(auth, scope, repository = repository)
        assertEquals("member", later.uiState.value.visiblePanel?.invitationId)
        later.hidePanel()
        later.respond("member", true)
        assertTrue(channel.sent.isEmpty())
        later.reopen()
        later.respond("member", true)
        assertEquals(1, channel.sent.size)
        channel.emit(status.replace("\"invitationId\":\"inv\"", "\"invitationId\":\"member\""))
        assertTrue(repository.state.value.leaders.isEmpty())
        assertEquals(1, later.uiState.value.panels.size)
    }

    @Test fun earlyStatusIsCachedUntil201AndNeverOverwrittenByTheCreateResponse() = scenario {
        api.create = {
            channel.emit(status)
            assertTrue(repository.state.value.leaders.isEmpty())
            channel.emit(status.replace("PENDING", "ACCEPTED").replace("\"acceptedCount\":1", "\"acceptedCount\":2")
                .replace("\"canStart\":false", "\"canStart\":true"))
            CreatedInvitation("inv", 1, 1)
        }
        repository.create(CreateInvitation(1, setOf(2)))
        val leader = repository.state.value.leaders.single()
        assertEquals(1L, leader.groupId)
        assertEquals(2, leader.status?.acceptedCount)
        assertTrue(leader.status?.canStart == true)
        assertTrue(leader.fresh)
    }

    @Test fun responseBeforeStatusDoesNotInventCountsAndDuplicateStatusReplacesWholeList() = scenario {
        repository.create(CreateInvitation(1, setOf(2)))
        assertNull(repository.state.value.leaders.single().status)
        channel.emit(status)
        channel.emit(status)
        assertEquals(1, repository.state.value.leaders.size)
        assertEquals(2, repository.state.value.leaders.single().status?.participants?.size)
        channel.emit(status.replace("PENDING", "REJECTED"))
        assertEquals(Participation.REJECTED, repository.state.value.leaders.single().status?.participants?.first()?.status)
    }

    @Test fun replayWithoutMetadataDoesNotGuessGroupAndReconnectRequiresNewStatus() = scenario {
        channel.emit(status)
        assertNull(repository.state.value.leaders.single().groupId)
        val oldIdentity = channel.identity!!
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        assertFalse(repository.state.value.leaders.single().fresh)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        assertFalse(repository.state.value.leaders.single().fresh)
        channel.messages.emit(LoginTextMessage(oldIdentity, status.replace("inv", "stale")))
        assertEquals(listOf("inv"), repository.state.value.leaders.map { it.invitationId })
        channel.emit(status)
        assertTrue(repository.state.value.leaders.single().fresh)
    }

    @Test fun wrongParticipantSetOrMissingLeaderCannotCorruptKnownInvitation() = scenario {
        repository.create(CreateInvitation(1, setOf(2)))
        channel.emit(status.replace("\"userId\":2", "\"userId\":3"))
        assertNull(repository.state.value.leaders.single().status)
        channel.emit(status.replace("\"userId\":7", "\"userId\":8"))
        assertNull(repository.state.value.leaders.single().status)
        channel.emit(status)
        assertNotNull(repository.state.value.leaders.single().status)
    }

    @Test fun timeoutWithEarlyStatusIsUncertainAndNeverBecomesCurrentPostSuccessOrRetry() = scenario {
        api.create = { channel.emit(status); throw InvitationFailure(InvitationProblem.NETWORK) }
        try { repository.create(CreateInvitation(1, setOf(2))); fail() } catch (_: InvitationFailure) { }
        assertTrue(repository.state.value.leaders.isEmpty())
        try { repository.create(CreateInvitation(1, setOf(2))); fail() }
        catch (e: InvitationFailure) { assertEquals(InvitationProblem.BUSY, e.problem) }
        assertEquals(1, api.calls)
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        channel.connectionVersion++
        auth.state.value = auth.state.value.copy(phase = AuthPhase.CONNECTED)
        channel.emit(status)
        assertEquals("inv", repository.state.value.leaders.single().invitationId)
        assertNull(repository.state.value.leaders.single().groupId)
        try { repository.create(CreateInvitation(1, setOf(2))); fail() }
        catch (e: InvitationFailure) { assertEquals(InvitationProblem.BUSY, e.problem) }
        assertEquals(1, api.calls)
    }

    @Test fun logoutAndSameUserNewSessionDiscardCachedAndLateMessages() = scenario {
        channel.emit(status)
        val oldIdentity = channel.identity!!
        channel.sessionVersion++
        auth.state.value = AuthState(phase = AuthPhase.SIGNED_OUT)
        assertTrue(repository.state.value.leaders.isEmpty())
        auth.state.value = AuthState(AuthPhase.CONNECTED, AuthUser(7, "리더"), true)
        channel.messages.emit(LoginTextMessage(oldIdentity, status))
        assertTrue(repository.state.value.leaders.isEmpty())
        channel.emit(status)
        assertEquals(1, repository.state.value.leaders.size)
    }

    @Test fun errorIsUnattributedAndDoesNotCloseConnectionOrEnableOtherStages() = scenario {
        channel.emit(status)
        channel.emit("""{"type":"ERROR","code":"INVITATION_NOT_FOUND","message":"fixture"}""")
        assertTrue(repository.state.value.socketError)
        assertEquals(1, repository.state.value.leaders.size)
        assertEquals(AuthPhase.CONNECTED, auth.state.value.phase)
        channel.emit("""{"type":"RIDE_INVITE","invitationId":"another"}""")
        channel.emit("""{"type":"RIDE_STARTED","rideSessionId":10,"groupId":1}""")
        assertEquals(1, repository.state.value.leaders.size)
    }

    @Test fun malformedAndUnknownMessagesNeverBecomeValidStatus() {
        val invalid = listOf("{", "[]", status + " garbage", status.replace("\"userId\":2", "\"userId\":7"),
            status.replace("\"userId\":2", "\"userId\":2.0"), status.replace("\"userId\":2", "\"userId\":\"2\""),
            status.replace("\"userId\":2", "\"userId\":0"), status.replace("PENDING", "UNKNOWN"),
            status.replace("\"acceptedCount\":1", "\"acceptedCount\":2"), status.replace("\"canStart\":false", "\"canStart\":true"),
            status.replace("\"canStart\":false", "\"canStart\":\"false\""), status.replace("같은 이름", " "),
            """{"type":"FUTURE"}""", """{"type":"ERROR","code":null,"message":"bad"}""")
        for (raw in invalid) assertNull(raw, parseInvitationMessage(raw))
        assertNotNull(parseInvitationMessage(status))
    }

    @Test fun disconnectedAndSelfInvitationNeverIssueThePost() = scenario {
        try { repository.create(CreateInvitation(1, setOf(7))); fail() }
        catch (e: InvitationFailure) { assertEquals(InvitationProblem.INVALID_REQUEST, e.problem) }
        auth.state.value = auth.state.value.copy(phase = AuthPhase.DISCONNECTED)
        try { repository.create(CreateInvitation(1, setOf(2))); fail() }
        catch (e: InvitationFailure) { assertEquals(InvitationProblem.NOT_CONNECTED, e.problem) }
        assertEquals(0, api.calls)
    }

    @Test fun unauthorizedUsesSessionCleanupInsteadOfBecomingCreationSuccess() = scenario {
        channel.emit(status)
        api.create = { throw UnauthorizedRequest() }
        try { repository.create(CreateInvitation(1, setOf(2))); fail() }
        catch (e: InvitationFailure) { assertEquals(InvitationProblem.UNAUTHORIZED, e.problem) }
        assertFalse(auth.state.value.enteredSession)
        assertTrue(repository.state.value.leaders.isEmpty())
    }

    @Test fun oldPostCannotPublishIntoANewUsersStateOrUnlockItsRequest() = scenario {
        val gate = CompletableDeferred<Unit>()
        api.create = { gate.await(); CreatedInvitation("late", 1, 1) }
        supervisorScope {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.create(CreateInvitation(1, setOf(2))) }
            channel.sessionVersion++
            auth.state.value = auth.state.value.copy(user = AuthUser(8, "다른 사용자"))
            gate.complete(Unit)
            try { pending.await(); fail() } catch (_: CancellationException) { }
        }
        assertEquals(8L, repository.state.value.userId)
        assertTrue(repository.state.value.leaders.isEmpty())
        api.create = { CreatedInvitation("new", 1, 1) }
        assertEquals("new", repository.create(CreateInvitation(1, setOf(2))).invitationId)
    }
}
