# 라이딩 초대 1~3단계 구현 및 검증

작업일: 2026-11-01

## 1단계 구현 범위

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

1단계 초대 테스트의 Repository와 전송 계층은 mock을 사용한다. 실제 초대 생성 HTTP부터 WebSocket 수신까지 연결한 통합 검증 결과는 아래 3단계를 참고한다. 만료 테스트는 Clock을 제어하며 실제 30분 대기를 검증한 것은 아니다.

## 1단계 실행 결과 및 재실행

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

## 2단계 구현 및 검증

- `LoginWebSocketHandler`: 엄격한 JSON 필드 검증, 인증된 연결 사용자로 응답 처리, 요청 연결에만 ERROR 전송, 연결 등록 후 복구 호출.
- `RideInvitationService`: PENDING에서 한 번만 응답 확정, 동일 응답은 무시하고 반대 응답은 충돌 오류, 선두에게 전체 현황 갱신. 초대별 잠금으로 최초 알림·응답·재접속 복구·정리를 직렬화한다.
- `OnlineUserRegistry`: 특정 연결에만 메시지를 보내는 메서드와 연결 유효성 기반 사용자 조회 추가. 기존 사용자별 방송·Ping과 같은 세션 잠금을 사용한다.
- `WebSocketErrorMessage`: 명세의 type/code/message 오류 DTO.
- 재접속한 선두는 유효한 초대의 최신 현황을 받고, 대상은 PENDING 초대만 받는다. 기존 연결에는 복구 메시지를 보내지 않으며, 만료·응답 완료·타인 초대는 대상 복구에서 제외한다.
- 응답·재접속으로 생성 시각을 갱신하지 않는다. 연결 종료는 초대 상태를 바꾸지 않는다.

추가 테스트:

| 테스트 | 확인 내용 |
|---|---|
| RideInvitationServiceTests 추가 5개 | 수락·거절·멱등성·반대 응답 충돌, 비초대자/선두/만료 차단, 복구 대상과 최신 현황, 같은 사용자의 상반된 동시 응답, 다른 참여자의 동시 응답과 복구 순서 |
| LoginWebSocketHandlerTests 신규 3개 | 등록 후 복구 호출, 사용자 ID 위조 무시, JSON 형식/boolean/후행 JSON 검증, 오류 수신 연결 격리와 연결 유지, 만료·제거된 연결 응답 차단 |

2단계 서비스 테스트는 Repository와 전송 객체를 mock으로 대체한다. Handler 테스트는 실제 OnlineUserRegistry와 JsonMapper를 사용하되 Service 및 WebSocketSession은 mock이다. 실제 초대 HTTP→WebSocket→응답→재접속 전체 흐름의 검증 결과는 아래 3단계를 참고한다.

2단계 최종 결과: `RIDEMATE_LIVE_TEST=true`로 `gradlew.bat --offline test build` 성공(2분 15초). JUnit XML 합계 **79개 통과, 실패·오류·건너뜀 0개**. 기존 71개와 신규 8개를 포함하며 기존 실제 MySQL·HTTP·WebSocket 회귀 테스트도 통과했다. 관련 테스트 선행 실행과 `git diff --check`도 통과했다. DB 스키마·Entity 및 `ddl-auto=validate`는 변경하지 않았다.

## 3단계 실제 통합 검증

추가 파일: `src/test/java/com/ridesync/server/RideInvitationLiveIntegrationTests.java`.

로컬 MySQL 및 127.0.0.1 임시 포트의 실제 Spring Boot 서버를 사용한다. 서버의 Service, Repository, Handler, 연결 레지스트리를 mock/spy로 대체하지 않는다. 계정은 실제 회원가입·로그인 API로 만들고, 그룹 생성·가입 신청·승인도 HTTP로 수행한다. WebSocket 텍스트는 실제 클라이언트 수신 큐에서 확인한다.

