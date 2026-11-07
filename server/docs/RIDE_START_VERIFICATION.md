# 라이딩 시작·조회 검증 (2026-11-02)

## 범위

- API_SPEC 8.4 시작 REST, 9.3 RIDE_STARTED, 10.1 세션 참여자 조회.
- 기존 MySQL 스키마·Entity 매핑·ddl-auto=validate 유지.
- 이번 단계는 통합 테스트 추가와 검증이며 운영 코드 추가 변경 없음.

## 실제 MySQL·HTTP·WebSocket 테스트

`RideInvitationLiveIntegrationTests`에 아래 5개를 추가했다. 기존 초대 테스트 3개를 합쳐 8개이다.

1. 초대 → 수락·거절 → 시작 → 다중 연결 알림 → 참여자 조회. PENDING·REJECTED 저장/알림 제외, 정확한 역할·시간·NULL 기록 필드 확인. 미인증·비참여·없는 세션 오류와 시작 후 초대 재사용/복구 차단 확인.
2. 동시 HTTP 시작 2건 중 200 하나·404 하나, DB 세션 1개와 참여자 2개, 알림 중복 없음 확인.
3. 실제 세션·참여자 INSERT 및 flush 후 테스트 예외 주입. 같은 트랜잭션 안에서 INSERT 존재를 확인하고 실패 응답 후 별도 DB 조회로 전체 롤백 확인. 알림 미전송, 재접속 초대 현황 복구, 같은 초대 재시작 성공 확인.
4. 수락 후 오프라인인 사용자도 세션에 저장되며 조회 가능. 재접속 시 시작 알림을 재전송하지 않는 정책 확인.
5. PENDING 응답과 시작 경쟁 시 DB 참여 인원·조회 권한·WebSocket 메시지가 실제 처리 순서와 일치하는지 확인.

롤백 테스트만 Repository spy의 save를 실제 saveAndFlush로 위임한 후 예외를 발생시킨다. 다른 테스트는 실제 저장·조회·트랜잭션을 사용한다. 모든 테스트는 UUID로 만든 계정과 해당 그룹의 세션·참여자만 정리하며, 정리 후 잔여 세션이 없음을 확인한다.

## 실행 명령

```powershell
$env:RIDEMATE_LIVE_TEST='true'
./gradlew.bat test build --rerun-tasks --console=plain
```

## 실행 결과

- 2026-11-02 현재 실행: 전체 105개 통과, 실패 0, 오류 0, 건너뜀 0. `BUILD SUCCESSFUL`, 2분 30초.
- 라이브 테스트: 인증 1개, 그룹 2개, 로그인 WebSocket 2개, 초대·라이딩 8개 모두 통과.
- `build/test-results/test/TEST-*.xml`을 합산했다. 이전 실행의 82개·27개·36개 결과를 재사용하지 않았다.
- `git diff --check` 통과. 기존 deprecated API 및 JVM class sharing 경고는 남아 있으나 테스트·빌드 실패는 없다.

## 검증 한계

- 로컬 MySQL과 127.0.0.1 HTTP·WebSocket 검증이다. Android 단말·외부 네트워크·부하 성능 검증은 아니다.
- 실제 DB 롤백은 INSERT 후 예외 경로를 검증했다. DB 서버 장애나 COMMIT 응답 유실을 재현한 것은 아니다. 커밋 예외 후 초대 유지·알림 차단은 별도 단위 테스트의 모의 트랜잭션 매니저로 검증한다.
- 경쟁 테스트는 실행 시 발생한 순서를 검사한다. 모든 스레드 스케줄 순서나 다중 서버 배포를 보장하는 검증은 아니다.
- 오프라인 시작 알림의 영구 저장·재전송, /ws/ride·종료·기록·통계는 이번 범위에 포함하지 않는다.
