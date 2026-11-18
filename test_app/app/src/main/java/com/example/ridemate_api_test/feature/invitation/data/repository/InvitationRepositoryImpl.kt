package com.example.ridemate_api_test.feature.invitation.data.repository

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.core.websocket.*
import com.example.ridemate_api_test.feature.auth.domain.repository.AuthRepository
import com.example.ridemate_api_test.feature.invitation.data.remote.*
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import com.example.ridemate_api_test.feature.invitation.domain.repository.InvitationRepository
import com.example.ridemate_api_test.feature.ride.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Eager app-owned subscriber: replay/early status messages never depend on a visible Route. */
class InvitationRepositoryImpl(
    private val api: InvitationApi,
    private val session: AuthenticatedSession,
    private val auth: AuthRepository,
    private val channel: LoginMessageChannel,
    scope: CoroutineScope,
) : InvitationRepository {
    private val mutableState = MutableStateFlow(InvitationRepositoryState())
    override val state = mutableState.asStateFlow()
    private var version = channel.sessionVersion
    private var creating = false
    private var uncertain = false
    private var uncertainOrigin: LoginSocketIdentity? = null
    private val snapshots = mutableMapOf<String, Pair<InvitationStatus, LoginSocketIdentity>>()
    private val targets = mutableMapOf<String, Set<Long>>()
    private val memberOrigins = mutableMapOf<String, LoginSocketIdentity>()
    // Session-only tombstones prevent late events/results from restoring ended invitations.
    private val closed = mutableMapOf<String, Long?>()
    private val startedRides = mutableSetOf<Long>()
    private val ambiguousRides = mutableSetOf<Long>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            auth.state.collect {
                syncOwner()
                mutableState.value = state.value.copy(connected = channel.identity != null, leaders = state.value.leaders.map { leader ->
                    leader.copy(fresh = leader.status != null && channel.identity != null && snapshots[leader.invitationId]?.second == channel.identity)
                }, members = state.value.members.map { member ->
                    val fresh = channel.identity != null && memberOrigins[member.invitation.invitationId] == channel.identity
                    member.copy(fresh = fresh, response = if (!fresh && member.response in setOf(
                        MemberResponse.ACCEPT_SENT, MemberResponse.REJECT_SENT, MemberResponse.SENDING))
                        MemberResponse.UNCERTAIN else member.response)
                })
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            channel.messages.collect { event ->
                syncOwner()
                if (event.identity != channel.identity || event.identity.userId != state.value.userId) return@collect
                when (val message = parseInvitationMessage(event.text)) {
                    is InvitationMessage.Status -> {
                        val status = message.value
                        if (status.invitationId in closed) return@collect
                        if (state.value.members.any { it.invitation.invitationId == status.invitationId }) return@collect
                        if (status.participants.singleOrNull { it.userId == event.identity.userId }?.status != Participation.ACCEPTED)
                            return@collect
                        val expected = targets[status.invitationId]
                        if (expected != null && expected != status.participants.map { it.userId }.toSet()) return@collect
                        snapshots[status.invitationId] = status to event.identity
                        val existing = state.value.leaders.firstOrNull { it.invitationId == status.invitationId }
                        // Unknown early status is cached, never proof that this POST succeeded.
                        if (existing != null || (!creating && (!uncertain || event.identity != uncertainOrigin))) {
                            publish((existing ?: LeaderInvitation(status.invitationId)).copy(status = status, fresh = true,
                                actionProblem = existing?.actionProblem?.takeUnless { it == InvitationProblem.INSUFFICIENT_PARTICIPANTS }))
                        }
                    }
                    is InvitationMessage.Invite -> {
                        val invite = message.value
                        if (invite.invitationId in closed) return@collect
                        if (invite.leaderId == event.identity.userId || state.value.leaders.any { it.invitationId == invite.invitationId }) return@collect
                        val old = state.value.members.firstOrNull { it.invitation.invitationId == invite.invitationId }
                        // An ID cannot silently change its group or leader. Same-connection duplicates are not recovery evidence.
                        if (old != null && (old.invitation.groupId != invite.groupId || old.invitation.leaderId != invite.leaderId)) return@collect
                        val replay = old != null && memberOrigins[invite.invitationId] != event.identity
                        memberOrigins[invite.invitationId] = event.identity
                        val next = if (old == null) MemberInvitation(invite) else old.copy(invitation = invite,
                            fresh = true, response = if (replay) MemberResponse.READY else old.response)
                        mutableState.value = state.value.copy(members = if (old == null) state.value.members + next
                            else state.value.members.map { if (it.invitation.invitationId == invite.invitationId) next else it })
                    }
                    is InvitationMessage.Error -> {
                        // No request/ invitation identifier: even a single outstanding send cannot prove attribution.
                        mutableState.value = state.value.copy(socketError = true, socketErrorCode = message.code)
                    }
                    is InvitationMessage.Started -> onStarted(message.value)
                    is InvitationMessage.Cancelled -> {
                        val knownGroup = state.value.leaders.firstOrNull { it.invitationId == message.invitationId }?.groupId
                            ?: state.value.members.firstOrNull { it.invitation.invitationId == message.invitationId }?.invitation?.groupId
                        if (knownGroup == null || knownGroup == message.groupId) finishInvitation(message.invitationId, message.groupId, cancelled = true)
                    }
                    null -> Unit
                }
            }
        }
    }

    private fun syncOwner() {
        val current = auth.state.value
        val id = current.user?.id.takeIf { current.enteredSession }
        if (version != channel.sessionVersion || state.value.userId != id) {
            version = channel.sessionVersion
            creating = false
            uncertain = false
            uncertainOrigin = null
            snapshots.clear()
            targets.clear()
            memberOrigins.clear()
            closed.clear()
            startedRides.clear()
            ambiguousRides.clear()
            mutableState.value = InvitationRepositoryState(userId = id)
        }
    }

    override fun dismissNotice() {
        syncOwner()
        mutableState.value = state.value.copy(notice = null)
    }

    private fun finishInvitation(id: String, groupId: Long?, cancelled: Boolean) {
        if (id in closed) return
        closed[id] = groupId
        val member = state.value.members.any { it.invitation.invitationId == id }
        snapshots.remove(id)
        targets.remove(id)
        memberOrigins.remove(id)
        mutableState.value = state.value.copy(
            leaders = state.value.leaders.filterNot { it.invitationId == id },
            members = state.value.members.filterNot { it.invitation.invitationId == id },
            notice = if (cancelled && member) InvitationNotice.CANCELLED else state.value.notice,
        )
    }

    private fun confirmStart(id: String, ride: StartedRide, role: RideRole) {
        if (id in closed || ride.rideSessionId in startedRides) return
        if (state.value.ride?.rideSessionId?.let { it != ride.rideSessionId } == true) {
            startedRides += ride.rideSessionId
            ambiguousRides.remove(ride.rideSessionId)
            finishInvitation(id, ride.groupId, cancelled = false)
            mutableState.value = state.value.copy(notice = InvitationNotice.START_AMBIGUOUS)
            return
        }
        startedRides += ride.rideSessionId
        ambiguousRides.remove(ride.rideSessionId)
        finishInvitation(id, ride.groupId, cancelled = false)
        mutableState.value = state.value.copy(ride = RideDestination(ride.rideSessionId, ride.groupId, role),
            notice = state.value.notice.takeUnless { it == InvitationNotice.START_AMBIGUOUS && ambiguousRides.isEmpty() })
    }

    private fun onStarted(ride: StartedRide) {
        if (ride.rideSessionId in startedRides || ride.rideSessionId in ambiguousRides) return
        val leaders = state.value.leaders.filter { it.groupId == null || it.groupId == ride.groupId }
        val members = state.value.members.filter { it.invitation.groupId == ride.groupId }
        // Include ended/unknown contexts: group ID alone cannot identify which invitation started.
        val endedContext = closed.values.any { it == null || it == ride.groupId }
        val leader = leaders.singleOrNull()?.takeIf { it.groupId == ride.groupId && members.isEmpty() }
        val member = members.singleOrNull()?.takeIf { leaders.isEmpty() && it.choice == true &&
            it.response in setOf(MemberResponse.ACCEPT_SENT, MemberResponse.UNCERTAIN, MemberResponse.SENDING) }
        if (!creating && !uncertain && !endedContext && leader != null) confirmStart(leader.invitationId, ride, RideRole.LEADER)
        else if (!creating && !uncertain && !endedContext && member != null) confirmStart(member.invitation.invitationId, ride, RideRole.MEMBER)
        else {
            ambiguousRides += ride.rideSessionId
            mutableState.value = state.value.copy(notice = InvitationNotice.START_AMBIGUOUS)
        }
    }

    override suspend fun start(invitationId: String) = performAction(invitationId, start = true)
    override suspend fun cancel(invitationId: String) = performAction(invitationId, start = false)

    private suspend fun performAction(id: String, start: Boolean) {
        syncOwner()
        val userId = state.value.userId ?: return
        if (channel.identity == null || id in closed || state.value.leaders.any { it.busy }) return
        val leader = state.value.leaders.firstOrNull { it.invitationId == id } ?: return
        if (leader.actionUncertain || leader.actionProblem in setOf(InvitationProblem.INVITATION_NOT_FOUND,
                InvitationProblem.FORBIDDEN, InvitationProblem.GROUP_NOT_FOUND) ||
            (start && (leader.groupId == null || !leader.fresh || leader.status?.canStart != true ||
                leader.actionProblem == InvitationProblem.INVALID_PARTICIPANT))) return
        val ticket = version
        publish(leader.copy(busy = true, actionProblem = null))
        try {
            if (start) {
                val ride = session.authenticated { api.start(it, id) }
                currentCoroutineContext().ensureActive()
                syncOwner()
                if (ticket != version || state.value.userId != userId || id in closed) return
                if (ride.rideSessionId <= 0 || ride.groupId != leader.groupId)
                    throw InvitationFailure(InvitationProblem.INVALID_RESPONSE)
                confirmStart(id, ride, RideRole.LEADER)
            } else {
                session.authenticated { api.cancel(it, id) }
                currentCoroutineContext().ensureActive()
                syncOwner()
                if (ticket != version || state.value.userId != userId || id in closed) return
                finishInvitation(id, leader.groupId, cancelled = true)
            }
        } catch (failure: Exception) {
            syncOwner()
            if (ticket == version && state.value.userId == userId && id !in closed) {
                val current = state.value.leaders.firstOrNull { it.invitationId == id }
                val problem = if (failure is CancellationException) InvitationProblem.NETWORK
                    else (failure as? InvitationFailure)?.problem ?: InvitationProblem.INVALID_RESPONSE
                if (current != null) publish(current.copy(busy = false, actionProblem = problem,
                    actionUncertain = problem.resultUncertain,
                    fresh = current.fresh && problem !in setOf(InvitationProblem.INSUFFICIENT_PARTICIPANTS, InvitationProblem.INVALID_PARTICIPANT)))
            }
            if (failure is CancellationException) throw failure
        } finally {
            if (ticket == version && id !in closed) {
                state.value.leaders.firstOrNull { it.invitationId == id }?.takeIf { it.busy }?.let { publish(it.copy(busy = false)) }
            }
        }
    }

    override fun respond(invitationId: String, accepted: Boolean) {
        syncOwner()
        val origin = channel.identity ?: return
        if (origin.userId != state.value.userId) return
        val member = state.value.members.firstOrNull { it.invitation.invitationId == invitationId } ?: return
        if (!member.fresh || memberOrigins[invitationId] != origin ||
            member.response !in setOf(MemberResponse.READY, MemberResponse.FAILED) ||
            (member.choice != null && member.choice != accepted) ||
            state.value.members.any { it.response in setOf(MemberResponse.SENDING, MemberResponse.ACCEPT_SENT,
                MemberResponse.REJECT_SENT, MemberResponse.UNCERTAIN) }) return
        fun publishMember(value: MemberInvitation) {
            mutableState.value = state.value.copy(members = state.value.members.map {
                if (it.invitation.invitationId == invitationId) value else it
            })
        }
        publishMember(member.copy(choice = accepted, response = MemberResponse.SENDING))
        val ticket = version
        val sent = try { channel.send(invitationResponseMessage(invitationId, accepted)) } catch (_: Exception) {
            // Unexpected transport exceptions cannot prove that nothing was queued.
            null
        }
        syncOwner()
        if (ticket != version || state.value.userId != origin.userId || invitationId in closed) return
        publishMember(member.copy(choice = accepted, fresh = channel.identity == origin, response = when {
            sent == null || channel.identity != origin -> MemberResponse.UNCERTAIN
            !sent -> MemberResponse.FAILED
            accepted -> MemberResponse.ACCEPT_SENT
            else -> MemberResponse.REJECT_SENT
        }))
    }

    private fun publish(leader: LeaderInvitation) {
        if (leader.invitationId in closed) return
        val previous = state.value.leaders
        mutableState.value = state.value.copy(socketError = false, leaders =
            if (previous.any { it.invitationId == leader.invitationId })
                previous.map { if (it.invitationId == leader.invitationId) leader else it }
            else previous + leader)
    }

    override suspend fun create(request: CreateInvitation): CreatedInvitation {
        syncOwner()
        val userId = state.value.userId ?: throw InvitationFailure(InvitationProblem.UNAUTHORIZED)
        val origin = channel.identity ?: throw InvitationFailure(InvitationProblem.NOT_CONNECTED)
        if (userId in request.inviteeIds) throw InvitationFailure(InvitationProblem.INVALID_REQUEST)
        if (creating || uncertain) throw InvitationFailure(InvitationProblem.BUSY)
        val ticket = version
        creating = true
        try {
            val created = session.authenticated { api.create(it, request) }
            currentCoroutineContext().ensureActive()
            syncOwner()
            if (version != ticket || state.value.userId != userId) throw CancellationException("Session changed")
            require(created.invitationId.isNotBlank() && created.groupId == request.groupId && created.invitedCount == request.inviteeIds.size)
            if (created.invitationId in closed) throw InvitationFailure(InvitationProblem.CLOSED)
            val expected = request.inviteeIds + userId
            targets[created.invitationId] = expected
            val cached = snapshots[created.invitationId]?.takeIf { (status, _) ->
                status.participants.map { it.userId }.toSet() == expected
            }
            if (cached == null) snapshots.remove(created.invitationId)
            publish(LeaderInvitation(created.invitationId, created.groupId, cached?.first,
                fresh = channel.identity != null && cached?.second == channel.identity))
            return created
        } catch (failure: Exception) {
            if (version == ticket) {
                uncertain = failure is CancellationException || failure !is InvitationFailure || failure.problem.resultUncertain
                uncertainOrigin = if (uncertain) origin else null
                // A replay on a new connection confirms an independent existing invitation,
                // never this POST's result. Keep the no-resend latch and do not navigate.
                if (uncertain) snapshots.values.filter { (_, identity) -> identity != origin && identity == channel.identity }
                    .forEach { (status, _) ->
                        val known = state.value.leaders.firstOrNull { it.invitationId == status.invitationId }
                        publish((known ?: LeaderInvitation(status.invitationId)).copy(status = status, fresh = true))
                    }
            }
            if (failure is UnauthorizedRequest) throw InvitationFailure(InvitationProblem.UNAUTHORIZED)
            throw failure
        } finally {
            if (version == ticket) creating = false
        }
    }
}
