package com.example.ridemate_api_test.feature.invitation.domain.model

/**
 * /ws/login `RIDE_INVITE_STATUS` 메시지의 참여자 상태.
 */
enum class ParticipantStatus {
    ACCEPTED,
    PENDING,
    REJECTED,
}

/**
 * 참여 현황의 참여자 한 명.
 * 서버 목록에는 초대를 자동 수락한 리더 본인도 포함된다.
 */
data class InviteParticipant(
    val userId: Long,
    val nickname: String,
    val status: ParticipantStatus,
)

/**
 * 라이딩 참여 현황 다이얼로그가 사용하는 상태.
 *
 * - 같은 `invitationId` 에 대해 새 메시지가 오면 누적하지 말고 **전체를 교체**한다.
 * - `participants` / `acceptedCount` 는 리더 본인을 포함한 값이다.
 * - `canStart` 는 서버 값을 그대로 신뢰한다(수락 인원 2명 이상 등 서버 판단).
 */
data class RideInviteStatusUiModel(
    val invitationId: String,
    val participants: List<InviteParticipant>,
    val acceptedCount: Int,
    val canStart: Boolean,
)

/** 다이얼로그에 표시하는 4개 숫자. */
data class InviteCounts(
    val invited: Int,
    val accepted: Int,
    val pending: Int,
    val rejected: Int,
)

/**
 * 스펙의 계산 규칙:
 * - 요청(초대) = 본인을 제외한 인원
 * - 수락 = 본인을 제외하고 `ACCEPTED` 인 인원
 * - 대기 = `PENDING` 인원
 * - 거절 = `REJECTED` 인원
 */
fun RideInviteStatusUiModel.counts(selfUserId: Long): InviteCounts {
    val others = participants.filter { it.userId != selfUserId }
    return InviteCounts(
        invited = others.size,
        accepted = others.count { it.status == ParticipantStatus.ACCEPTED },
        pending = participants.count { it.status == ParticipantStatus.PENDING },
        rejected = participants.count { it.status == ParticipantStatus.REJECTED },
    )
}

