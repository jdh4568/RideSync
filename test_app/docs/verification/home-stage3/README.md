# 메인 화면 3단계 실기기 확인 자료

2026-11-16. 모델 SM-S947N, Android 16/API 36, 세로·3버튼 내비게이션. USB 디버깅으로 조작하고 실제 서버 통신은 `192.168.0.5:18080`의 Wi-Fi LAN 경로를 사용했다. 승인·전체 결과·미검증 항목은 [개발계획](../../HOME_DEVELOPMENT_PLAN.md)의 3단계 결과를 따른다.

| 캡처 | 확인 내용 |
|---|---|
| [01-main.png](01-main.png) | 사용자 직접 로그인 후 실제 메인 상단 |
| [02-notification.png](02-notification.png) | 알림 준비 중 안내 |
| [03-ride-preparing.png](03-ride-preparing.png) | 라이딩 시작 준비 중 안내 |
| [04-group-preparing.png](04-group-preparing.png) | 그룹 관리 준비 중 안내 |
| [05-bottom-connected.png](05-bottom-connected.png) | 실제 스와이프로 하단 목록·연결 상태·로그아웃 접근 |
| [06-foreground-reconnected.png](06-foreground-reconnected.png) | 백그라운드 후 앱 복귀·재연결 |
| [07-network-interrupted.png](07-network-interrupted.png) | Wi-Fi 단절 뒤 재연결 중·오류 표시 |
| [09-network-waited.png](09-network-waited.png) | 자동 재시도 대기 후 상태 |
| [11-disconnected-controls.png](11-disconnected-controls.png) | 연결 단절·수동 재연결·로그아웃 영역 |
| [12-manual-reconnected.png](12-manual-reconnected.png) | Wi-Fi 복구 후 다시 연결을 눌러 재접속 |
| [13-font150-top.png](13-font150-top.png) | 시스템 글꼴 1.5·세로 동작 카드·통계 배치 |
| [14-font150-bottom.png](14-font150-bottom.png) | 확대 글꼴에서 최근 목록·하단 동작 접근 |
| [16-offline-before-logout.png](16-offline-before-logout.png) | Wi-Fi 단절 상태의 로그아웃 직전 |
| [18-relogin-connected.png](18-relogin-connected.png) | 오프라인 로그아웃·재실행 후 사용자가 재로그인한 실제 연결 상태 |
| [19-final-main.png](19-final-main.png) | 설정 복구 후 남겨둔 메인 상단 |

정상 메인은 본문을 스크롤하므로 상단 캡처에서 최근 목록 끝이 보이지 않는 것은 전체 정보 누락을 뜻하지 않는다. 하단 캡처는 강제 스크롤 초기값이 아닌 실제 ADB 입력 스와이프 결과이다.

로그아웃 이후 로그인 화면은 입력 정보 보존을 피하기 위해 이미지로 저장하지 않았다. UI 접근성 정보에서 로그인·회원가입 표시와 주행 기록 미표시를 검사했다. `run-as ... test -f no_backup/login-session.enc`로 로그인 전후 파일의 존재 여부만 확인했으며 내용을 읽거나 복호화하지 않았다.

이번 장치 검증 스크립트 출력:

```text
PASS: offline logout shows login and removes the stored session file
Original enabled Wi-Fi restored
PASS: back and reopening do not restore authenticated home
PASS: process restart after logout requires login
```

최종 복구 값은 Wi-Fi 켜짐(`wifi_on=1`), 원래 글꼴 배율(`font_scale=1.0`)이다. 후속 재로그인에서 세션 파일이 다시 생성되고 메인 연결이 복구된 것을 확인했다. 이 자료는 외부 인터넷, 다른 기기, 실제 토큰 만료, 새 계정 가입 전 과정의 증거가 아니다.
