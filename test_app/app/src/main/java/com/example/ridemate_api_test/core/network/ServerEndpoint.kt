package com.example.ridemate_api_test.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ServerEndpoint(baseUrl: String, private val allowHttp: Boolean) {
    private val base: HttpUrl = baseUrl.toHttpUrl().also {
        require(it.encodedPath == "/" && it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty())
    }
    fun url(path: String): HttpUrl {
        // HTTP is only permitted for development builds, in addition to the manifest policy.
        check(allowHttp || base.isHttps)
        return base.newBuilder().encodedPath(path).build()
    }
}
