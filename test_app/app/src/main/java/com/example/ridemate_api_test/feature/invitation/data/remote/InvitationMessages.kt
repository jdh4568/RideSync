package com.example.ridemate_api_test.feature.invitation.data.remote

import com.example.ridemate_api_test.feature.invitation.domain.model.*
import org.json.JSONObject
import org.json.JSONTokener

/** Only validated messages cross the app-owned transport boundary. */
sealed interface InvitationMessage {
    data class Status(val value: InvitationStatus) : InvitationMessage
    data class Invite(val value: ReceivedInvitation) : InvitationMessage
    data class Error(val code: String) : InvitationMessage
    data class Started(val value: StartedRide) : InvitationMessage
    data class Cancelled(val invitationId: String, val groupId: Long) : InvitationMessage
}

fun parseInvitationMessage(text: String): InvitationMessage? = try {
    val json = invitationObject(text)
    when (json.text("type")) {
        "RIDE_STARTED" -> InvitationMessage.Started(StartedRide(
            json.integer("rideSessionId").also { require(it > 0) },
            json.integer("groupId").also { require(it > 0) },
        ))
        "RIDE_INVITE_CANCELLED" -> InvitationMessage.Cancelled(
            json.text("invitationId").also { require(it.isNotBlank()) },
            json.integer("groupId").also { require(it > 0) },
        )
        "RIDE_INVITE" -> InvitationMessage.Invite(ReceivedInvitation(
            json.text("invitationId").also { require(it.isNotBlank()) },
            json.integer("groupId").also { require(it > 0) },
            json.text("groupName").also { require(it.isNotBlank()) },
            json.integer("leaderId").also { require(it > 0) },
            json.text("leaderNickname").also { require(it.isNotBlank()) },
        ))
        "RIDE_INVITE_STATUS" -> {
            val id = json.text("invitationId").also { require(it.isNotBlank()) }
            val array = json.getJSONArray("participants")
            require(array.length() in 2..50)
            val participants = (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                InvitationParticipant(entry.integer("userId").also { require(it > 0) },
                    entry.text("nickname").also { require(it.isNotBlank()) },
                    Participation.valueOf(entry.text("status")))
            }
            require(participants.map { it.userId }.distinct().size == participants.size)
            val accepted = json.integer("acceptedCount").also {
                require(it == participants.count { p -> p.status == Participation.ACCEPTED }.toLong())
            }.toInt()
            val canStart = json.get("canStart") as? Boolean ?: error("Invalid boolean")
            require(canStart == (accepted >= 2))
            InvitationMessage.Status(InvitationStatus(id, participants, accepted, canStart))
        }
        "ERROR" -> {
            require(json.text("code").isNotBlank() && json.text("message").isNotBlank())
            InvitationMessage.Error(json.text("code"))
        }
        else -> null
    }
} catch (_: Exception) { null }

fun invitationResponseMessage(invitationId: String, accepted: Boolean): String {
    require(invitationId.isNotBlank())
    return JSONObject().put("type", "RIDE_INVITE_RESPONSE")
        .put("invitationId", invitationId).put("accepted", accepted).toString()
}

internal fun JSONObject.integer(key: String): Long {
    val value = get(key)
    require(value is Int || value is Long)
    return (value as Number).toLong()
}
internal fun JSONObject.text(key: String): String = get(key) as? String ?: error("Invalid string")
internal fun invitationObject(text: String): JSONObject {
    val tokener = JSONTokener(text)
    val value = tokener.nextValue() as? JSONObject ?: error("Invalid object")
    require(tokener.nextClean() == '\u0000')
    return value
}
