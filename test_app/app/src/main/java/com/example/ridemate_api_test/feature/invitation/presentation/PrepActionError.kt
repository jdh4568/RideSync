package com.example.ridemate_api_test.feature.invitation.presentation

import com.example.ridemate_api_test.R

/**
 * 라이딩 준비(그룹 선택·멤버 선택) 화면이 실패 응답에 취하는 동작 묶음.
 *
 * - [navigateLogin]: 다시 로그인 안내 후 로그인 화면으로 이동한다.
 * - [openGroupSelect]: 그룹 선택 화면으로 되돌린다.
 * - [reloadMembers]: 멤버 목록을 다시 조회한다.
 */
data class PrepActionError(
    val messageRes: Int,
    val navigateLogin: Boolean = false,
    val openGroupSelect: Boolean = false,
    val reloadMembers: Boolean = false,
)

/** `GET /api/v1/groups/owned` 실패 매핑. 빈 배열과는 다른 문구를 쓴다. */
fun resolveOwnedGroupsError(httpStatus: Int): PrepActionError = when (httpStatus) {
    401 -> PrepActionError(R.string.ride_error_unauthorized, navigateLogin = true)
    else -> PrepActionError(R.string.select_group_error)
}

/** `GET /api/v1/groups/{groupId}/members` 실패 매핑. */
fun resolveMembersError(httpStatus: Int, code: String?): PrepActionError = when {
    httpStatus == 401 -> PrepActionError(R.string.ride_error_unauthorized, navigateLogin = true)
    httpStatus == 403 -> PrepActionError(R.string.select_member_forbidden)
    code == "GROUP_NOT_FOUND" -> PrepActionError(R.string.group_not_found, openGroupSelect = true)
    else -> PrepActionError(R.string.select_member_error)
}

/**
 * `POST /api/v1/ride-invitations` 실패 매핑.
 *
 * - 400 `INVALID_INVITEE` / 409 `USER_OFFLINE` 는 멤버 목록을 다시 조회한다.
 * - 404 `GROUP_NOT_FOUND` 는 그룹 선택 화면으로 되돌린다.
 * - 네트워크 오류는 자동 재요청하지 않는다(중복 초대 생성 방지).
 */
fun resolveCreateInviteError(httpStatus: Int, code: String?): PrepActionError = when {
    httpStatus == 401 -> PrepActionError(R.string.ride_error_unauthorized, navigateLogin = true)
    httpStatus == 403 -> PrepActionError(R.string.invite_create_forbidden)
    code == "GROUP_NOT_FOUND" -> PrepActionError(R.string.group_not_found, openGroupSelect = true)
    httpStatus == 400 && code == "INVALID_INVITEE" ->
        PrepActionError(R.string.invite_invalid_invitee, reloadMembers = true)
    httpStatus == 400 -> PrepActionError(R.string.invite_invalid_request)
    httpStatus == 409 && code == "USER_OFFLINE" ->
        PrepActionError(R.string.invite_user_offline, reloadMembers = true)
    else -> PrepActionError(R.string.invite_network_error)
}
