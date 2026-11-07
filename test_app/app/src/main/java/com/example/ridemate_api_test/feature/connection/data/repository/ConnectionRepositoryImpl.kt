package com.example.ridemate_api_test.feature.connection.data.repository

import com.example.ridemate_api_test.feature.connection.domain.repository.ConnectionRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ConnectionRepositoryImpl : ConnectionRepository {
    override suspend fun checkConnection(baseUrl: String): Int = withContext(Dispatchers.IO) {
        val uri = try {
            URI(baseUrl.trim().trimEnd('/'))
        } catch (_: Exception) {
            throw IllegalArgumentException("올바른 서버 주소를 입력해 주세요.")
        }
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.orEmpty() in listOf("", "/") && uri.port in -1..65535 && uri.port != 0) {
            "http://서버주소:포트 형식으로 입력해 주세요. API 경로는 제외합니다."
        }
        val url = URI("${uri.toString().trimEnd('/')}/api/v1/auth/check-login-id?loginId=ridemate_connection_test").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("서버가 HTTP $status 응답을 반환했습니다. 서버 주소와 실행 상태를 확인해 주세요.")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val payload = try { JSONObject(body) } catch (_: Exception) { null }
            if (payload?.opt("available") !is Boolean) {
                throw IOException("HTTP 200 응답은 받았지만 RideMate API 응답 형식이 아닙니다.")
            }
            status
        } finally {
            connection.disconnect()
        }
    }
}
