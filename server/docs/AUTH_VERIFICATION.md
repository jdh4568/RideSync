# 인증 기능 검증 결과

검증일: 2026-10-29

## 실제 실행 검증

현재 application.properties의 MySQL 설정과 `ddl-auto=validate`를 사용했다.
`AuthLiveIntegrationTests`는 127.0.0.1의 임시 포트에 실제 Spring Boot 서버를 띄우고 HTTP 요청을 전송한다. Repository를 모킹하거나 인메모리 DB로 대체하지 않는다.

| 항목 | 확인 결과 |
|---|---|
| DB 연결 및 JPA 시작 | 성공 |
| 실제 user 테이블 UNIQUE | login_id, nickname 각각 단일 UNIQUE 인덱스 존재 |
| 회원가입 | 201, userId/nickname 반환 |
| 저장 데이터 | BCrypt 해시 검증 성공, created_at 저장 |
| 중복 확인 | 가입 전 아이디 사용 가능, 가입 후 아이디/닉네임 사용 불가 |
| 중복 가입 | 아이디 중복과 닉네임 중복 각각 409 |
| 잘못된 가입 요청 | 필수값 누락 및 비밀번호 확인 불일치 400 |
| 로그인 | 200, accessToken/userId/nickname 반환 |
| 로그인 실패 | 없는 아이디와 틀린 비밀번호 모두 401 INVALID_CREDENTIALS |
| 보호 경로 /test | 토큰 없이 401, 발급된 토큰으로 200 |
| 잘못된 JWT | 서명 변조 및 잘못된 형식 모두 401 INVALID_TOKEN |
| 인증 유지 방식 | 토큰을 뺀 후속 요청은 401, 세션 인증으로 유지되지 않음 |
| 동시 회원가입 2건 | 201/409 반환, 해당 아이디의 DB 레코드는 1개 |
| 정리 | 생성한 검증 계정 삭제 확인, 임시 서버 종료 |

전체 테스트 37개가 통과했다(실제 연동 시나리오 1개, 기본 contextLoads 1개, 기존 인증 관련 테스트 35개). 토큰 만료 및 삭제된 사용자의 인증 거부는 기존 MVC 테스트에서 확인한 결과이며, 이번 실제 HTTP 시나리오에는 포함하지 않았다.

## 재실행

실제 MySQL이 실행 중이고 프로젝트의 DB 연결 설정이 맞아야 한다. JWT 환경변수는 필수가 아니다.

PowerShell에서 서버 프로젝트 디렉터리를 기준으로 실행:

```powershell
$env:RIDEMATE_LIVE_TEST = 'true'
try {
    .\gradlew.bat test --rerun-tasks
} finally {
    Remove-Item Env:RIDEMATE_LIVE_TEST -ErrorAction SilentlyContinue
}
```

`RIDEMATE_LIVE_TEST`는 검증용 선택 변수이며 서버 실행에 필요하지 않다. 설정하지 않으면 실제 HTTP 연동 테스트는 건너뛴다. 기존 `contextLoads` 테스트는 별도로 실제 DB 연결이 필요하다.

검증 계정은 매번 무작위 식별자를 사용하고 finally에서 해당 계정만 삭제한다. 강제 프로세스 종료 시 정리가 실행되지 않을 수 있다. 실제 계정 비밀번호, DB 접속 비밀번호, JWT 원문은 이 문서에 저장하지 않는다.

## 검증 범위

백엔드 로그인·회원가입과 JWT 인증 검증까지 완료했다. Android 앱의 화면 전환, 네트워크 연결 및 WebSocket 연결은 별도 검증 대상이다. DB 스키마와 인증 제품 코드는 이번 검증에서 변경하지 않았다.
