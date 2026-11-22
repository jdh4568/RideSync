package com.example.ridemate_api_test.feature.invitation.domain.model

/**
 * WS(/ws/login) RIDE_INVITE 메시지를 표현한 초대 모델.
 * invitationId 로 수락/거절 응답과 취소/오류 정리를 식별한다.
 */
data class RideInviteUiModel(
    val invitationId: String,
    val groupId: Long,
    val groupName: String,
    val leaderId: Long,
    val leaderNickname: String,
)
