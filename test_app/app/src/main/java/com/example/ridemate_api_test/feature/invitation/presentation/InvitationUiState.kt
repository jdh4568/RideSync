package com.example.ridemate_api_test.feature.invitation.presentation

import com.example.ridemate_api_test.feature.ride.domain.model.RideDestination
import com.example.ridemate_api_test.feature.invitation.domain.model.InvitationProblem
import com.example.ridemate_api_test.feature.invitation.domain.model.InvitationNotice

/** Counts shown here exclude the leader. No sample or assumed server counts are defaults. */
data class InvitationCounts(val requested: Int, val accepted: Int, val pending: Int, val rejected: Int) {
    init {
        require(listOf(requested, accepted, pending, rejected).all { it in 0..49 })
        require(requested == accepted + pending + rejected)
    }
}

enum class InvitationResponseUi { READY, SENDING, ACCEPT_SENT, REJECT_SENT, FAILED, UNCERTAIN }

sealed interface InvitationPanel {
    val invitationId: String

    data class Leader(
        override val invitationId: String,
        val counts: InvitationCounts? = null,
        val canStart: Boolean = false,
        val busy: Boolean = false,
        val statusConnected: Boolean = false,
        val fresh: Boolean = false,
        val socketError: Boolean = false,
        val actionsConnected: Boolean = false,
        val actionProblem: InvitationProblem? = null,
        val actionUncertain: Boolean = false,
        val startContextKnown: Boolean = false,
    ) : InvitationPanel

    data class Member(
        override val invitationId: String,
        val groupName: String,
        val leaderNickname: String,
        val response: InvitationResponseUi = InvitationResponseUi.READY,
        val choice: Boolean? = null,
        val fresh: Boolean = false,
        val responseBlocked: Boolean = false,
        val socketErrorCode: String? = null,
    ) : InvitationPanel
}

/** Server-derived presentation; samples are confined to Preview/tests. */
data class InvitationUiState(
    val panels: List<InvitationPanel> = emptyList(),
    val hiddenIds: Set<String> = emptySet(),
    val ride: RideDestination? = null,
    val rideOpen: Boolean = false,
    val creating: Boolean = false,
    val creationUncertain: Boolean = false,
    val creationProblem: InvitationProblem? = null,
    val createdInvitationId: String? = null,
    val notice: InvitationNotice? = null,
) {
    val canCreate: Boolean get() = !creating && !creationUncertain && createdInvitationId == null &&
        creationProblem !in setOf(InvitationProblem.FORBIDDEN, InvitationProblem.GROUP_NOT_FOUND)
    val visiblePanel: InvitationPanel? get() = panels.firstOrNull { it.invitationId !in hiddenIds }
    val hasInvitations: Boolean get() = panels.isNotEmpty()

    // Hiding is local presentation only: never a cancellation or a response.
    fun hidePanel(): InvitationUiState = visiblePanel?.let {
        copy(hiddenIds = hiddenIds + it.invitationId)
    } ?: this

    fun reopen(): InvitationUiState = copy(hiddenIds = emptySet())
    fun openRide(): InvitationUiState = if (ride != null) copy(rideOpen = true) else this
    fun backHome(): InvitationUiState = copy(rideOpen = false)
}
