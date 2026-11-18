package com.example.ridemate_api_test.feature.invitation.domain.repository

import com.example.ridemate_api_test.feature.invitation.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface InvitationRepository {
    val state: StateFlow<InvitationRepositoryState>
    suspend fun create(request: CreateInvitation): CreatedInvitation
    fun respond(invitationId: String, accepted: Boolean)
    suspend fun start(invitationId: String)
    suspend fun cancel(invitationId: String)
    fun dismissNotice()
}
