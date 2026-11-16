# 메인 화면 자산

원본: [Figma 메인 화면](https://www.figma.com/design/vdeCHSAx4g9YRVDcyfF7Sh/RideMate--Copy-?node-id=233-2430), 2026-11-16 조회.

- 배경 PNG(852×1845)는 기존 `app/src/main/res/drawable-nodpi/login_landscape.png`와 SHA-256이 같아 앱에서는 기존 파일을 재사용한다. `background.png`는 다운로드한 원본이다.
- 배경 SHA-256: `BEBD320EB153FCAF80EA4B7E9E6A7E69C542A55D619F26824DF0A9E1A3B73038`.
- SVG는 Sharp의 density 288로 PNG 변환해 `drawable-nodpi/home_*.png`로 사용한다. SVG 루트 크기는 변경하지 않았다.
- `ride_arrow.svg`, `group_arrow.svg`는 Figma의 회전과 같이 시계 방향 90도로 회전해 PNG로 변환했다. 화면에서는 원·화살표를 겹쳐 표시한다.
- `notification_badged.svg`는 원본이다. 합의한 배지 제외를 위해 fill `#E02F32`인 경로만 제거한 `notification.svg`를 변환한다. 프로필 자산은 사용하지 않는다.
- 카드 원·아이콘·최근 목록 구분선은 해당 원본 SVG를 변환한 PNG를 사용한다.
- 원본 Inter 폰트 파일은 제공되지 않아 기존 Android Sans Serif 계열을 사용한다. 로고는 기존 `LoginDesign.Logo` 스타일과 문자열을 재사용한다.

| 원본 | Figma 위치 | 앱 리소스 / 표시 크기 |
|---|---|---|
| `background.png` | 화면 배경 | `login_landscape` / 창 크기, Crop |
| `notification.svg` | 상단 알림 | `home_notification` / 30×30dp, 터치 48dp |
| `bicycle.svg` | 라이딩 시작 카드 | `home_bicycle` / 38×26.5dp |
| `users.svg` | 그룹 관리 카드 | `home_users` / 30×30dp |
| `ride_circle.svg`, `group_circle.svg` | 카드 우측 원 | `home_ride_circle`, `home_group_circle` / 28×28dp |
| `ride_arrow.svg`, `group_arrow.svg` | 원 안 우측 화살표 | `home_ride_arrow`, `home_group_arrow` / 약 6.6×12.7dp |
| `divider.svg` | 최근 라이딩 각 행 아래 | `home_divider` / 가용 너비, 높이 0.5dp |

임시 Figma 다운로드 URL은 앱 실행에 사용하지 않는다. 렌더링 검증 결과와 미확인 조건은 [개발계획](../../HOME_DEVELOPMENT_PLAN.md)에 기록한다.
