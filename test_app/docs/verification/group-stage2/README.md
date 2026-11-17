# 그룹 API 자체 검증

2026-11-17, 2단계 구현의 자동 검증 자료다. 실제 Spring 서버·MySQL·실기기 테스트가 아니다.

기본 명령:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline
```

기본 JVM 테스트는 Android SDK의 JSON 스텁에 의존하지 않는 상태·세션 테스트를 포함한다. 실제 Android JSON 구현이 필요한 추가 HTTP 계약 테스트는 다음 명령으로 함께 실행한다.

```powershell
$env:GRADLE_USER_HOME='C:/Users/jdh45/.gradle'
& ./docs/verification/group-stage2/Verify-GroupHttp.ps1
```

Android Studio가 다른 경로에 있으면 -StudioRoot를 지정한다. 설치된 Studio의 plugins/design-tools/lib/layoutlib.jar에서 org.json 클래스만 build/group-json/json-runtime.jar로 추출하고, 임시 Gradle init script로 GroupHttpContractTest.kt 소스와 테스트 런타임만 연결한다. 앱 빌드 설정·의존성을 바꾸거나 새 패키지를 설치하지 않는다.

실행 결과: 기본 45개와 추가 6개, 총 51개 통과(실패·오류·건너뜀 0개). 테스트 소스는 가짜 계정/토큰과 루프백 ServerSocket만 사용한다. 결과 XML과 HTML은 app/build/test-results/testDebugUnitTest 및 app/build/reports/tests/testDebugUnitTest에 생성되며 다음 테스트 실행 시 갱신된다.

검증한 내용과 당시 미실행 항목은 [개발계획 2단계 기록](../../GROUP_MANAGEMENT_DEVELOPMENT_PLAN.md)을 따른다. 당시 미확인 오류 문구 Preview는 3단계에서 추가 확인했다. 실제 서버·실기기의 남은 검증과 사용자 절차는 [3단계 인수인계](../group-stage3/README.md)를 참고한다.
