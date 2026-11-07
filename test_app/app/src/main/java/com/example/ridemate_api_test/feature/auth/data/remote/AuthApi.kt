package com.example.ridemate_api_test.feature.auth.data.remote

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.feature.auth.domain.model.*
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AuthApi { suspend fun login(loginId: String, password: String): AuthCredentials }

class HttpAuthApi(private val client: OkHttpClient, private val endpoint: ServerEndpoint) : AuthApi {
    override suspend fun login(loginId: String, password: String): AuthCredentials = suspendCancellableCoroutine { continuation ->
        val request = try {
            Request.Builder().url(endpoint.url("/api/v1/auth/login"))
                .post(JSONObject().put("loginId", loginId).put("password", password).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json").build()
        } catch (_: Exception) {
            continuation.resumeWithException(AuthFailure(AuthProblem.INVALID_REQUEST))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(AuthFailure(AuthProblem.NETWORK))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw AuthFailure(when (response.code) {
                            400 -> AuthProblem.INVALID_REQUEST
                            401 -> AuthProblem.INVALID_CREDENTIALS
                            403 -> AuthProblem.FORBIDDEN
                            in 500..599 -> AuthProblem.SERVER
                            else -> AuthProblem.INVALID_RESPONSE
                        })
                        if (response.code != 200) throw AuthFailure(AuthProblem.INVALID_RESPONSE)
                        val payload = JSONObject(response.readLoginBody())
                        val token = payload.get("accessToken") as? String ?: error("Invalid token type")
                        val rawId = payload.get("userId")
                        require(rawId is Long || rawId is Int)
                        val id = (rawId as Number).toLong()
                        val nickname = payload.get("nickname") as? String ?: error("Invalid nickname type")
                        val parts = token.split('.')
                        require(parts.size == 3 && parts.all { part -> part.matches(Regex("[A-Za-z0-9_-]+")) }
                            && id > 0 && nickname.isNotBlank())
                        // Used only for local expiry scheduling; the server validates the signature at handshake.
                        val claims = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
                        require(claims.getString("sub") == id.toString())
                        val expiry = Math.multiplyExact(claims.getLong("exp"), 1000L)
                        require(expiry > 0)
                        continuation.resume(AuthCredentials(token, AuthUser(id, nickname), expiry))
                    } catch (failure: AuthFailure) {
                        continuation.resumeWithException(failure)
                    } catch (_: Exception) {
                        continuation.resumeWithException(AuthFailure(AuthProblem.INVALID_RESPONSE))
                    }
                }
            }
        })
    }
}

/** A successful HTTP status does not guarantee that the response body arrived intact. */
internal fun Response.readLoginBody(): String = try {
    body?.string() ?: ""
} catch (_: IOException) {
    throw AuthFailure(AuthProblem.NETWORK)
}
