# 로그인 웹소켓 및 그룹원 온라인 상태 검증

검증일: 2026-10-31

## 최종 결과

- 중단 전 실행한 전체 테스트의 JUnit XML을 재개 후 확인했다: 63개 통과, 실패/오류/건너뜀 0개.
- 실제 생존 확인 테스트는 90.765초에 완료했다. 정상 연결은 유지되고 Pong 미응답 연결은 1008로 종료됐다.
- 최초 무응답 테스트는 Java HttpClient의 자동 Pong 때문에 시간 초과됐다. 원시 소켓으로 교체한 최종 실행은 통과했다.
- 재개 시 완료된 테스트 결과를 보존하고 `gradlew.bat build -x test`로 최종 빌드를 별도 확인했다: BUILD SUCCESSFUL. 테스트를 다시 실행한 결과로 집계하지 않았다.

## 범위

- JWT 인증을 사용하는 표준 WebSocket /ws/login
- 사용자별 복수 세션, 정상 종료·오류·JWT 만료·Pong 무응답 정리
- GET /api/v1/groups/{groupId}/members의 권한과 online 응답
- 기존 인증 및 그룹 관리 기능 회귀 검증

DB 스키마·Entity는 변경하지 않았다. 접속 정보는 단일 서버 프로세스 메모리에 저장한다.
목록에는 본인을 포함한 APPROVED 그룹원만 나오며, online은 REST 재조회 시 갱신된다.
접속 상태 변경을 클라이언트에 자동 방송하는 기능은 포함하지 않는다.

## 검증 방식

실제 테스트는 로컬 MySQL과 127.0.0.1 임시 포트의 Spring Boot 서버를 사용한다.
LoginWebSocketLiveTests는 실제 로그인 토큰과 웹소켓을 사용하며, Handler spy는 호출 관측용이다.
GroupLiveIntegrationTests는 실제 HTTP 요청으로 그룹원 목록을 조회한다.

| 항목 | 확인 방식 |
|---|---|
| 로그인 토큰으로 웹소켓 연결 | 실제 HTTP 로그인 후 웹소켓 연결 |
| 인증 실패 | 토큰 누락·변조·만료·형식 오류·삭제된 사용자 연결을 실제 HTTP 401로 거부 |
| 사용자 ID 위조 방지 | URL userId와 무관하게 인증 principal의 사용자 ID 사용 |
| URL 토큰 거부 | 헤더 없이 쿼리에만 토큰을 보내면 연결 거부 |
| 다중 연결 | 하나를 닫아도 온라인 유지, 마지막 연결 종료 시 오프라인 |
| 재연결·JWT 만료 | 종료 후 새 연결 성공, 짧은 토큰 만료 후 서버가 실제 소켓 종료 |
| 정상 생존 확인 | 실제 Ping 수신 및 Pong 응답, 90초 이상 정상 연결 유지 |
| Pong 무응답 | 원시 소켓으로 WebSocket 업그레이드 후 Ping만 수신, Pong 미전송 시 1008 종료 프레임 수신 |
| 그룹원 online | 실제 REST 응답에서 연결 전 false → 연결 후 true → 종료 후 false |
| 그룹원 권한·상태 | MySQL 및 MVC 테스트로 승인 멤버만 조회, 본인 포함, 대기·거절·미가입 제외/차단 |
| 이전 세션의 늦은 정리 | 단위 테스트로 이전 세션 제거가 새 세션에 영향 없음 확인 |
| 전송·종료 실패 | 단위 테스트에서 예외 주입 후 레지스트리 정리 확인 |
| 서버 종료 정리 | 단위 테스트로 전체 세션 정리 확인 |

Ping 주기는 30초, Pong 무응답 기준은 90초 초과다.
서버는 1초 간격으로 점검하므로 실제 종료에는 스케줄링 지연이 있을 수 있다.
무응답 테스트는 클라이언트 라이브러리의 자동 Pong을 피하기 위해 원시 소켓으로 제어 프레임을 읽는다.
전체 테스트 실행에는 이 실제 대기 시간을 포함한다.

## 재실행

프로젝트의 MySQL 연결 설정이 유효하고 MySQL이 실행 중이어야 한다.
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

RIDEMATE_LIVE_TEST 미설정 시 실제 HTTP·웹소켓 테스트는 건너뛴다.
일부 기존 DB 통합 테스트는 이 변수와 무관하게 MySQL이 필요하다.
테스트는 무작위 계정을 사용하고 생성 데이터만 롤백/삭제한다. 강제 종료 시 정리가 생략될 수 있다.
HTML 보고서: build/reports/tests/test/index.html

## 검증 한계 및 후속 범위

- Android 단말의 백그라운드 동작, 이동통신 단절, 외부 네트워크 및 프록시는 검증하지 않았다.
- 무응답 테스트는 Pong을 의도적으로 보내지 않는 상황이며 실제 무선 네트워크 단절 시험은 아니다.
- 여러 서버 인스턴스 간 접속 상태 공유와 대규모 부하는 현재 범위에 포함하지 않는다.
- 라이딩 초대·수락·시작, /ws/ride 및 실시간 수신호는 후속 개발이다.
- 연결 중 계정 삭제에 따른 즉시 강제 종료는 구현하지 않았다. 삭제된 계정의 신규 연결은 거부한다.
