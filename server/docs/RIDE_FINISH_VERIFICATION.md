# 라이딩 종료 2단계 검증 (2026-11-02)

## 구현

- POST /api/v1/rides/{rideSessionId}/finish: 세션 선두 및 LEADER 참여 기록 검사.
- RIDING → FINISHED, endedAt 저장. 선두의 재요청은 최초 결과를 반환하며 재알림·시각 갱신 없음.
- RideFinishService의 독립 트랜잭션이 커밋된 뒤 RIDE_FINISHED 전송 및 해당 라이딩 연결 정상 종료.
- DB 실패 시 알림·연결 정리 없음. /ws/login과 다른 라이딩 연결 유지.
- 고정 64개 잠금 중 세션 ID에 대응하는 잠금을 연결 등록·신호·종료에서 공유한다. 동일 버킷에 속한 다른 세션은 잠시 직렬 처리될 수 있지만 세션 ID별 메시지 격리는 유지된다. 잠금 객체의 무한 누적은 없다.
- DB 행 잠금으로 종료 상태 변경을 직렬화한다. 프로세스 내 잠금은 커밋·알림·연결 정리까지 유지한다.
- Entity·DB 스키마·ddl-auto=validate 변경 없음. 개인 기록 저장은 미구현.

## 테스트

RideWebSocketLiveTests에 3개 추가:

1. 실제 REST 종료 권한/인증/없는 세션 검사, DB 상태·종료 시각, 정확한 종료 알림, 다중 연결 종료, 다른 라이딩 및 로그인 연결 유지, 재요청 동일 결과, 재접속 거부, 참여 행 보존.
2. 실제 MySQL UPDATE·flush 후 테스트 예외 주입. 트랜잭션 내부 변경 확인 후 별도 조회에서 RIDING·ended_at NULL로 롤백 확인. 종료 알림 없음, 연결과 수신호 유지, 재시도 성공.
3. 동시 종료 2건·수신호·새 연결 경쟁. 종료 응답 일치, 종료 메시지 1회 및 마지막 메시지임을 검사. 새 연결은 종료 전 등록 후 종료 알림을 받거나 종료 후 오류로 거부됨.

RideFinishServiceTests에 3개 추가:

- 커밋 후 알림 순서, 재요청 시 저장/알림 중복 없음.
- 모의 커밋 예외 시 알림·정리 미실행.
- READY 및 비선두 거부.

## 실행

```powershell
$env:RIDEMATE_LIVE_TEST='true'
./gradlew.bat test build --console=plain
```

## 실행 결과

- 2026-11-02 전체 실행: 117개 통과, 실패 0, 오류 0, 건너뜀 0. BUILD SUCCESSFUL (2분 55초).
- `build/test-results/test/TEST-*.xml` 합산. 라이딩 WebSocket 라이브 테스트 5개(기존 2 + 종료 3), 종료 서비스 단위 테스트 3개 포함.
- `git diff --check` 통과. deprecated API/JVM class sharing 경고는 남아 있으나 테스트·빌드 실패는 없다.

## 한계

실제 MySQL·로컬 HTTP/WebSocket 검증이다. Android·외부 네트워크·부하·다중 서버 배포 검증은 아니다. 롤백 검증은 실제 UPDATE 후 의도적 예외이며 DB 서버 장애·COMMIT 응답 유실 재현은 아니다. 커밋 실패 경로는 모의 트랜잭션 매니저로 별도 확인한다. 경쟁 테스트는 실행 시 발생한 순서의 일관성을 확인한다.
