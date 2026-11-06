package com.example.ridemate_api_test.feature.connection.domain.repository

interface ConnectionRepository {
    /** 공개 조회 API를 호출하며 서버 데이터를 변경하지 않는다. */
    suspend fun checkConnection(baseUrl: String): Int
}
