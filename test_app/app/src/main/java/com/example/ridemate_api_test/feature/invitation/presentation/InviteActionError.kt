package com.example.ridemate_api_test.feature.invitation.presentation

import com.example.ridemate_api_test.R

/**
 * 시작/취소 요청 실패 시 화면이 취해야 하는 동작 묶음.
 *
 * - [clearInvitation]: 참여 현황 다이얼로그와 대기 상태를 정리한다.
 * - [openGroupList]: 그룹 목록으로 이동한다.
 */
data class InviteActionError(
    val messageRes: Int,
    val clearInvitation: Boolean = false,
    val openGroupList: Boolean = false,
)

/**
 * `POST /api/v1/ride-invitations/{id}/start` 실패 응답 매핑.
 * 본문 `code` 가 없으면 HTTP 상태로만 판단한다.
 *
 * - 409 `INSUFFICIENT_PARTICIPANTS` 는 대기 상태를 유지한다(정리하지 않음).
 * - 404 `GROUP_NOT_FOUND` 는 그룹 목록으로 이동한다.
 */
fun resolveStartRideError(httpStatus: Int, code: String?): InviteActionError = when {
    httpStatus == 401 -> InviteActionError(R.string.ride_error_unauthorized)
    httpStatus == 403 -> InviteActionError(R.string.waiting_start_forbidden)
    code == "INVITATION_NOT_FOUND" -> InviteActionError(R.string.invite_not_found, clearInvitation = true)
    code == "GROUP_NOT_FOUND" -> InviteActionError(R.string.group_not_found, openGroupList = true)
    code == "INSUFFICIENT_PARTICIPANTS" -> InviteActionError(R.string.waiting_insufficient_participants)
    code == "INVALID_PARTICIPANT" -> InviteActionError(R.string.waiting_invalid_participant)
    else -> InviteActionError(R.string.invite_connection_error)
}

/**
 * `POST /api/v1/ride-invitations/{id}/cancel` 실패 응답 매핑.
 *
 * 404 `INVITATION_NOT_FOUND` 는 다이얼로그를 정리하지만 **취소 성공으로 단정하지 않는다**
 * (이미 취소/만료/시작된 경우를 구분할 수 없음). 사용자에게는 유효하지 않다는 안내만 노출한다.
 */
fun resolveCancelRideError(httpStatus: Int, code: String?): InviteActionError = when {    httpStatus == 401 -> InviteActionError(R.string.ride_error_unauthorized)
    httpStatus == 403 -> InviteActionError(R.string.waiting_cancel_forbidden)
    code == "INVITATION_NOT_FOUND" -> InviteActionError(R.string.invite_not_found, clearInvitation = true)
    else -> InviteActionError(R.string.invite_connection_error)
}
