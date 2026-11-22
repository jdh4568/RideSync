package com.example.ridemate_api_test.app.navigation

/**
 * 앱 화면 라우트. 현재는 단일 Activity + Compose 상태 내비게이션을 사용한다.
 * 추후 REST/WebSocket 연동 시 Navigation Compose로 교체하기 쉽도록 라우트만 분리해 둔다.
 */
sealed class Route(val label: String) {
    data object Login : Route("login")
    data object Signup : Route("signup")
    data object Home : Route("home")
    data object GroupList : Route("group_list")
    data object GroupSelect : Route("group_select")
    /** 라이딩 준비(2): 직전 화면에서 선택한 groupId 를 함께 들고 이동한다. */
    data class MemberSelect(val groupId: Long) : Route("member_select")

    data object LeaderRide : Route("leader_ride")
    data object FollowerRide : Route("follower_ride")
}
