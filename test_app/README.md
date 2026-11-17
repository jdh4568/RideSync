# RideMate Android

Kotlin / Jetpack Compose 앱. 단일 app 모듈에서 feature-first + MVVM + Repository 구조를 사용합니다.
기본 패키지와 applicationId는 기존 `com.example.ridemate_api_test`를 유지합니다.

## 패키지

소스 기준: `app/src/main/java/com/example/ridemate_api_test/`

- `app/navigation`: 로그인·회원가입 화면 및 연결 완료 후 메인 화면 사이의 이동.
- `app/session`: 앱이 소유하는 인증·WebSocket 상태, 재연결·만료·로그아웃 조정.
- `core/designsystem`: 테마와 공통 Compose 컴포넌트.
- `core/network`, `websocket`, `datastore`: 공통 서버 주소, OkHttp WebSocket, Keystore 기반 암호화 저장. `location`은 향후 구현 자리입니다.
- `core/di`: 현재는 AppContainer를 사용한 수동 의존성 주입.
- `feature/auth`: 로그인·회원가입 UI와 입력 검증, Repository, 인증 API 연동. 회원가입은 아이디·닉네임 중복 확인 후 제출하며 성공 시 로그인 화면으로 돌아옵니다. 실제 서버·기기 통합 검증은 남아 있습니다.
- `feature/home`: Figma 기반 UI·임시 주행 데이터·준비 중 안내와 기존 인증 세션 연결을 구현했습니다. 로그인 목적지는 메인 화면이며 연결 상태·오류·수동 재연결·로그아웃은 기존 AuthRepository를 사용합니다. SM-S947N(Android 16)에서 메인·팝업·하단 스크롤·통신 단절/복구·오프라인 로그아웃·재로그인·글꼴 확대를 확인했습니다. 다른 기기·실제 토큰 만료 등 남은 조건은 [메인 화면 개발계획](docs/HOME_DEVELOPMENT_PLAN.md)을 참고하세요.
- `feature/group`: 그룹 관리 UI·생성 팝업·메인 왕복 이동과 목록 조회·가입 신청·그룹 생성 API를 연결했습니다. 가입 상태와 조회된 정원에 따라 버튼을 제한하고 성공 후 목록을 갱신합니다. 보호 REST의 JWT 전달·401 세션 종료는 기존 LoginSession을 재사용합니다. 생성 결과가 불명확하면 자동 재전송하지 않고 목록 확인을 안내합니다. 자체 JVM·로컬 HTTP 계약 검증과 선택한 오류 Preview를 확인했으며 실제 Spring 서버·실기기 검증은 남아 있습니다. [그룹 관리 개발계획](docs/GROUP_MANAGEMENT_DEVELOPMENT_PLAN.md)과 [사용자 실기기 확인 절차](docs/verification/group-stage3/README.md)를 참고하세요.
- `feature/group/presentation/requests`: 메인 알림 팝업에서 그룹장이 소유한 그룹의 대기 신청 조회·수락·거절을 연결했습니다. 조회 403은 별도 비그룹장 상태로 안내하고 다른 오류는 빈 목록으로 바꾸지 않습니다. 서버 성공 확인 후 항목을 제거하며, 처리 중 닫기·재열기와 세션 변경의 늦은 응답을 방지합니다. 불명확한 변경 결과는 자동 재전송 없이 목록을 확인합니다. 예시 신청은 기본 Preview에만 있습니다. 가능한 자체 점검·인수인계 정리를 완료했으며 실제 서버는 연결 시간 초과로 미검증, 실기기는 사용자 후속 확인입니다. [알림 개발계획](docs/GROUP_JOIN_NOTIFICATIONS_DEVELOPMENT_PLAN.md)과 [실기기 확인 절차](docs/verification/group-join-notifications-stage3/README.md)를 참고하세요.
- `feature/invitation`, `ride`: 다음 단계용 패키지 자리. 기능은 아직 구현하지 않았습니다.
- `feature/connection`: 실행 및 서버 연결 테스트. presentation → domain의 Repository 인터페이스 → data의 구현 순서로 연결됩니다.

기능별 presentation에는 Route, Screen, ViewModel, UiState를 둡니다. domain에는 모델과 Repository 인터페이스를, data에는 API/DTO 및 Repository 구현을 둡니다. 추가 local/mapper/usecase/service 패키지는 실제 필요할 때 생성합니다. 빈 패키지는 Git에서 유지되도록 .gitkeep을 사용합니다.

## USB 실기기 실행

