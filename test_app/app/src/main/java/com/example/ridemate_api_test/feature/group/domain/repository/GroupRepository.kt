package com.example.ridemate_api_test.feature.group.domain.repository

import com.example.ridemate_api_test.feature.group.domain.model.*

interface GroupRepository {
    suspend fun groups(): List<Group>
    suspend fun ownedGroups(): List<Group>
    suspend fun join(groupId: Long)
    suspend fun create(request: CreateGroup): CreatedGroup
    suspend fun joinRequests(): List<GroupJoinRequest>
    suspend fun respondToJoinRequest(groupId: Long, userId: Long, decision: GroupRequestDecision): GroupRequestResult
}
