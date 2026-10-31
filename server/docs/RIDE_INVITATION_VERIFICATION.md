# 라이딩 초대 1단계 구현 및 검증

작업일: 2026-11-01

## 구현 범위

- `POST /api/v1/ride-invitations`: JWT 사용자 기준 그룹장 및 APPROVED 자격 검사, HTTP 201 응답.
- 필수값·양수 ID·1~49명·중복 ID·본인 포함·미승인 대상 검증.
- 선두와 초대 대상 모두 유효한 로그인 WebSocket 연결이 있는지 검사.
- 초대별 UUID, 생성 시각, 대상 및 최초 참여 상태를 단일 서버 메모리에 저장.
- 선두 ACCEPTED, 대상 PENDING, `acceptedCount=1`, `canStart=false`로 초기화.
- 대상의 모든 유효한 연결에 `RIDE_INVITE`, 선두의 모든 유효한 연결에 최초 `RIDE_INVITE_STATUS` 전송.
- Ping과 텍스트 전송은 같은 세션 잠금을 사용한다. 전송 실패 연결만 정리하고 나머지 연결 전송을 계속한다.
- 생성 후 30분 만료. 내부 조회는 만료 시각부터 제외하며, 60초 간격으로 메모리 상태를 정리한다.

Entity·DB 스키마·Repository·기존 인증 정책은 변경하지 않았다. `ddl-auto=validate`를 유지한다. 초대 생성은 기존 그룹/사용자 정보를 읽으며 라이딩 세션이나 그룹 가입 상태를 DB에 쓰지 않는다.

## 변경 파일

- `controller/RideInvitationController.java`: 생성 API와 요청 검증.
- `service/RideInvitationService.java`: 권한·대상·온라인 검증, 메모리 상태, 만료 정리, 최초 알림.
- `dto/CreateRideInvitationRequest.java`, `CreateRideInvitationResponse.java`: REST 계약.
- `dto/RideInviteMessage.java`, `RideInviteStatusMessage.java`: WebSocket 알림 계약.
- `websocket/OnlineUserRegistry.java`: 사용자별 텍스트 전송.
- 신규 Controller/Service 테스트와 기존 OnlineUserRegistry 테스트 확장.
- `docs/API_SPEC.md`: 1단계 구현 상태와 2단계 미구현 경계 표시.

Java 경로는 `src/main/java/com/ridesync/server/`, 테스트 경로는 `src/test/java/com/ridesync/server/` 기준이다.

## 검증 범위

| 테스트 | 확인 내용 |
|---|---|
| RideInvitationControllerTests | JWT 필수, 입력 검증, HTTP 201과 응답 형식, 인증 principal 전달 |
| RideInvitationServiceTests | 그룹장 권한, 승인 대상, 온라인 조건, 중복·본인 차단, 고유 ID, 초기 메시지 JSON, DB save 미호출, 30분 만료 경계·정리 |
| OnlineUserRegistryTests | 모든 유효 연결에 전송, 타 사용자·만료 연결 제외, 일부 전송 실패 후 계속 전송 및 연결 정리, 기존 Ping/Pong 회귀 |

신규 초대 테스트의 Repository와 전송 계층은 mock을 사용한다. 실제 초대 생성 HTTP부터 WebSocket 수신까지 연결한 통합 검증은 3단계에서 수행한다. 만료 테스트는 Clock을 제어하며 실제 30분 대기를 검증한 것은 아니다.

## 실행 결과 및 재실행

최종 실행 결과: `RIDEMATE_LIVE_TEST=true` 상태에서 `gradlew.bat --offline test build` 성공(2분 12초). JUnit XML 합계 71개, 실패·오류·건너뜀 0개. 기존 63개와 신규 8개를 포함하며 기존 실제 MySQL·HTTP·WebSocket 회귀 테스트도 실행했다. 신규 초대의 실제 네트워크 통합 검증까지 완료했다는 의미는 아니다.

첫 관련 테스트 실행에서 MVC 테스트 2개는 테스트용 UserRepository에 JWT 필터가 사용하는 `existsById`를 설정하지 않아 401로 실패했다. 테스트 설정을 수정한 뒤 위 전체 실행에서 통과했다. `git diff --check`도 통과했다.

재실행(기존 MySQL 설정 및 서버 필요):

```powershell
$previousLiveTest = $env:RIDEMATE_LIVE_TEST
$env:RIDEMATE_LIVE_TEST = 'true'
try {
    .\gradlew.bat --offline test build
} finally {
    if ($null -eq $previousLiveTest) {
        Remove-Item Env:RIDEMATE_LIVE_TEST -ErrorAction SilentlyContinue
    } else {
        $env:RIDEMATE_LIVE_TEST = $previousLiveTest
    }
}
```

## 후속 구현

- 2단계: 수락·거절, 참여 현황 갱신, ERROR 응답, 재접속 복구.
- 현재 LoginWebSocketHandler의 클라이언트 텍스트 수신은 기존처럼 1003으로 종료된다.
- 3단계: 신규 초대 흐름의 실제 REST·WebSocket 통합 검증.
- 라이딩 시작, `/ws/ride`, 종료·기록·통계는 별도 후속 작업이다.