1. Android Studio에서 이 test_app 디렉터리를 열고 Gradle Sync를 완료합니다.
2. 휴대폰의 개발자 옵션에서 USB 디버깅을 켜고 PC에 USB로 연결한 뒤 디버깅 허용 팝업을 승인합니다.
3. Android Studio에서 연결된 기기와 app 실행 구성을 선택하고 Run을 누릅니다.
4. 첫 화면에 Figma 기반 로그인 화면이 표시됩니다. 화면 표시 자체에는 서버가 필요하지 않습니다.
5. 실제 로그인은 현재 `ridesyncBaseUrl`에 맞게 서버와 연결 경로를 준비한 뒤 기존 계정으로 시도합니다. USB 터널 주소를 쓸 때에는 아래 포트 전달을 설정합니다. API 인증·암호화 저장·WebSocket 최초 연결이 모두 완료되어야 메인 화면으로 이동합니다. 주행 기록·최근 라이딩은 임시 데이터이며 라이딩 시작 버튼은 준비 중 안내를 표시합니다. 알림 버튼은 메인 위에 ‘알림 내역’ 팝업을 열고 최신 대기 신청을 조회합니다. 그룹장은 수락·거절할 수 있고, 소유 그룹이 없으면 비그룹장 안내를 표시합니다. 그룹 관리 버튼으로 실제 그룹 목록을 조회하고 가입 신청·그룹 생성 팝업을 사용할 수 있습니다. 알림 API의 실제 Spring 서버·실기기 실행 검증은 아직 수행하지 않았습니다.
6. 회원가입 버튼으로 가입 화면에 진입합니다. 아이디·닉네임을 각각 중복 확인하고 입력 조건을 충족하면 가입할 수 있습니다. 확인한 값을 수정하면 재확인이 필요합니다. 가입 요청 중에는 입력과 화면 이탈을 제한하며, 성공 시 가입한 아이디와 완료 안내를 로그인 화면에 표시합니다. 자동 로그인은 하지 않습니다. 응답 단절 등으로 가입 여부가 불명확하면 자동 재전송하지 않고 확인 방법을 안내합니다. 구현 범위와 남은 검증은 [회원가입 개발계획](docs/SIGNUP_DEVELOPMENT_PLAN.md)을 참고하세요.

로그인 화면은 밝은 테마와 Figma 색상을 사용합니다. 비밀번호는 영구 저장소나 saved-state Bundle에 저장하지 않고 인증 응답 처리 후 입력에서도 지웁니다. JWT·사용자 정보는 Android Keystore의 AES-GCM 키로 암호화하여 백업 제외 내부 파일에 저장합니다. 자동 로그인은 제공하지 않으며 프로세스 재시작 시 저장된 세션을 정리하고 새 로그인을 요구합니다.

## 서버 연결 설정

현재 서버의 기본 포트는 `18080`입니다. 앱 코드의 기본 주소는 `http://127.0.0.1:18080`이며 REST와 WebSocket이 같은 설정을 사용합니다. 현재 프로젝트의 `gradle.properties`는 `ridesyncBaseUrl=http://192.168.0.5:18080`으로 지정되어 있어 2026-11-16 실기기 검증은 Wi-Fi LAN 통신으로 수행했습니다. USB 디버깅 연결 자체가 USB 네트워크 터널을 의미하지는 않습니다. 실제 휴대폰에서 `127.0.0.1` 주소를 사용할 때에는 휴대폰 자신을 가리키므로 USB 포트 전달이 필요합니다.

PC에서 서버를 실행한 뒤 PowerShell에서 다음 명령을 실행합니다. 아래 SDK 경로는 기본 설치 위치이며, 다른 위치에 설치했다면 수정하세요.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:18080 tcp:18080
```

메인 화면 하단에는 연결 상태·오류·필요 시 다시 연결 버튼과 로그아웃을 표시합니다. 연결이 끊겨도 메인 화면을 유지하고 기존 정책으로 재연결합니다. 로그아웃·만료·인증 거절 시 로그인으로 돌아옵니다. 과거 임시 확인 화면 코드는 보존했지만 일반 진입 경로에는 사용하지 않습니다.

여러 기기가 연결되어 있으면 adb에 `-s 기기시리얼`을 추가합니다. USB 재연결 후에는 reverse 명령이 다시 필요할 수 있습니다. 서버 포트를 기본값 `18080`에서 변경했다면 reverse 명령의 포트와 아래 `ridesyncBaseUrl` 설정을 함께 맞춰 다시 빌드합니다. 로그인 화면에는 서버 주소 입력란이 없습니다. USB reverse를 사용하면 같은 Wi-Fi 연결은 필요하지 않습니다.

주소 변경은 프로젝트 Gradle 속성 `ridesyncBaseUrl` 한 곳에서 관리합니다. 예를 들어 에뮬레이터는 `-PridesyncBaseUrl=http://10.0.2.2:18080`, 외부 연결은 해당 서버의 HTTPS 주소로 빌드합니다. 기존 `feature/connection` 진단 화면은 보존했지만 현재 진입 메뉴는 없습니다.

