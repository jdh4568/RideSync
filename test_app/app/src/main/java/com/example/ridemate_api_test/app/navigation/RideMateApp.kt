package com.example.ridemate_api_test.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.ridemate_api_test.core.di.AppContainer
import com.example.ridemate_api_test.feature.auth.presentation.login.LoginRoute
import com.example.ridemate_api_test.feature.auth.presentation.signup.SignupRoute
import com.example.ridemate_api_test.feature.group.presentation.GroupListRoute
import com.example.ridemate_api_test.feature.home.presentation.HomeRoute
import com.example.ridemate_api_test.feature.invitation.domain.model.InviteParticipant
import com.example.ridemate_api_test.feature.invitation.domain.model.ParticipantStatus
import com.example.ridemate_api_test.feature.invitation.domain.model.RideInviteStatusUiModel
import com.example.ridemate_api_test.feature.invitation.presentation.GroupSelectRoute
import com.example.ridemate_api_test.feature.invitation.presentation.MemberSelectRoute
import com.example.ridemate_api_test.feature.invitation.presentation.WaitingDialog
import com.example.ridemate_api_test.feature.ride.presentation.follower.FollowerRideRoute
import com.example.ridemate_api_test.feature.ride.presentation.leader.LeaderRideRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** DEMO: 로그인한 사용자(리더) 본인. 실제로는 로그인 응답의 userId 를 사용한다. */
private const val DEMO_SELF_USER_ID = 1L

/** DEMO: 초대 생성 API 가 돌려주는 invitationId 를 흉내 낸 값. */
private const val DEMO_INVITATION_ID = "inv-100"

