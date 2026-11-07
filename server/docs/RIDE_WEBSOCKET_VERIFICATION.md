# 라이딩 WebSocket 1단계 검증 (2026-11-02)

## 구현 범위

- /ws/ride/{rideSessionId}: JWT 인증, RIDING 상태 및 세션 참여 기록 검증.
- RIDE_SIGNAL: LEADER만 발신, 같은 세션 MEMBER의 모든 유효한 연결에 명세의 8종 신호 전달.
- senderId와 세션은 인증된 사용자·접속 경로로 결정한다. LEADER_CHANGE는 신호 전달이며 역할을 변경하지 않는다.
- 별도 RideConnectionRegistry에서 다중 연결, JWT 만료, Ping/Pong, 전송 실패·연결 종료를 관리한다. /ws/login 연결과 온라인 상태에 섞지 않는다.
- JWT 필터의 만료 시각 전달 대상을 기존 /ws/login에서 /ws/ride/ 경로까지 확장했다.
- 연결 종료로 세션이나 참여 기록을 변경하지 않는다. 신호 DB 저장·재접속 재전송은 없다.
- 종료 REST·종료 알림·종료 시 일괄 정리·기록 저장은 후속 단계이다. 종료 처리와 신호/새 연결 경쟁 제어 역시 종료 단계에서 통합한다.

## 검증

- RideWebSocketLiveTests: 실제 로컬 MySQL·HTTP로 가입/그룹/초대/수락/시작 후 실제 WebSocket 연결.
- 8종 신호와 정확한 senderId, 동일 세션 다중 연결, 다른 세션 격리, 후미 발신 차단, 잘못된 JSON·신호·type 오류, 재접속 및 역할 불변 확인.
- 미인증 handshake 401, 비참여자·없는 세션의 ERROR 후 종료, FINISHED 신규 연결 및 발신 차단 확인. FINISHED는 테스트가 만든 행만 직접 변경해 검사하며 종료 API 검증이 아니다.
- RideConnectionRegistryTests: 전송 실패 격리, 선두 제외·세션 격리, JWT 만료, Ping/Pong 시간 조건, 종료/실패 후 메모리 정리.
- RideServiceTests: READY·FINISHED 거부, 참여 기록 기반 역할 조회.
- 테스트 생성 데이터만 외래키 순서대로 정리한다. 기존 Entity·스키마·ddl-auto=validate는 유지한다.

## 실행

```powershell
$env:RIDEMATE_LIVE_TEST='true'
./gradlew.bat test build --console=plain
```

## 최종 실행 결과

- 중단 전에 시작한 Gradle 실행의 완료 출력을 재확인했다. 전체 111개 통과, 실패 0, 오류 0, 건너뜀 0, BUILD SUCCESSFUL이다.
- `build/test-results/test/TEST-*.xml` 합산 결과이며 라이딩 WebSocket 라이브 테스트 2개와 연결 관리 테스트 3개가 포함된다.
- 결과 XML 생성 이후 src 파일 변경이 없음을 확인했다. 재개 시 코드를 다시 작성하거나 전체 테스트를 중복 실행하지 않고 결과 기록을 보완했다.
- Gradle 경과 시간은 31분 47초로 출력되었다. 중단을 포함한 실행의 경과 시간이며 서버 처리 성능 수치가 아니다.
- `git diff --check` 통과. 기존 deprecated API 및 JVM class sharing 경고는 있으나 빌드 실패는 없다.

## 한계

Android·외부 네트워크·부하 성능은 미검증이다. 라이딩 연결의 JWT 만료와 Ping/Pong 시간 조건은 단위 테스트로 확인했으며, 실제 90초 대기 테스트를 새 라이딩 경로에 추가하지 않았다. 전체 회귀에 포함된 기존 로그인 WebSocket 테스트에는 실제 시간 경과 검증이 있다.
