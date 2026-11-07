# 개인 주행 기록 저장 3단계 검증 (2026-11-02)

## 범위

- PUT /api/v1/rides/{rideSessionId}/record 구현. FINISHED 세션 참여자 자신의 기존 행만 수정.
- BigDecimal 및 필수·0 이상·정수 8자리/소수 2자리 검증. 초과는 400이며 반올림하지 않음.
- 참여 행 PESSIMISTIC_WRITE 잠금과 단일 트랜잭션으로 동시 요청의 거리/속도가 섞이지 않도록 처리.
- 기존 RideController·RideService·RideSessionMemberRepository 확장, 요청·응답 DTO 2개 추가.
- 스키마·Entity·ddl-auto=validate 유지. 메인 통계 및 최근 라이딩 조회는 이번 범위에서 제외.

## 테스트

- Controller: 필수값 누락/null, 음수, 소수 자리 초과, 정수 범위 초과 거부. 0·99999999.99 경계 허용, JWT 사용자 기준 전달, 정확한 응답 필드.
- Service: READY·RIDING 상태 거부와 기존 값 유지.
- 실제 MySQL·HTTP·WebSocket: 시작 → 수신호 → 종료 → 선두/후미 기록 저장. 미인증·비참여·없는 세션·종료 전 요청 거부. 재전송 갱신, 다른 참여자 기록 보호, 오류 입력 후 DB 값 보존.
- 동시 HTTP 저장: 요청별 두 값이 한 쌍으로 반영되고 참여 행이 추가되지 않는지 검증.
- 테스트 생성 계정·그룹·세션만 정리.

## 실행

```powershell
$env:RIDEMATE_LIVE_TEST='true'
./gradlew.bat test build --console=plain
```

## 한계

전체 실행 결과(2026-11-02): 테스트 122개 통과, 실패 0, 오류 0, 건너뜀 0. BUILD SUCCESSFUL (3분 7초). `build/test-results/test/TEST-*.xml` 합산으로 확인했다. `git diff --check` 통과. 기존 deprecated API 및 JVM class sharing 경고는 남아 있다.

실제 로컬 MySQL·HTTP·WebSocket 검증이며 Android GPS 계산·외부 네트워크·부하 검증은 아니다. 기록 값은 Android가 계산하여 제출한 값이며 서버가 GPS로 재계산하지 않는다. 동시 저장의 최종값은 마지막 반영 요청이며 클라이언트 발신 시각 순서를 보장하지 않는다.
