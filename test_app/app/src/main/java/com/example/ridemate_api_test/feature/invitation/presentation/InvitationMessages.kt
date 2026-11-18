package com.example.ridemate_api_test.feature.invitation.presentation

import com.example.ridemate_api_test.R
import com.example.ridemate_api_test.feature.invitation.domain.model.InvitationProblem

fun InvitationProblem.creationMessageResource(): Int = when (this) {
    InvitationProblem.INVALID_REQUEST -> R.string.invitation_create_invalid
    InvitationProblem.INVALID_INVITEE -> R.string.invitation_create_invitee
    InvitationProblem.USER_OFFLINE -> R.string.invitation_create_offline
    InvitationProblem.FORBIDDEN -> R.string.invitation_create_forbidden
    InvitationProblem.GROUP_NOT_FOUND -> R.string.invitation_create_group_missing
    InvitationProblem.UNAUTHORIZED -> R.string.auth_expired
    InvitationProblem.NOT_CONNECTED -> R.string.invitation_status_uncertain
    InvitationProblem.CLOSED -> R.string.invitation_already_closed
    else -> R.string.invitation_create_uncertain
}

fun InvitationProblem.actionMessageResource(): Int = when (this) {
    InvitationProblem.INVITATION_NOT_FOUND -> R.string.invitation_action_not_found
    InvitationProblem.INSUFFICIENT_PARTICIPANTS -> R.string.invitation_start_insufficient
    InvitationProblem.INVALID_PARTICIPANT -> R.string.invitation_start_invalid_participant
    InvitationProblem.FORBIDDEN -> R.string.invitation_action_forbidden
    InvitationProblem.GROUP_NOT_FOUND -> R.string.invitation_create_group_missing
    InvitationProblem.INVALID_REQUEST -> R.string.invitation_create_invalid
    else -> R.string.invitation_action_uncertain
}
