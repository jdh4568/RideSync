package com.example.ridemate_api_test.core.network

/** Tokens are provided only to the data-layer request, never to screen state. */
interface AuthenticatedSession {
    suspend fun <T> authenticated(request: suspend (String) -> T): T
}

/** HTTP 401, including a response with an unreadable/missing error body. */
class UnauthorizedRequest : Exception("Unauthorized request")
