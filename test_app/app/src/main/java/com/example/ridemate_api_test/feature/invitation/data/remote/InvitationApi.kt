package com.example.ridemate_api_test.feature.invitation.data.remote

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.core.network.UnauthorizedRequest
import com.example.ridemate_api_test.feature.invitation.domain.model.*
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface InvitationApi {
    suspend fun create(token: String, request: CreateInvitation): CreatedInvitation
    suspend fun start(token: String, invitationId: String): StartedRide
    suspend fun cancel(token: String, invitationId: String)
}

class HttpInvitationApi(private val client: OkHttpClient, private val endpoint: ServerEndpoint) : InvitationApi {
    override suspend fun start(token: String, invitationId: String): StartedRide = action(token, invitationId, "start") { json ->
        require(json.text("status") == "RIDING")
        java.time.LocalDateTime.parse(json.text("startedAt"))
        StartedRide(json.integer("rideSessionId").also { require(it > 0) },
            json.integer("groupId").also { require(it > 0) })
    }

    override suspend fun cancel(token: String, invitationId: String) = action(token, invitationId, "cancel") { json ->
        require(json.text("invitationId") == invitationId && json.text("status") == "CANCELLED")
    }

    private suspend fun <T> action(token: String, invitationId: String, action: String, parse: (JSONObject) -> T): T {
        require(invitationId.isNotBlank())
        val url = endpoint.url("/api/v1/ride-invitations").newBuilder().addPathSegment(invitationId).addPathSegment(action).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null)).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(InvitationFailure(InvitationProblem.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { runCatching {
                        if (response.code == 401) throw UnauthorizedRequest()
                        if (response.code != 200) {
                            val code = try { invitationObject(response.body?.string().orEmpty()).optString("code") }
                                catch (_: Exception) { "" }
                            throw InvitationFailure(when {
                                response.code == 400 && code == "INVALID_REQUEST" -> InvitationProblem.INVALID_REQUEST
                                response.code == 403 -> InvitationProblem.FORBIDDEN
                                response.code == 404 && code == "INVITATION_NOT_FOUND" -> InvitationProblem.INVITATION_NOT_FOUND
                                response.code == 404 && code == "GROUP_NOT_FOUND" -> InvitationProblem.GROUP_NOT_FOUND
                                response.code == 409 && code == "INSUFFICIENT_PARTICIPANTS" -> InvitationProblem.INSUFFICIENT_PARTICIPANTS
                                response.code == 409 && code == "INVALID_PARTICIPANT" -> InvitationProblem.INVALID_PARTICIPANT
                                response.code in 500..599 -> InvitationProblem.SERVER
                                else -> InvitationProblem.INVALID_RESPONSE
                            })
                        }
                        val raw = try { response.body?.string().orEmpty() }
                            catch (_: IOException) { throw InvitationFailure(InvitationProblem.NETWORK) }
                        try { parse(invitationObject(raw)) }
                        catch (_: Exception) { throw InvitationFailure(InvitationProblem.INVALID_RESPONSE) }
                    } }
                    if (!continuation.isCancelled) result.fold(onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) })
                }
            })
        }
    }

    override suspend fun create(token: String, request: CreateInvitation): CreatedInvitation {
        val payload = JSONObject().put("groupId", request.groupId)
            .put("inviteeIds", JSONArray(request.inviteeIds.sorted())).toString()
        val http = Request.Builder().url(endpoint.url("/api/v1/ride-invitations"))
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(http)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(InvitationFailure(InvitationProblem.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        runCatching {
                            if (response.code == 401) throw UnauthorizedRequest()
                            if (response.code != 201) {
                                val code = try { JSONObject(response.body?.string().orEmpty()).optString("code") }
                                    catch (_: Exception) { "" }
                                throw InvitationFailure(when {
                                    response.code == 400 && code == "INVALID_INVITEE" -> InvitationProblem.INVALID_INVITEE
                                    response.code == 400 && code == "INVALID_REQUEST" -> InvitationProblem.INVALID_REQUEST
                                    response.code == 403 -> InvitationProblem.FORBIDDEN
                                    response.code == 404 && code == "GROUP_NOT_FOUND" -> InvitationProblem.GROUP_NOT_FOUND
                                    response.code == 409 && code == "USER_OFFLINE" -> InvitationProblem.USER_OFFLINE
                                    response.code in 500..599 -> InvitationProblem.SERVER
                                    else -> InvitationProblem.INVALID_RESPONSE
                                })
                            }
                            val raw = try { response.body?.string().orEmpty() }
                                catch (_: IOException) { throw InvitationFailure(InvitationProblem.NETWORK) }
                            try {
                                val json = invitationObject(raw)
                                require(json.text("status") == "PENDING")
                                val id = json.text("invitationId").also { require(it.isNotBlank()) }
                                require(json.integer("groupId") == request.groupId)
                                require(json.integer("invitedCount") == request.inviteeIds.size.toLong())
                                CreatedInvitation(id, request.groupId, request.inviteeIds.size)
                            } catch (_: Exception) { throw InvitationFailure(InvitationProblem.INVALID_RESPONSE) }
                        }
                    }
                    if (!continuation.isCancelled) result.fold(
                        onSuccess = { continuation.resume(it) }, onFailure = { continuation.resumeWithException(it) })
                }
            })
        }
    }
}