@Composable
fun RideMateApp(container: AppContainer) {
    val backStack = remember { mutableStateListOf<Route>(Route.Login) }
    val current = backStack.last()
    val scope = rememberCoroutineScope()

    // TODO: 로그인 성공 시 저장한 userId 로 교체한다.
    val selfUserId = DEMO_SELF_USER_ID

    // 참여 현황: 값이 있으면 메인 화면 위에 대기 다이얼로그를 띄운다.
    var inviteStatus by remember { mutableStateOf<RideInviteStatusUiModel?>(null) }
    // 시작/취소 요청 진행 여부 — 두 버튼을 동시에 비활성화한다.
    var inviteProcessing by remember { mutableStateOf(false) }
    var inviteErrorRes by remember { mutableStateOf<Int?>(null) }

    val navigate: (Route) -> Unit = { route -> backStack.add(route) }
    val popBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    val resetToHome: () -> Unit = {
        backStack.clear()
        backStack.add(Route.Home)
    }
    val resetToLogin: () -> Unit = {
        backStack.clear()
        backStack.add(Route.Login)
    }

    // TODO: /ws/login 의 RIDE_INVITE_STATUS 메시지 구독 위치.
    //  - 현재 다이얼로그의 invitationId 와 일치하는 메시지만 처리한다.
    //  - 새 목록 전체로 교체한다(누적 금지).
    //  - 재연결 시에도 유효한 초대의 최신 현황을 다시 받아 초기 표시로 사용한다.
    //  DEMO: 수락 이벤트 1건을 흉내 내어 교체 갱신과 canStart 활성화를 확인할 수 있게 한다.
    LaunchedEffect(inviteStatus?.invitationId) {
        val snapshot = inviteStatus ?: return@LaunchedEffect
        if (snapshot.canStart) return@LaunchedEffect
        delay(2_000)
        val latest = inviteStatus
        if (latest == null || latest.invitationId != snapshot.invitationId || latest.canStart) {
            return@LaunchedEffect
        }
        var acceptedOne = false
        val replaced = latest.participants.map { participant ->
            if (!acceptedOne && participant.status == ParticipantStatus.PENDING) {
                acceptedOne = true
                participant.copy(status = ParticipantStatus.ACCEPTED)
            } else {
                participant
            }
        }
        inviteStatus = latest.copy(
            participants = replaced,
            acceptedCount = replaced.count { it.status == ParticipantStatus.ACCEPTED },
            canStart = replaced.count { it.status == ParticipantStatus.ACCEPTED } >= 2,
        )
    }

    Box(Modifier.safeDrawingPadding()) {
        when (current) {
            is Route.Login -> LoginRoute(
                onNavigateSignup = { navigate(Route.Signup) },
                onNavigateHome = resetToHome,
            )

            is Route.Signup -> SignupRoute(onBack = popBack, onSignedUp = { navigate(Route.Login) })

            is Route.Home -> HomeRoute(
                onOpenGroupList = { navigate(Route.GroupList) },
                onStartRide = { navigate(Route.GroupSelect) },
                onAcceptInvite = { navigate(Route.FollowerRide) },
            )

            is Route.GroupList -> GroupListRoute(onBack = popBack)

            is Route.GroupSelect -> GroupSelectRoute(
                onBack = popBack,
                onNext = { groupId -> navigate(Route.MemberSelect(groupId)) },
                onNavigateLogin = resetToLogin,
            )

            is Route.MemberSelect -> MemberSelectRoute(
                groupId = current.groupId,
                selfUserId = selfUserId,
                onBack = popBack,
                onInvite = { invited ->
                    // 초대 생성 성공: 메인 화면으로 이동하고 참여 현황 다이얼로그를 표시한다.
                    // 리더 본인은 자동 수락 상태이므로 목록에 ACCEPTED 로 포함된다.
                    inviteErrorRes = null
                    inviteProcessing = false
                    inviteStatus = RideInviteStatusUiModel(
                        invitationId = DEMO_INVITATION_ID,
                        participants = listOf(
                            InviteParticipant(selfUserId, "홍길동", ParticipantStatus.ACCEPTED),
                        ) + invited,
                        acceptedCount = 1,
                        canStart = false,
                    )
                    resetToHome()
                },
                onNavigateLogin = resetToLogin,
                onOpenGroupSelect = {
                    // 404 GROUP_NOT_FOUND: 그룹 선택 화면으로 되돌린다.
                    backStack.clear()
                    backStack.add(Route.Home)
                    backStack.add(Route.GroupSelect)
                },
            )

            is Route.LeaderRide -> LeaderRideRoute(onFinish = resetToHome)

            is Route.FollowerRide -> FollowerRideRoute(onFinish = resetToHome)
        }

        if (inviteStatus != null && current is Route.Home) {
            WaitingDialog(
                status = inviteStatus,
                selfUserId = selfUserId,
                processing = inviteProcessing,
                errorMessage = if (inviteErrorRes != null) stringResource(inviteErrorRes!!) else null,
                onStart = startRide@{
                    val snapshot = inviteStatus ?: return@startRide
                    if (inviteProcessing || !snapshot.canStart) return@startRide
                    inviteProcessing = true
                    inviteErrorRes = null
                    scope.launch {
                        // TODO: POST /api/v1/ride-invitations/{invitationId}/start (body 없음)
                        //  성공 200: rideSessionId 저장 → 대기 다이얼로그/대기 상태 정리
                        //            → GET /api/v1/rides/{rideSessionId} 조회 → ws/ride/{rideSessionId} 연결
                        //            → 리더 라이딩 화면 이동 (같은 rideSessionId 는 1회만 처리)
                        //  실패: resolveStartRideError(httpStatus, code) 매핑
                        //        clearInvitation → inviteStatus = null
                        //        openGroupList  → navigate(Route.GroupList)
                        //        409 INSUFFICIENT_PARTICIPANTS → 대기 유지(정리하지 않음)
                        //  RIDE_STARTED 알림을 먼저 처리했다면 이후 실패 응답으로 화면을 벗어나지 않는다.
                        delay(700) // DEMO: 요청 지연 흉내
                        inviteProcessing = false
                        inviteStatus = null
                        inviteErrorRes = null
                        navigate(Route.LeaderRide)
                    }
                },
                onCancel = cancelRide@{
                    if (inviteProcessing) return@cancelRide
                    inviteProcessing = true
                    inviteErrorRes = null
                    scope.launch {
                        // TODO: POST /api/v1/ride-invitations/{invitationId}/cancel (body 없음)
                        //  성공 200: 해당 초대의 다이얼로그/대기 상태 정리, 메인 화면 유지, /ws/login 연결 유지
                        //  실패: resolveCancelRideError(httpStatus, code) 매핑
                        //        404 INVITATION_NOT_FOUND 는 '취소 성공'으로 단정하지 않는다.
                        //  RIDE_INVITE_CANCELLED 알림이 먼저 와도 정리는 1회만 수행한다.
                        delay(500) // DEMO: 요청 지연 흉내
                        inviteProcessing = false
                        inviteStatus = null
                        inviteErrorRes = null
                    }
                },
                onDismissError = { inviteErrorRes = null },
            )
        }
    }
}
