package com.example.ridemate_api_test.feature.auth.data.remote

import com.example.ridemate_api_test.core.network.ServerEndpoint
import com.example.ridemate_api_test.feature.auth.domain.repository.*
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface SignupApi {
    suspend fun check(identity: SignupIdentity, value: String): Boolean
    suspend fun signup(request: SignupRequest): SignupResult
}

class HttpSignupApi(private val client: OkHttpClient, private val endpoint: ServerEndpoint) : SignupApi {
    override suspend fun check(identity: SignupIdentity, value: String): Boolean {
        val path = if (identity == SignupIdentity.LOGIN_ID) "check-login-id" else "check-nickname"
        val parameter = if (identity == SignupIdentity.LOGIN_ID) "loginId" else "nickname"
        val request = buildRequest {
            Request.Builder().url(endpoint.url("/api/v1/auth/$path").newBuilder()
                .addQueryParameter(parameter, value).build()).get().build()
        }
        return execute(request, 200) { json ->
            json.get("available") as? Boolean ?: error("Invalid availability")
        }
    }

    override suspend fun signup(request: SignupRequest): SignupResult {
        val httpRequest = buildRequest {
            val body = JSONObject().put("loginId", request.loginId).put("password", request.password)
                .put("passwordConfirm", request.passwordConfirm).put("nickname", request.nickname)
            Request.Builder().url(endpoint.url("/api/v1/auth/signup"))
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        }
        return execute(httpRequest, 201) { json ->
            val id = json.get("userId")
            require(id is Long || id is Int)
            val userId = (id as Number).toLong()
            val nickname = json.get("nickname") as? String ?: error("Invalid nickname")
            require(userId > 0 && nickname.isNotBlank())
            SignupResult(userId, nickname)
        }
    }

    private fun buildRequest(build: () -> Request): Request = try {
        // Public auth endpoints must not inherit an Authorization header.
        build().newBuilder().removeHeader("Authorization").header("Accept", "application/json").build()
    } catch (_: Exception) { throw SignupFailure(SignupProblem.INVALID_REQUEST) }

    private suspend fun <T> execute(request: Request, successCode: Int, decode: (JSONObject) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled)
                        continuation.resumeWithException(SignupFailure(SignupProblem.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        runCatching {
                            // HTTP failures with missing/malformed bodies still have a safe status fallback.
                            if (response.code != successCode) {
                                val code = try {
                                    JSONObject(response.body?.string().orEmpty()).optString("code")
                                } catch (_: Exception) { "" }
                                throw SignupFailure(signupProblem(response.code, code))
                            }
                            val body = try { response.body?.string().orEmpty() }
                            catch (_: IOException) { throw SignupFailure(SignupProblem.NETWORK) }
                            try { decode(JSONObject(body)) }
                            catch (_: Exception) { throw SignupFailure(SignupProblem.INVALID_RESPONSE) }
                        }
                    }
                    if (!continuation.isCancelled) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) })
                }
            })
        }
}

internal fun signupProblem(status: Int, code: String): SignupProblem = when {
    status == 400 && code == "PASSWORD_MISMATCH" -> SignupProblem.PASSWORD_MISMATCH
    status == 400 && code == "INVALID_PASSWORD" -> SignupProblem.INVALID_PASSWORD
    status == 409 && code == "DUPLICATE_LOGIN_ID" -> SignupProblem.DUPLICATE_LOGIN_ID
    status == 409 && code == "DUPLICATE_NICKNAME" -> SignupProblem.DUPLICATE_NICKNAME
    status == 409 && code == "DUPLICATE_USER" -> SignupProblem.DUPLICATE_USER
    status == 400 -> SignupProblem.INVALID_REQUEST
    status == 401 -> SignupProblem.UNAUTHORIZED
    status == 403 -> SignupProblem.FORBIDDEN
    status in 500..599 -> SignupProblem.SERVER
    else -> SignupProblem.INVALID_RESPONSE
}

