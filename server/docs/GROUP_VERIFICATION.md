# 그룹 관리 기능 검증 결과

검증일: 2026-10-30

후속 진행(2026-10-31): 아래는 그룹 REST 7개 완료 당시 기록이다.
이후 온라인 상태를 포함한 그룹원 조회도 구현했으며, 웹소켓 최종 검증은
[WEBSOCKET_VERIFICATION.md](WEBSOCKET_VERIFICATION.md)를 참고한다.

## 완료 범위

DATABASE.md와 API_SPEC.md 기준으로 다음 REST API 7개를 구현하고 검증했다.
모든 API는 JWT 인증을 요구한다.

| 메서드 | 경로 | 성공 상태 |
|---|---|---|
| POST | /api/v1/groups | 201 |
| GET | /api/v1/groups | 200 |
| GET | /api/v1/groups/my | 200 |
| POST | /api/v1/groups/{groupId}/join | 200 |
| GET | /api/v1/groups/join-requests | 200 |
| POST | /api/v1/groups/{groupId}/requests/{userId}/approve | 200 |
| POST | /api/v1/groups/{groupId}/requests/{userId}/reject | 200 |

그룹 생성·그룹장 등록은 단일 트랜잭션이다. 인원 집계는 그룹장을 포함한 APPROVED만 센다.
가입 신청 시 정원 검사는 하지 않으며 승인할 때 검사한다.
가입 신청·승인·거절은 같은 그룹 행을 먼저 잠근다.
승인 인원은 잠금 조회로 확인하여 동시 승인 시에도 최신 커밋 결과를 반영한다.
Entity와 DB 스키마 및 ddl-auto=validate는 유지했다.

## 실제 실행 결과

RIDEMATE_LIVE_TEST=true 상태에서 gradlew.bat clean test build 성공.
JUnit XML 기준 총 54개 테스트 통과, 실패 0개, 건너뜀 0개.
기존 인증 실제 HTTP 테스트와 그룹 DB 동시성 테스트도 포함한다.

| 검증 | 방식 및 결과 |
|---|---|
| 회원가입·로그인 → 그룹 생성 | 실제 HTTP/MySQL, 201 및 그룹장 자동 등록 확인 |
| 전체·내 그룹 조회 | 실제 HTTP/MySQL, 미가입 null, 승인 전 내 그룹 제외, 승인 후 포함 |
| 대기 신청 통합 조회 | 실제 HTTP/MySQL, 소유 그룹 2개의 신청 통합, 다른 소유자 그룹 제외 |
| 거절 → 재신청 → 승인 | 실제 HTTP/MySQL, 기존 group_member_id 재사용, 가입 시각 저장 |
| 권한 및 중복 처리 | 실제 HTTP 및 MVC 테스트, 비그룹장 403, 중복·완료된 신청 409 |
| 정원 초과 | 실제 HTTP/MySQL, GROUP_FULL 409, 신청은 PENDING 유지 |
| 처리 후 대기 목록 | 실제 HTTP/MySQL, 승인·거절한 신청 제외, 빈 배열 확인 |
| 동시 가입 신청 2건 | 실제 MySQL 서비스 테스트, 1건 성공·1건 충돌, 신청 레코드 1개 |
| 마지막 자리 동시 승인 2건 | 실제 MySQL 서비스 테스트, 1건 승인·1건 GROUP_FULL, 정원 유지 |
| 생성 원자성 | 그룹장 저장에 예외 주입, 실제 그룹 INSERT가 롤백되어 그룹·가입 레코드 0건 |
| 입력·인증·없는 대상 | MVC 테스트, 길이·정원 경계값 및 400/401/403/404/409 확인 |

GroupLiveIntegrationTests는 127.0.0.1 임시 포트의 서버에 HTTP 요청을 보낸다.
정상 흐름 테스트는 실제 Repository와 MySQL을 사용한다.
별도 롤백 테스트에서만 Repository spy에 저장 실패를 주입하고 실제 DB 롤백 결과를 조회한다.
GroupQueryIntegrationTests의 동시성 검증은 HTTP 부하 시험이 아닌 서비스 호출 검증이다.

## 오류 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| INVALID_REQUEST | 400 | 요청 형식·입력 검증 실패 |
| UNAUTHORIZED / INVALID_TOKEN | 401 | 인증 누락 / 잘못된 인증 |
| FORBIDDEN | 403 | 소유 그룹 없음 또는 해당 그룹장이 아님 |
| GROUP_NOT_FOUND | 404 | 그룹 없음 |
| JOIN_REQUEST_NOT_FOUND | 404 | 대상 가입 레코드 없음 |
| GROUP_JOIN_CONFLICT | 409 | 이미 가입했거나 신청 대기 중 |
| JOIN_REQUEST_CONFLICT | 409 | 처리 대상이 PENDING이 아님 |
| GROUP_FULL | 409 | 승인 인원이 정원에 도달 |

## 재실행

실행 가능한 MySQL과 프로젝트의 DB 연결 설정이 필요하다.
서버 프로젝트 디렉터리에서 실행한다.

```powershell
$previousLiveTest = $env:RIDEMATE_LIVE_TEST
$env:RIDEMATE_LIVE_TEST = 'true'
try {
    .\gradlew.bat clean test build
} finally {
    if ($null -eq $previousLiveTest) {
        Remove-Item Env:RIDEMATE_LIVE_TEST -ErrorAction SilentlyContinue
    } else {
        $env:RIDEMATE_LIVE_TEST = $previousLiveTest
    }
}
```

RIDEMATE_LIVE_TEST 미설정 시 AuthLiveIntegrationTests와 GroupLiveIntegrationTests는 건너뛴다.
기존 contextLoads 및 GroupQueryIntegrationTests는 이 변수와 관계없이 실제 MySQL이 필요하다.
검증 계정은 무작위 식별자를 사용한다. 테스트 데이터는 롤백 또는 finally에서 해당 데이터만 삭제한다.
프로세스 강제 종료 시 finally 정리가 실행되지 않을 수 있다.
테스트 보고서: build/reports/tests/test/index.html

## 남은 범위

- 그룹 멤버 조회(GET /api/v1/groups/{groupId}/members)는 online 필드의 WebSocket 의존성 때문에 보류했다.
- WebSocket, 라이딩 초대·시작·종료·기록 및 Android 실제 단말 연동은 이번 완료 범위에 포함하지 않는다.
- 이 결과는 로컬 MySQL과 로컬 HTTP 검증이며, 운영 환경 부하·외부 네트워크 검증을 의미하지 않는다.
- 그룹 수정·삭제·탈퇴·강퇴는 현재 명세 및 구현 범위에 포함하지 않는다.
