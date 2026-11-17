package com.example.ridemate_api_test.feature.group.data.repository

import com.example.ridemate_api_test.core.network.*
import com.example.ridemate_api_test.feature.group.data.remote.GroupApi
import com.example.ridemate_api_test.feature.group.domain.model.*
import com.example.ridemate_api_test.feature.group.domain.repository.GroupRepository

class GroupRepositoryImpl(private val api: GroupApi, private val session: AuthenticatedSession) : GroupRepository {
    override suspend fun groups() = authorized { api.groups(it) }
    override suspend fun ownedGroups() = authorized { api.ownedGroups(it) }
    override suspend fun join(groupId: Long) = authorized { api.join(it, groupId) }
    override suspend fun create(request: CreateGroup) = authorized { api.create(it, request) }
    override suspend fun joinRequests() = authorized { api.joinRequests(it) }
    override suspend fun respondToJoinRequest(groupId: Long, userId: Long, decision: GroupRequestDecision) =
        authorized { api.respondToJoinRequest(it, groupId, userId, decision) }

    private suspend fun <T> authorized(request: suspend (String) -> T): T = try { session.authenticated(request) }
    catch (_: UnauthorizedRequest) { throw GroupFailure(GroupProblem.UNAUTHORIZED) }
}