| 테스트 | 실제 검증 내용 |
|---|---|
| fullInvitationLifecycleWithMultipleConnectionsErrorsAndReconnect | 선두·대상 다중 연결의 초대/최초 현황, 비초대자와 선두의 응답 차단, 사용자 ID 위조 무시, 잘못된 JSON/boolean 오류 후 연결 유지, 수락·거절·동일 재전송·반대 응답 충돌, PENDING 대상 재접속 초대, 선두 재접속 최신 현황, 응답 완료 대상의 팝업 재전송 제외, 없는 초대 오류 |
| creationRejectsInvalidPermissionsTargetsAndOfflineUsersWithoutMessages | 미인증·일반 멤버 생성 차단, 빈 목록·중복·본인 포함 차단, 미승인 대상 차단, 대상/선두 오프라인 차단, 실패 후 알림이 전송되지 않음 |
| concurrentRepliesFromTwoDevicesCommitOnceAndBroadcastInOrder | 같은 사용자의 두 실제 연결에서 수락/거절 동시 전송, 한 상태만 확정, 패배한 응답에만 충돌 오류, 선두에게 중복 없는 현황, 새 선두 연결에 동일한 최신 현황 복구 및 기존 연결에 복구 메시지 미전송 |

각 시나리오에서 해당 그룹의 RIDE_SESSION 및 RIDE_SESSION_MEMBER가 생성되지 않았는지 SQL로 확인한다. 전체 흐름에서는 승인된 그룹원 수가 유지되는지도 확인한다. 종료 시 테스트가 생성한 그룹·가입 관계·계정만 삭제하고 잔여 그룹/계정이 없는지 확인한다. 소켓은 종료하며, 테스트 클래스 종료 시 Spring 컨텍스트도 닫아 임시 초대 메모리를 해제한다.

신규 통합 테스트만 실행한 결과: 3개 통과, 빌드 성공(50초). 제품 코드 수정 없이 검증했다.

최종 전체 실행: 2026-11-01, `RIDEMATE_LIVE_TEST=true`로 `gradlew.bat --offline test build` 성공(2분 19초). JUnit XML 합계 **82개 통과, 실패·오류·건너뜀 0개**. 신규 실제 초대 통합 테스트 3개와 기존 79개를 포함한다. 전체 실행의 신규 통합 테스트 3개 소요 시간은 약 8.5초다. 기존 인증·그룹·JWT 만료·실제 Ping/Pong 테스트도 실행했다. 기존 main 산출물은 변경이 없어 Gradle이 재사용했으며 전체 test 작업은 실제 재실행했다. `git diff --check`도 통과했다.

이번 단계 변경 파일은 통합 테스트 1개와 API_SPEC.md, 본 검증 문서다. 제품 코드·DB 스키마·Entity·`ddl-auto=validate`는 변경하지 않았다.

### 검증 한계

- 실제 검증은 로컬 PC의 MySQL·루프백 HTTP/WebSocket 환경이다. Android 단말, 화면 전환, 이동통신 단절, 외부 프록시 및 다중 서버 환경은 검증하지 않았다.
- 추가 메시지가 없는지는 기대 메시지 처리 후 250ms의 수신 큐 관측으로 검사한다. 무기한 미전송 보장은 아니다.
- 초대 30분 만료는 기존 Clock 기반 단위 테스트로 검증했다. 실제 30분 대기나 서버 재시작 후 클라이언트 화면 정리는 이번 통합 테스트에 포함하지 않는다.
- 전송 실패 주입은 기존 연결 레지스트리 단위 테스트의 범위이며, 이번 통합 테스트에서는 실제 정상 종료·재접속을 검증한다.
- 테스트는 강제 종료되면 데이터 정리가 완료되지 않을 수 있다. 전체 테이블 삭제나 스키마 변경은 수행하지 않는다.

## 후속 구현

- 라이딩 시작, `/ws/ride`, 종료·기록·통계는 별도 후속 작업이다.
