package com.example.ridemate_api_test.feature.group.data.remote

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.group.domain.model.*
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface GroupApi {
    suspend fun groups(token: String): List<Group>
    suspend fun ownedGroups(token: String): List<Group>
    suspend fun join(token: String, groupId: Long)
    suspend fun create(token: String, request: CreateGroup): CreatedGroup
    suspend fun joinRequests(token: String): List<GroupJoinRequest>
    suspend fun respondToJoinRequest(token: String, groupId: Long, userId: Long, decision: GroupRequestDecision): GroupRequestResult
}

class HttpGroupApi(private val client: OkHttpClient, private val endpoint: ServerEndpoint) : GroupApi {
    override suspend fun joinRequests(token: String): List<GroupJoinRequest> =
        execute(token, "/api/v1/groups/join-requests", null, 200) { body ->
            val array = JSONArray(body)
            val requests = (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                require(json.text("status") == "PENDING")
                GroupJoinRequest(
                    json.integer("groupMemberId").also { require(it > 0) },
                    json.integer("groupId").also { require(it > 0) },
                    json.integer("userId").also { require(it > 0) },
                    json.text("nickname").also { require(it.isNotBlank()) },
                    json.text("groupName").also { require(it.isNotBlank()) },
                )
            }
            require(requests.map { it.id }.distinct().size == requests.size)
            require(requests.map { it.groupId to it.userId }.distinct().size == requests.size)
            requests
        }

    override suspend fun respondToJoinRequest(
        token: String, groupId: Long, userId: Long, decision: GroupRequestDecision,
    ): GroupRequestResult {
        require(groupId > 0 && userId > 0)
        return execute(token, "/api/v1/groups/$groupId/requests/$userId/${decision.path}", "", 200) { body ->
            val json = JSONObject(body)
            require(json.integer("groupId") == groupId && json.integer("userId") == userId &&
                json.text("status") == decision.status.name)
            GroupRequestResult(groupId, userId, decision.status)
        }
    }

    override suspend fun groups(token: String): List<Group> =
        execute(token, "/api/v1/groups", null, 200) { decodeGroups(it) }

    override suspend fun ownedGroups(token: String): List<Group> =
        execute(token, "/api/v1/groups/owned", null, 200) { decodeGroups(it, owned = true) }

    private fun decodeGroups(body: String, owned: Boolean = false): List<Group> {
        val array = JSONArray(body)
        val groups = (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val id = json.integer("groupId").also { require(it > 0) }
            val name = json.text("groupName").also { require(it.isNotBlank()) }
            val description = if (json.has("description") && json.isNull("description")) "" else json.text("description")
            val count = json.integer("memberCount").also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
            val max = json.integer("maxMembers").also { require(it in 2..50) }.toInt()
            require(json.has("membershipStatus"))
            val status = if (json.isNull("membershipStatus")) Membership.NONE else when (json.text("membershipStatus")) {
                "APPROVED" -> Membership.APPROVED
                "PENDING" -> Membership.PENDING
                "REJECTED" -> Membership.REJECTED
                else -> Membership.UNKNOWN
            }
            // Reject a broken owned-list contract rather than filtering or showing unauthorized choices.
            if (owned) require(status == Membership.APPROVED && json.text("myRole") == "OWNER")
            Group(id, name, description, count, max, status)
        }
        require(groups.map { it.id }.distinct().size == groups.size)
        return groups
    }

    override suspend fun join(token: String, groupId: Long) {
        require(groupId > 0)
        execute(token, "/api/v1/groups/$groupId/join", "", 200) { body ->
            val json = JSONObject(body)
            require(json.integer("groupId") == groupId && json.text("status") == "PENDING")
        }
    }

    override suspend fun create(token: String, request: CreateGroup): CreatedGroup {
        val body = JSONObject().put("groupName", request.name).put("description", request.description)
            .put("maxMembers", request.maxMembers).toString()
        return execute(token, "/api/v1/groups", body, 201) { raw ->
            val json = JSONObject(raw)
            val id = json.integer("groupId").also { require(it > 0) }
            val name = json.text("groupName").also { require(it.isNotBlank()) }
            CreatedGroup(id, name)
        }
    }

    private suspend fun <T> execute(token: String, path: String, body: String?, expected: Int, decode: (String) -> T): T {
        val request = try {
            Request.Builder().url(endpoint.url(path)).header("Authorization", "Bearer $token")
                .header("Accept", "application/json").apply {
                    if (body == null) get() else post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                }.build()
        } catch (_: Exception) { throw GroupFailure(GroupProblem.INVALID_REQUEST) }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(GroupFailure(GroupProblem.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        runCatching {
                            if (response.code == 401) throw UnauthorizedRequest()
                            if (response.code != expected) {
                                val code = try { JSONObject(response.body?.string().orEmpty()).optString("code") }
                                    catch (_: Exception) { "" }
                                throw GroupFailure(groupProblem(response.code, code))
                            }
                            val raw = try { response.body?.string().orEmpty() }
                                catch (_: IOException) { throw GroupFailure(GroupProblem.NETWORK) }
                            try { decode(raw) }
                            catch (_: Exception) { throw GroupFailure(GroupProblem.INVALID_RESPONSE) }
                        }
                    }
                    if (!continuation.isCancelled) result.fold(
                        onSuccess = { continuation.resume(it) }, onFailure = { continuation.resumeWithException(it) })
                }
            })
        }
    }
}

private fun JSONObject.integer(key: String): Long {
    val value = get(key)
    require(value is Int || value is Long)
    return (value as Number).toLong()
}
private fun JSONObject.text(key: String): String = get(key) as? String ?: error("Invalid field type")

internal fun groupProblem(status: Int, code: String): GroupProblem = when {
    status == 409 && code == "GROUP_FULL" -> GroupProblem.FULL
    status == 400 -> GroupProblem.INVALID_REQUEST
    status == 403 -> GroupProblem.FORBIDDEN
    status == 404 -> GroupProblem.NOT_FOUND
    status == 409 -> GroupProblem.CONFLICT
    status in 500..599 -> GroupProblem.SERVER
    else -> GroupProblem.INVALID_RESPONSE
}
