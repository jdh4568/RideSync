package com.example.ridemate_api_test.feature.invitation.domain.model

import com.example.ridemate_api_test.feature.ride.domain.model.RideDestination

data class CreateInvitation(val groupId: Long, val inviteeIds: Set<Long>) {
    init { require(groupId > 0 && inviteeIds.size in 1..49 && inviteeIds.all { it > 0 }) }
}
data class CreatedInvitation(val invitationId: String, val groupId: Long, val invitedCount: Int)
enum class Participation { PENDING, ACCEPTED, REJECTED }
data class InvitationParticipant(val userId: Long, val nickname: String, val status: Participation)
data class InvitationStatus(
    val invitationId: String, val participants: List<InvitationParticipant>,
    val acceptedCount: Int, val canStart: Boolean,
)
data class LeaderInvitation(
    val invitationId: String,
    val groupId: Long? = null,
    val status: InvitationStatus? = null,
    val fresh: Boolean = false,
    val busy: Boolean = false,
    val actionProblem: InvitationProblem? = null,
    val actionUncertain: Boolean = false,
)
data class InvitationRepositoryState(
    val userId: Long? = null,
    val leaders: List<LeaderInvitation> = emptyList(),
    val socketError: Boolean = false,
    val members: List<MemberInvitation> = emptyList(),
    val socketErrorCode: String? = null,
    val ride: RideDestination? = null,
    val notice: InvitationNotice? = null,
    val connected: Boolean = false,
)

data class StartedRide(val rideSessionId: Long, val groupId: Long)
enum class InvitationNotice { CANCELLED, START_AMBIGUOUS }

enum class MemberResponse { READY, SENDING, ACCEPT_SENT, REJECT_SENT, FAILED, UNCERTAIN }

data class ReceivedInvitation(
    val invitationId: String, val groupId: Long, val groupName: String,
    val leaderId: Long, val leaderNickname: String,
)

data class MemberInvitation(
    val invitation: ReceivedInvitation,
    val response: MemberResponse = MemberResponse.READY,
    val choice: Boolean? = null,
    val fresh: Boolean = true,
)
enum class InvitationProblem {
    INVALID_REQUEST, INVALID_INVITEE, USER_OFFLINE, FORBIDDEN, GROUP_NOT_FOUND,
    UNAUTHORIZED, NETWORK, SERVER, INVALID_RESPONSE, NOT_CONNECTED, BUSY,
    INVITATION_NOT_FOUND, INSUFFICIENT_PARTICIPANTS, INVALID_PARTICIPANT, CLOSED,
}
class InvitationFailure(val problem: InvitationProblem) : Exception(problem.name)

val InvitationProblem.resultUncertain: Boolean get() = this in setOf(
    InvitationProblem.NETWORK, InvitationProblem.SERVER, InvitationProblem.INVALID_RESPONSE,
)
