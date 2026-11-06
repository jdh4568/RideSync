# RideMate Android

Kotlin / Jetpack Compose 앱. 단일 app 모듈에서 feature-first + MVVM + Repository 구조를 사용합니다.
기본 패키지와 applicationId는 기존 `com.example.ridemate_api_test`를 유지합니다.

## 패키지

소스 기준: `app/src/main/java/com/example/ridemate_api_test/`

- `app/navigation`: 앱 진입점 및 향후 화면 이동 구성. 현재는 연결 테스트 화면 하나를 표시합니다.
- `app/session`: 향후 로그인 세션과 앱 공통 이벤트 조정.
- `core/designsystem`: 테마와 공통 Compose 컴포넌트.
- `core/network`, `websocket`, `datastore`, `location`: 공통 플랫폼·통신 기반.
- `core/di`: 현재는 AppContainer를 사용한 수동 의존성 주입.
- `feature/auth`, `home`, `group`, `invitation`, `ride`: 다음 단계용 패키지 자리. 기능은 아직 구현하지 않았습니다.
- `feature/connection`: 실행 및 서버 연결 테스트. presentation → domain의 Repository 인터페이스 → data의 구현 순서로 연결됩니다.

기능별 presentation에는 Route, Screen, ViewModel, UiState를 둡니다. domain에는 모델과 Repository 인터페이스를, data에는 API/DTO 및 Repository 구현을 둡니다. 추가 local/mapper/usecase/service 패키지는 실제 필요할 때 생성합니다. 빈 패키지는 Git에서 유지되도록 .gitkeep을 사용합니다.

## USB 실기기 실행

1. Android Studio에서 이 test_app 디렉터리를 열고 Gradle Sync를 완료합니다.
2. 휴대폰의 개발자 옵션에서 USB 디버깅을 켜고 PC에 USB로 연결한 뒤 디버깅 허용 팝업을 승인합니다.
3. Android Studio에서 연결된 기기와 app 실행 구성을 선택하고 Run을 누릅니다.
4. 앱 실행만 확인하려면 첫 화면의 '앱 실행 정상' 및 기기 정보를 확인하면 됩니다. 서버는 필요하지 않습니다.
5. 서버 연결까지 확인하려면 PC에서 기존 서버를 실행합니다. 서버 코드는 변경하지 않습니다.
6. PowerShell에서 다음 명령을 실행합니다. 아래 SDK 경로는 기본 설치 위치이며, 다른 위치에 설치했다면 수정하세요.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
```

7. 앱의 서버 주소를 `http://127.0.0.1:8080`으로 두고 '서버 연결 테스트'를 누릅니다.

여러 기기가 연결되어 있으면 adb에 `-s 기기시리얼`을 추가합니다. USB 재연결 후에는 reverse 명령이 다시 필요할 수 있습니다. 실제 서버 포트가 8080이 아니라면 reverse 명령의 포트와 앱 입력 주소를 맞춥니다. USB reverse를 사용하면 같은 Wi-Fi 연결은 필요하지 않습니다.

테스트는 `GET /api/v1/auth/check-login-id?loginId=ridemate_connection_test`를 호출합니다. HTTP 200과 boolean 타입의 available 응답을 확인하며, true/false 모두 연결 성공입니다. 사용자 생성이나 데이터 변경은 하지 않습니다. 인증·라이딩·WebSocket 전체 기능 검증은 아닙니다.

HTTP 개발 서버 접속은 debug manifest에서만 허용합니다. release에서는 HTTPS 주소를 사용합니다. 연결/읽기 타임아웃은 각각 5초이며 주소는 화면 회전 시 유지되지만 앱 프로세스 재시작 후에는 기본값으로 돌아옵니다.

## 검증 명령

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

화면은 고정 프레임 대신 가변 너비, 최대 콘텐츠 너비, 스크롤, 시스템 바 및 키보드 여백을 사용합니다. Compose Preview에는 390×884와 작은 화면/확대 글꼴 구성을 제공합니다. 실제 제조사 기기 호환성은 각 기기에서 별도로 확인해야 합니다.