HTTP 개발 서버 접속은 debug에서만 허용합니다. release는 `ridesyncBaseUrl`에 HTTPS 주소를 지정해야 하며 HTTP 설정은 요청 전에 거부합니다. 로그인 REST는 연결 10초·읽기 15초·전체 20초 제한을 사용하고 자동 재전송하지 않습니다. WebSocket 연결 실패 시 1·2·4초 간격으로 최대 3회 재시도한 뒤 수동 재연결을 제공합니다.

백그라운드 진입 시 WebSocket과 재연결 작업을 정리하고, 포그라운드 복귀 시 메모리에 남은 유효한 토큰으로 재접속합니다. 백그라운드 초대 수신이나 푸시 알림은 이번 범위가 아닙니다. 초대 메시지 UI는 아직 없으며 알 수 없는/잘못된 텍스트·바이너리 메시지는 무시합니다. 로그아웃·토큰 만료·인증 거절 시 연결과 세션을 정리하고 로그인 화면으로 돌아옵니다.

## 검증 명령

가입 신청 알림의 로컬 HTTP 계약 검증은 `powershell -File .\tools\test-group-join-requests.ps1`로 실행합니다. Android Studio 설치 경로가 다르면 `-AndroidStudioHome`으로 지정합니다. 기존 JDK·Gradle 캐시 설정을 사용하며 IDE에 포함된 JSON 구현만 테스트 런타임에 적용합니다. 새 의존성을 설치하거나 앱 빌드 설정을 바꾸지 않습니다. 이 도구로 `app/src/contractTest`를 별도 컴파일하며 실제 Spring 서버 검증과 구분합니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

로그인 화면은 고정 프레임 대신 가변 너비, 최대 콘텐츠 너비, 스크롤, 시스템 바 및 키보드 여백을 사용합니다. Compose Preview에 390×844, 320×640(글꼴 1.3/2.0), 600×960, 가로 화면, 입력 오류, 샘플 로딩 상태를 정의했습니다. Preview 정의와 실제 렌더링 검증은 구분합니다.

로그인 1단계의 구현 범위·Figma 대비 차이·검증 한계는 [검증 기록](docs/LOGIN_UI_VERIFICATION.md)을 참고하세요. 실제 인증 계약은 [서버 API 명세](../server/docs/API_SPEC.md)를 따릅니다.

과거 로그인 구현·로컬 검증 결과는 [인증 연동 기록](docs/LOGIN_AUTH_VERIFICATION.md)을 참고하세요. 사용자는 2026-11-16에 회원가입부터 임시 확인 화면까지의 정상 흐름을 확인했다고 보고했습니다. 이후 메인 교체와 JVM 테스트 31개·빌드·lint 검증을 완료했고, 3단계에서 실제 서버·SM-S947N의 메인·연결·로그아웃 등 주요 흐름을 확인했습니다. 3단계에서는 앱 코드 변경이 없어 전체 빌드·테스트를 다시 실행하지 않았습니다. 상세 결과와 미실행 조건은 [메인 개발계획](docs/HOME_DEVELOPMENT_PLAN.md)을, 새 화면의 Preview·검증 범위는 [UI 지침](docs/APP_UI_GUIDELINES.md)을 따릅니다.

회원가입의 과거 3단계 기록은 API 연동·가능한 로컬 검증·문서 정리까지이며, 당시 JVM 테스트 26개·디버그 빌드가 통과하고 lint 오류 0개·경고 12개였습니다. 기본 화면과 선택한 중복/입력 오류 Preview를 확인했으나 당시 환경 미준비로 실제 통합 검증은 실행하지 못했습니다. 이후 사용자가 보고한 정상 흐름 확인과 구분하며, 키보드·오류 상태의 하단 스크롤·생명주기 등 확인되지 않은 항목은 [회원가입 개발계획](docs/SIGNUP_DEVELOPMENT_PLAN.md)의 후속 검증 절차를 참고하세요.
