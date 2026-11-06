# RideMate API 명세서

## 1. API 개요

### 기술 스택

- Backend: Spring Boot 4.1.1
- Authentication: Spring Security + JWT
- Database: MySQL + Spring Data JPA
- Real-time Communication: WebSocket
- Client: Android (Kotlin, Jetpack Compose)

### 공통 규칙

1. REST API의 공통 경로는 `/api/v1`이다.
2. 로그인, 회원가입, 아이디·닉네임 중복 확인을 제외한 API는 JWT 인증을 사용한다.
3. 사용자 식별은 JWT를 기준으로 처리한다.
4. 요청 및 응답 데이터는 JSON 형식을 사용한다.
5. 날짜와 시간은 ISO 8601 형식을 사용한다.
6. API 요청과 응답에는 DTO를 사용한다.
7. 그룹 가입 상태는 MySQL에서 관리한다.
8. 라이딩 초대 및 수락·거절 상태는 서버 메모리에서 관리한다.
9. 실시간 접속 상태와 라이딩 수신호는 WebSocket으로 처리한다.
10. 기존 데이터베이스 구조는 `DATABASE.md`를 따른다.

### JWT 인증

인증이 필요한 REST API는 다음 헤더를 사용한다.

```http
Authorization: Bearer JWT_TOKEN
```

- Access Token의 기본 유효기간은 1시간이다. Refresh Token은 현재 MVP 범위에 포함하지 않는다.
- JWT의 `sub`에는 사용자 ID가 들어가며, HS256 서명 및 만료 시각을 검증한다.
- 보호 API의 토큰 누락은 401(`UNAUTHORIZED`), 만료·변조·잘못된 토큰 또는 삭제된 사용자는 401(`INVALID_TOKEN`)로 응답한다.
- 공개 인증 API는 Authorization 헤더 없이 호출한다. 헤더를 보내면 해당 토큰도 검증한다.
- 로그아웃은 Android에서 저장된 JWT를 삭제하는 방식으로 처리하며, 별도의 서버 로그아웃 API는 사용하지 않는다.
- 로그아웃해도 발급된 JWT 자체는 만료 전까지 유효하다. 서버의 토큰 차단 목록 및 즉시 무효화는 현재 MVP 범위에 포함하지 않는다.

로컬 실행 시 JWT 환경변수를 설정할 필요가 없다. 설정이 없으면 공통 개발용 기본키와 유효기간 1시간을 사용한다. 기존 MySQL 연결 설정은 별도로 필요하다.

필요한 경우에만 다음 환경변수로 덮어쓸 수 있다:

| 이름 | 설명 |
|---|---|
| `JWT_SECRET` | 선택. 기본키 대신 사용할 키. 무작위 32바이트 이상의 키를 Base64로 인코딩한 값. |
| `JWT_EXPIRATION_SECONDS` | 선택. 토큰 유효기간(초), 기본값 `3600`. 양수여야 한다. |

기본키는 GitHub에 공개되는 로컬 시연용 키이므로 실제 서비스에는 사용하지 않는다. 직접 설정한 키가 잘못된 형식이거나 길이가 부족하면 서버가 시작되지 않는다. 키를 변경하면 기존 토큰을 사용할 수 없으므로 다시 로그인한다.

IntelliJ에서 `ServerApplication`을 실행하거나 PowerShell에서 다음 명령으로 실행한다:

```powershell
.\gradlew.bat bootRun
```

이미 IntelliJ 실행 설정에 `JWT_SECRET`을 등록했다면 해당 값이 우선 적용된다. 공통 기본키를 사용하려면 해당 항목을 삭제한다. 인증된 사용자 ID는 Controller에서 `@AuthenticationPrincipal Long userId`로 받는다.

---

## 2. 로그인 화면

### 2.1 로그인

**POST `/api/v1/auth/login`**

Request

```json
{
  "loginId": "donghyun",
  "password": "1234"
}
```

Response

```json
{
  "accessToken": "JWT_TOKEN",
  "userId": 1,
  "nickname": "동현"
}
```

처리 규칙:

- 로그인 아이디와 비밀번호를 검증한다.
- 인증 성공 시 JWT를 발급한다.
- 성공 시 HTTP 200으로 응답한다. 없는 아이디와 잘못된 비밀번호는 동일하게 401(`INVALID_CREDENTIALS`)로 응답한다.
- Android는 JWT를 저장한다.
- 로그인 성공 후 `/ws/login` WebSocket에 연결한다.
- 연결이 완료되면 메인 화면으로 이동한다.

### 2.2 로그아웃

**Android 클라이언트 처리 — 별도 REST API 없음**

처리 순서:

1. 앱의 영구 저장소와 메모리에서 Access Token 및 로그인 사용자 정보를 삭제한다.
2. 이후 HTTP 요청에 기존 `Authorization` 헤더를 사용하지 않는다.
3. 연결된 `/ws/login` 및 `/ws/ride/{rideSessionId}` WebSocket이 있으면 종료하고, 자동 재연결을 중단한다.
4. 로그인 화면으로 이동하고 이전 화면 기록을 정리하여 뒤로 가기로 로그인이 필요한 화면에 재진입하지 못하게 한다.

처리 규칙:

- 서버 접속 불가 또는 네트워크 단절 상태에서도 로컬 정보 삭제와 로그인 화면 이동을 완료한다.
- WebSocket 종료 응답을 기다리느라 로그아웃 처리를 막지 않는다.
- 로그아웃은 회원 탈퇴가 아니며, DB의 사용자 계정과 기존 기록을 삭제하지 않는다.
- 삭제 전 복사된 JWT는 만료 전까지 서버 인증에 사용될 수 있다. 다시 서비스를 이용하려면 앱에서 로그인하여 토큰을 발급받는다.

---

## 3. 회원가입 화면

### 3.1 아이디 중복 확인

**GET `/api/v1/auth/check-login-id?loginId=donghyun`**

Response

```json
{
  "available": true
}
```

### 3.2 닉네임 중복 확인

**GET `/api/v1/auth/check-nickname?nickname=동현`**

Response

```json
{
  "available": true
}
```

### 3.3 회원가입

**POST `/api/v1/auth/signup`**

Request

```json
{
  "loginId": "donghyun",
  "password": "1234",
  "passwordConfirm": "1234",
  "nickname": "동현"
}
```

Response

```json
{
  "userId": 1,
  "nickname": "동현"
}
```

처리 규칙:

- 아이디와 닉네임의 중복 여부를 검증한다.
- 비밀번호와 비밀번호 확인 값이 일치하는지 검증한다.
- 비밀번호는 BCrypt로 해싱하여 저장한다.
- `passwordConfirm`은 DB에 저장하지 않는다.
- 회원가입 성공 시 HTTP 201을 반환한다.
- 필수값은 공백일 수 없으며, 아이디와 닉네임은 각각 최대 50자이다. 중복 확인에도 동일한 규칙을 적용한다.
- 비밀번호는 BCrypt 입력 한도에 따라 UTF-8 기준 최대 72바이트이다.
- 비밀번호 확인 불일치는 400(`PASSWORD_MISMATCH`), 비밀번호 한도 초과는 400(`INVALID_PASSWORD`)을 반환한다.
- 중복 아이디·닉네임은 409(`DUPLICATE_LOGIN_ID`, `DUPLICATE_NICKNAME`)를 반환한다. 동시 가입에 따른 DB 중복 키 오류는 409(`DUPLICATE_USER`)를 반환한다.

---

## 4. 메인 화면

### 4.1 개인 주행 통계 및 최근 라이딩 조회

**GET `/api/v1/users/me/home`**

Response

```json
{
  "totalDistance": 152.5,
  "averageSpeed": 22.3,
  "totalRides": 12,
  "lastRideDate": "2026-10-28",
  "recentRides": [
    {
      "rideSessionId": 10,
      "groupName": "한강모임",
      "startedAt": "2026-10-28T14:00:00",
      "distance": 15.2,
      "averageSpeed": 21.5
    },
    {
      "rideSessionId": 9,
      "groupName": "B그룹",
      "startedAt": "2026-10-25T10:00:00",
      "distance": 12.5,
      "averageSpeed": 20.1
    }
  ]
}
```

처리 규칙:

- JWT로 로그인한 사용자의 주행 기록을 조회한다.
- FINISHED 상태의 라이딩을 통계 집계 대상으로 사용한다.
- 총 주행 거리는 저장된 `distance`의 합계로 계산한다.
- 평균 주행 속도는 저장된 `average_speed`의 산술평균으로 계산한다.
- 총 라이딩 횟수는 완료된 세션 참여 기록 수로 계산한다.
- 마지막 주행일은 가장 최근에 시작한 완료된 라이딩의 `started_at`을 기준으로 한다.
- 최근 라이딩은 최신순으로 최대 3개를 반환한다.
- 주행 기록이 없으면 통계 숫자는 0, 마지막 주행일은 null, 최근 라이딩은 빈 배열을 반환한다.

---

## 5. 그룹 관리 화면

### 5.1 전체 그룹 목록 조회

**GET `/api/v1/groups`**

Response

```json
[
  {
    "groupId": 1,
    "groupName": "한강모임",
    "description": "주말 자전거 모임",
    "memberCount": 5,
    "maxMembers": 20,
    "membershipStatus": "APPROVED",
    "myRole": "OWNER"
  },
  {
    "groupId": 2,
    "groupName": "B그룹",
    "description": "자전거 라이딩",
    "memberCount": 8,
    "maxMembers": 15,
    "membershipStatus": null,
    "myRole": null
  }
]
```

처리 규칙:

- 전체 그룹 목록을 반환한다.
- `memberCount`는 APPROVED 상태의 그룹원 수이다.
- `membershipStatus`는 현재 사용자의 해당 그룹 가입 상태이다.
- 가입 이력이 없다면 `membershipStatus`와 `myRole`은 null이다.
- 가입 신청 상태에 따라 가입 신청 버튼의 표시 및 활성화 여부를 결정한다.

### 5.2 그룹 가입 신청

**POST `/api/v1/groups/{groupId}/join`**

Request: 없음

Response

```json
{
  "groupId": 2,
  "status": "PENDING"
}
```

처리 규칙:

- JWT로 가입 신청자를 식별한다.
- GROUP_MEMBER에 MEMBER / PENDING 상태로 등록한다.
- 이미 APPROVED 상태인 사용자는 다시 신청할 수 없다.
- PENDING 상태의 중복 신청은 허용하지 않는다.
- REJECTED 상태라면 기존 레코드를 PENDING으로 변경하여 재신청한다.

---

## 6. 그룹 생성 팝업

### 6.1 그룹 생성

**POST `/api/v1/groups`**

Request

```json
{
  "groupName": "한강모임",
  "description": "주말 자전거 모임",
  "maxMembers": 20
}
```

Response

```json
{
  "groupId": 1,
  "groupName": "한강모임"
}
```

처리 규칙:

- 그룹 최대 인원은 2~50명이다.
- 그룹 생성자는 자동으로 그룹장이 된다.
- RIDING_GROUP에 그룹 정보를 저장한다.
- GROUP_MEMBER에 생성자를 OWNER / APPROVED 상태로 등록한다.
- 그룹 생성과 그룹장 등록은 하나의 트랜잭션으로 처리한다.

---

## 7. 그룹 선택 화면

### 7.1 내가 가입한 그룹 조회

**GET `/api/v1/groups/my`**

Response

```json
[
  {
    "groupId": 1,
    "groupName": "한강모임",
    "role": "OWNER",
    "memberCount": 5
  },
  {
    "groupId": 3,
    "groupName": "C그룹",
    "role": "MEMBER",
    "memberCount": 8
  }
]
```

처리 규칙:

- 현재 사용자가 APPROVED 상태로 가입한 그룹만 반환한다.
- OWNER와 MEMBER 모두 조회 대상이다.
- 그룹 조회 권한과 초대 생성 권한은 구분한다. 일반 멤버도 가입한 그룹을 조회할 수 있다.
- 그룹장만 자신이 소유한 그룹에서 라이딩 초대를 생성할 수 있으며, 초대 생성자인 그룹장이 선두 주자가 된다.

---

### 7.2 내가 그룹장인 그룹 조회

**GET `/api/v1/groups/owned`**

라이딩 그룹 선택 화면에서 사용한다. JWT 인증이 필요하며 Request body는 없다.

Response (HTTP 200)

```json
[
  {
    "groupId": 1,
    "groupName": "한강모임",
    "description": "한강 중심 라이딩",
    "memberCount": 13,
    "maxMembers": 20,
    "membershipStatus": "APPROVED",
    "myRole": "OWNER"
  }
]
```

- JWT 사용자와 `RIDING_GROUP.owner_id`가 일치하고 해당 사용자가 APPROVED 멤버인 그룹만 반환한다.
- `memberCount`는 그룹장을 포함한 APPROVED 멤버 수이다. PENDING·REJECTED는 제외한다.
- 그룹 ID 오름차순으로 반환하며 조회 대상이 없으면 HTTP 200과 빈 배열을 반환한다.
- 인증이 없으면 401이다. 프론트엔드에서 그룹장 그룹을 별도로 필터링할 필요가 없다.
- 응답은 전체 그룹 조회와 같은 형식이다. 기존 `/groups` 및 `/groups/my`의 조회 조건과 응답은 유지한다.

---

## 8. 멤버 선택 및 라이딩 참여 현황

### 8.1 그룹 멤버 조회

**GET `/api/v1/groups/{groupId}/members`**

Response

```json
{
  "groupId": 1,
  "groupName": "한강 모임",
  "memberCount": 3,
  "description": "한강에서 즐기는 라이딩 동호회",
  "members": [
    {
      "userId": 1,
      "nickname": "동현",
      "role": "OWNER",
      "online": true
    },
    {
      "userId": 2,
      "nickname": "라이더2",
      "role": "MEMBER",
      "online": true
    },
    {
      "userId": 3,
      "nickname": "라이더3",
      "role": "MEMBER",
      "online": false
    }
  ]
}
```

처리 규칙:

- 응답은 그룹 정보와 `members` 배열을 포함하는 객체이다. 기존 최상위 배열 대신 `members`를 읽는다.
- `memberCount`는 본인과 그룹장을 포함한 APPROVED 멤버 수이며 `members` 배열 길이와 같다.
- `description`은 그룹 설명이 없으면 `null`이다.
- APPROVED 상태의 그룹원만 조회한다.
- `online`은 WebSocket 접속 상태를 기준으로 판단한다.
- 현재 로그인한 사용자는 초대 대상에서 제외한다.
- 그룹 멤버 목록은 DB에서, 온라인 상태는 서버 메모리에서 조회한다.

조회 정책(3단계 구현 완료):

- 해당 그룹의 APPROVED 멤버만 조회할 수 있다.
- 응답에는 본인도 포함한다. Android에서 본인을 초대 선택 대상에서 제외한다.
- online은 REST 조회 시점의 상태이다. 접속 상태 변경 자동 알림은 현재 범위에 포함하지 않는다.

### 8.2 라이딩 참여 요청 생성

**POST `/api/v1/ride-invitations`**

Request

```json
{
  "groupId": 1,
  "inviteeIds": [2, 3, 4]
}
```

Response

```json
{
  "invitationId": "inv-100",
  "groupId": 1,
  "status": "PENDING",
  "invitedCount": 3
}
```

처리 규칙:

- 해당 그룹의 그룹장만 초대를 생성할 수 있다. JWT 사용자 ID와 `RIDING_GROUP.owner_id`가 일치해야 하며, 요청자도 APPROVED 멤버여야 한다.
- 초대를 생성한 그룹장이 선두 주자가 된다. 다른 멤버를 선두로 지정하는 기능은 MVP 범위에 포함하지 않는다.
- 해당 그룹의 APPROVED 멤버만 초대할 수 있다.
- 초대 대상은 본인을 제외한 사용자이다.
- 서버는 고유한 invitationId를 생성한다.
- 초대 대상과 참여 상태를 서버 메모리에 저장한다.
- 초대 대상에게 WebSocket으로 참여 요청을 전달한다.
- 이 단계에서는 RIDE_SESSION을 생성하지 않는다.

초대 생성 정책(2026-11-01, 1단계 구현):

- 성공 시 HTTP 201을 반환한다. `invitedCount`는 선두를 제외한 초대 대상 수이다.
- `groupId`와 `inviteeIds`의 각 ID는 양수여야 하며, 초대 대상은 1~49명이다. 필수값 누락, 빈 목록, 중복 ID, 본인 포함은 400(`INVALID_REQUEST`)으로 전체 요청을 거부한다.
- 그룹이 없으면 404(`GROUP_NOT_FOUND`), 그룹장이 아니거나 요청자가 승인된 멤버가 아니면 403(`FORBIDDEN`)을 반환한다.
- 존재하지 않는 사용자 또는 해당 그룹의 APPROVED 멤버가 아닌 대상을 포함하면 400(`INVALID_INVITEE`)으로 전체 요청을 거부한다.
- 생성 시 선두와 초대 대상 모두 유효한 `/ws/login` 연결이 하나 이상 있어야 한다. 오프라인 사용자가 있으면 409(`USER_OFFLINE`)로 전체 요청을 거부한다.
- 모든 검증을 통과한 뒤 메모리에 저장한다. 선두는 ACCEPTED, 초대 대상은 PENDING으로 초기화한다.
- 생성 후 전송 도중 연결이 끊기더라도 초대를 롤백하거나 참여 상태를 변경하지 않는다. 전송 실패는 수락·거절을 의미하지 않으며 재접속 시 복구 정책은 13.5절을 따른다.
- POST 재호출은 별도의 초대를 생성한다. MVP에서는 생성 요청의 멱등 키나 사용자별 동시 초대 제한을 추가하지 않는다. Android는 요청 중 생성 버튼의 중복 입력을 막는다.

### 8.3 라이딩 참여 현황 실시간 수신

WebSocket: `/ws/login`

메시지 타입: `RIDE_INVITE_STATUS`

서버 → 선두

```json
{
  "type": "RIDE_INVITE_STATUS",
  "invitationId": "inv-100",
  "participants": [
    {
      "userId": 1,
      "nickname": "동현",
      "status": "ACCEPTED"
    },
    {
      "userId": 2,
      "nickname": "라이더2",
      "status": "ACCEPTED"
    },
    {
      "userId": 3,
      "nickname": "라이더3",
      "status": "PENDING"
    },
    {
      "userId": 4,
      "nickname": "라이더4",
      "status": "REJECTED"
    }
  ],
  "acceptedCount": 2,
  "canStart": true
}
```

처리 규칙:

- 참여 요청 생성 후 서버가 최초 참여 현황을 전송한다.
- 사용자가 수락하거나 거절할 때마다 서버 메모리의 상태를 갱신한다.
- 상태 변경 시 전체 참여 현황을 선두에게 WebSocket으로 전송한다.
- 선두 사용자는 ACCEPTED 상태로 계산한다.
- 선두를 포함한 ACCEPTED 인원이 2명 이상이면 canStart가 true가 된다.
- `acceptedCount`와 `canStart`는 참여 응답 기준이며 현재 온라인 인원과는 구분한다. 최초에는 `acceptedCount=1`, `canStart=false`이다.
- 라이딩 참여 현황을 위한 별도 REST GET API는 사용하지 않는다.

### 8.4 라이딩 시작

**POST `/api/v1/ride-invitations/{invitationId}/start`**

Request: 없음

Response

```json
{
  "rideSessionId": 10,
  "groupId": 1,
  "status": "RIDING",
  "startedAt": "2026-10-29T14:00:00"
}
```

처리 규칙:

- 유효한 초대의 생성자이면서 해당 그룹의 그룹장인 사용자만 라이딩을 시작할 수 있다. 만료되거나 정리된 초대는 사용할 수 없다.
- 선두 포함 수락 인원이 2명 이상이어야 한다.
- RIDE_SESSION을 생성하고 RIDING 상태로 저장한다.
- ACCEPTED 상태의 사용자만 RIDE_SESSION_MEMBER에 등록한다.
- 선두는 LEADER, 나머지 참여자는 MEMBER로 저장한다.
- 서버는 확정된 참여자들에게 WebSocket으로 라이딩 시작을 알린다.
- 라이딩 시작 후 해당 invitationId의 임시 상태는 정리한다.

시작 API 세부 정책(2026-11-02):

- 성공 시 HTTP 200을 반환한다. `startedAt`은 한국 시간(`Asia/Seoul`)의 ISO 로컬 날짜·시간 문자열이다. Entity의 `Instant` 매핑은 유지한다.
- 없거나 만료·정리된 초대는 404(`INVITATION_NOT_FOUND`), 없는 그룹은 404(`GROUP_NOT_FOUND`), 생성자·그룹장·선두의 APPROVED 자격 미충족은 403(`FORBIDDEN`)이다.
- ACCEPTED가 2명 미만이면 409(`INSUFFICIENT_PARTICIPANTS`), 확정 참여자가 더 이상 APPROVED 그룹원이 아니면 409(`INVALID_PARTICIPANT`)로 거부한다.
- 시작 시 온라인 여부는 다시 요구하지 않는다. ACCEPTED 사용자만 참여자로 확정한다.
- 초대별 잠금을 DB 커밋까지 유지한다. 세션과 참여자는 하나의 트랜잭션으로 저장하며, 저장·커밋 실패 시 유효기간 내 초대를 유지하고 시작 알림을 보내지 않는다.
- 커밋 성공 후 초대를 정리하고 선두 포함 확정 참여자의 모든 유효한 `/ws/login` 연결에 `RIDE_STARTED`를 보낸다. 전송 실패는 DB 저장을 취소하지 않으며 시작 알림의 오프라인 보관·재접속 재전송은 제공하지 않는다.
- 동일 초대의 동시 시작은 한 번만 성공한다. 성공 후 재호출은 404(`INVITATION_NOT_FOUND`)이며, 응답과 시작이 경쟁하면 초대별 잠금을 먼저 획득한 처리 순서로 확정한다.

---

### 8.5 라이딩 시작 전 초대 취소

**기본 취소 구현 완료(2026-11-07):** 기본 취소만 대상이며 오프라인 취소 알림 복구는 포함하지 않는다.

**POST `/api/v1/ride-invitations/{invitationId}/cancel`**

JWT 인증 필요. Request body: 없음.

Response (HTTP 200)

```json
{
  "invitationId": "inv-100",
  "status": "CANCELLED"
}
```

처리 규칙:

- 해당 초대를 생성한 리더만 유효한 시작 전 초대를 취소할 수 있다. 수락 인원과 현재 온라인 여부는 취소 조건이 아니다.
- 초대를 메모리에서 제거하고 이후 수락·거절·시작 요청을 차단한다. 세션·참여 기록을 생성하거나 기존 주행 기록을 변경하지 않는다.
- 미인증·잘못된 토큰은 401, 유효한 초대의 생성자가 아닌 사용자는 403(`FORBIDDEN`), 없거나 만료·취소·시작 후 정리된 초대는 404(`INVITATION_NOT_FOUND`)이다.
- 취소 결과를 별도로 보관하지 않으므로 취소 재요청도 404이다. 404만으로 취소 성공을 판단하지 않는다. 이미 시작된 라이딩은 기존 종료 API를 사용한다.
- 초대별 잠금으로 취소·시작·응답·재접속 복구·만료 정리를 직렬화한다. 취소가 먼저 완료되면 이후 요청을 거부하고, 시작이 먼저 커밋되면 취소를 거부한다. 시작 저장 실패 시 유효한 초대는 유지되어 취소할 수 있다.
- 수락이 먼저 처리되어도 이후 취소할 수 있다. 취소 알림 이후 해당 초대의 초대·현황·시작 메시지를 새로 전송하지 않는다.
- 제거 후 리더와 초대 대상 전체(PENDING·ACCEPTED·REJECTED)의 유효한 `/ws/login` 연결 모두에 9.4절의 취소 알림을 보낸다. 일부 전송 실패로 취소를 되돌리거나 다른 연결의 알림을 중단하지 않는다.
- 취소해도 `/ws/login` 연결은 유지한다. 취소 이력·오프라인 큐·재접속 시 취소 알림 재전송은 제공하지 않는다.

Android 처리:

- 요청 중 시작·취소 버튼을 모두 비활성화한다. REST 성공 또는 해당 취소 알림을 받으면 참여 현황 팝업을 정리하고 메인 화면을 유지한다.
- REST 응답보다 취소 알림이 먼저 도착할 수 있다. `invitationId`별로 중복 정리를 방지한다.
- 네트워크 오류로 결과를 받지 못하면 취소 완료로 단정하지 않는다. 재요청의 404도 시작·만료·취소를 구분하지 못한다.

---

## 9. 라이딩 요청 팝업

### 9.1 라이딩 초대 수신

WebSocket: `/ws/login`

메시지 타입: `RIDE_INVITE`

서버 → 초대 대상

```json
{
  "type": "RIDE_INVITE",
  "invitationId": "inv-100",
  "groupId": 1,
  "groupName": "한강모임",
  "leaderId": 1,
  "leaderNickname": "동현"
}
```

처리 규칙:

- 초대를 받은 사용자에게 라이딩 요청 팝업을 표시한다.
- 초대 대상은 해당 그룹의 APPROVED 멤버여야 한다.

### 9.2 라이딩 초대 수락 및 거절

WebSocket: `/ws/login`

메시지 타입: `RIDE_INVITE_RESPONSE`

클라이언트 → 서버

```json
{
  "type": "RIDE_INVITE_RESPONSE",
  "invitationId": "inv-100",
  "accepted": true
}
```

처리 규칙:

- 수락하면 accepted는 true이다.
- 거절하면 accepted는 false이다.
- 서버는 인증된 WebSocket 연결로 응답 사용자를 식별한다.
- 서버 메모리의 초대 상태를 변경한다.
- 변경된 참여 현황을 선두 사용자에게 실시간 전달한다.

응답 처리 정책(2단계 구현):

- `type`은 `RIDE_INVITE_RESPONSE`, `invitationId`는 비어 있지 않은 문자열, `accepted`는 JSON boolean이어야 한다. 누락·null·문자열·숫자를 false로 간주하지 않는다.
- 초대 대상만 응답할 수 있다. 선두 본인 또는 비초대자의 응답은 `FORBIDDEN` 오류이며, 메시지에 포함된 사용자 ID로 응답자를 바꿀 수 없다.
- PENDING에서 ACCEPTED 또는 REJECTED로 한 번만 변경한다. 동일 응답 재전송은 성공으로 취급하되 상태·인원은 중복 반영하지 않는다. 반대 응답으로 변경하려 하면 `INVITATION_RESPONSE_CONFLICT` 오류를 반환한다.
- 다중 연결에서 동시에 응답하더라도 사용자별 첫 번째로 처리된 응답만 확정한다. 상태 갱신과 선두에게 보내는 현황 순서를 보장하여 오래된 현황이 최신 현황을 덮지 않도록 한다.
- 존재하지 않거나 만료·정리된 초대는 `INVITATION_NOT_FOUND` 오류를 반환한다.
- 잘못된 JSON·필드 형식은 `INVALID_MESSAGE`, 지원하지 않는 메시지 타입은 `UNSUPPORTED_MESSAGE_TYPE` 오류를 반환한다.
- 오류는 14절의 `ERROR` 형식으로 요청을 보낸 연결에만 전송한다. 일반적인 메시지 검증 오류로 정상 연결을 종료하지 않는다. JWT 만료·통신 오류에 따른 기존 연결 정리 정책은 유지한다.

### 9.3 라이딩 시작 알림

WebSocket: `/ws/login`

메시지 타입: `RIDE_STARTED`

서버 → 참여 확정 사용자

```json
{
  "type": "RIDE_STARTED",
  "rideSessionId": 10,
  "groupId": 1
}
```

처리 규칙:

- 라이딩이 시작되면 ACCEPTED 사용자에게 전달한다.
- 수락한 후미 사용자는 주행 화면으로 이동한다.
- REJECTED 또는 PENDING 상태인 사용자는 주행에 포함하지 않는다.

---

### 9.4 라이딩 초대 취소 알림

**기본 취소 구현 완료(2026-11-07).**

WebSocket: `/ws/login` — 서버 → 리더 및 초대 대상 전체의 유효한 연결

```json
{
  "type": "RIDE_INVITE_CANCELLED",
  "invitationId": "inv-100",
  "groupId": 1
}
```

- Android는 `invitationId`가 일치하는 초대 팝업·대기 목록·수락 후 대기 상태만 정리한다. 다른 초대나 진행 중인 라이딩은 변경하지 않는다.
- 리더는 참여 현황 팝업을 닫는다. 멤버는 취소 안내를 표시하고 기존 화면을 유지한다. 이미 정리된 초대의 중복 알림은 무시한다.
- 공통 수신부에서 처리하며 화면 이동이나 `/ws/login` 연결 종료를 요구하지 않는다.
- 취소 순간 오프라인인 사용자는 알림을 놓칠 수 있다. 취소된 초대는 복구하지 않지만, 취소 알림도 재전송하지 않으므로 기존 팝업·수락 후 대기가 자동으로 정리된다고 보장하지 않는다.
- 초대 만료·서버 재시작을 이 알림으로 통지하는 기능은 이번 범위에 포함하지 않는다.

---

## 10. 주행 화면 (선두)

### 10.1 주행 화면 정보 조회

**GET `/api/v1/rides/{rideSessionId}`**

Response

```json
{
  "groupName": "한강모임",
  "participantCount": 5
}
```

처리 규칙:

- `groupName`은 주행 화면의 그룹 이름에 사용한다.
- `participantCount`는 RIDE_SESSION_MEMBER에 등록된 인원 수이다.
- 조회는 해당 라이딩 세션의 참여자만 가능하다.
- 성공 시 HTTP 200과 `groupName`, `participantCount`만 반환한다. 현재 그룹 가입 상태·온라인 여부가 아닌 RIDE_SESSION_MEMBER의 참여 기록을 기준으로 조회 권한을 검사한다.
- 미인증 요청은 401(`UNAUTHORIZED`), 없는 세션은 404(`RIDE_SESSION_NOT_FOUND`), 세션에 참여하지 않은 사용자는 그룹장·그룹원이어도 403(`FORBIDDEN`)을 반환한다.
- 속도와 이동 거리는 Android에서 GPS를 기반으로 계산한다.

### 10.2 실시간 수신호 전송

WebSocket: `/ws/ride/{rideSessionId}`

메시지 타입: `RIDE_SIGNAL`

클라이언트 → 서버

```json
{
  "type": "RIDE_SIGNAL",
  "signal": "LEFT"
}
```

서버 → 후미 참여자

```json
{
  "type": "RIDE_SIGNAL",
  "senderId": 1,
  "signal": "LEFT"
}
```

신호 종류

| signal | 의미 |
|---|---|
| LEFT | 좌회전 |
| RIGHT | 우회전 |
| STOP | 정지 |
| SLOW | 감속 |
| ROAD_HAZARD | 노면 주의 |
| CURVE_CAUTION | 커브 주의 |
| SINGLE_FILE | 한 줄 주행 |
| DOUBLE_FILE | 두 줄 주행 |
| LEADER_CHANGE | 선두 교체 요청 |

처리 규칙:

- 해당 세션의 LEADER만 수신호를 전송할 수 있다.
- 서버는 해당 라이딩 세션의 MEMBER들에게 메시지를 전달한다.
- 수신호 기록은 MySQL에 저장하지 않는다.

### 10.3 라이딩 종료

**POST `/api/v1/rides/{rideSessionId}/finish`**

Request: 없음

Response

```json
{
  "rideSessionId": 10,
  "status": "FINISHED",
  "endedAt": "2026-10-29T15:00:00"
}
```

처리 규칙:

- 해당 라이딩의 선두 주자만 종료할 수 있다.
- RIDE_SESSION의 상태를 FINISHED로 변경한다.
- ended_at을 기록한다.
- 모든 참여자에게 WebSocket으로 종료 메시지를 전송한다.

종료 API 세부 정책(2026-11-02):

- 성공 시 HTTP 200이며 `endedAt`은 한국 시간의 ISO 로컬 날짜·시간 문자열이다.
- JWT 사용자와 세션의 leader_id가 일치하고 해당 참여 기록의 역할이 LEADER여야 한다. 미인증은 401, 없는 세션은 404(RIDE_SESSION_NOT_FOUND), 권한 부족은 403(FORBIDDEN)이다.
- RIDING을 FINISHED로 변경한다. 선두의 FINISHED 재요청은 기존 종료 결과를 200으로 반환하며 시각 갱신·알림 재전송은 하지 않는다. READY는 409(RIDE_NOT_ACTIVE)이다.
- DB 커밋 성공 후 해당 라이딩의 선두·후미 모든 유효한 연결에 RIDE_FINISHED를 보내고 정상 종료(1000)한다. 일부 전송 실패가 다른 연결의 알림·정리를 막지 않는다. /ws/login 및 다른 라이딩 연결은 유지한다.
- DB 저장·커밋 실패 시 종료 알림과 연결 정리를 하지 않는다. 종료·수신호·새 연결 등록은 같은 서버 내에서 순서를 보호하여 종료 알림 이후 신호 전송이나 종료된 세션 등록을 막는다.
- 오프라인 알림 보관·재전송은 하지 않는다. 참여자와 개인 기록 행은 유지하며 기록 저장은 별도 단계이다.

---

## 11. 주행 화면 (후미)

### 11.1 실시간 수신호 수신

WebSocket: `/ws/ride/{rideSessionId}`

메시지 타입: `RIDE_SIGNAL`

서버 → 후미

```json
{
  "type": "RIDE_SIGNAL",
  "senderId": 1,
  "signal": "LEFT"
}
```

처리 규칙:

- 받은 신호를 Android 주행 화면에 표시한다.
- 최근 수신호 목록은 Android 로컬 메모리에서 관리한다.
- 소리 및 진동 설정은 Android에서 처리한다.
- 수신호 기록은 DB에 저장하지 않는다.

### 11.2 라이딩 종료 알림

WebSocket: `/ws/ride/{rideSessionId}`

메시지 타입: `RIDE_FINISHED`

서버 → 참여자

```json
{
  "type": "RIDE_FINISHED",
  "rideSessionId": 10
}
```

처리 규칙:

- 라이딩 종료 시 참여자들에게 전송한다.
- 클라이언트는 주행을 종료하고 최종 GPS 기록을 계산한다.
- 선두와 후미 모두 자신의 주행 기록을 서버에 저장한다.

### 11.3 개인 주행 기록 저장

**PUT `/api/v1/rides/{rideSessionId}/record`**

Request

```json
{
  "distance": 15.2,
  "averageSpeed": 22.5
}
```

Response

```json
{
  "rideSessionId": 10,
  "distance": 15.2,
  "averageSpeed": 22.5
}
```

처리 규칙:

- JWT로 로그인한 사용자를 식별한다.
- 해당 세션에 참여한 사용자만 기록을 저장할 수 있다.
- 사용자는 자신의 RIDE_SESSION_MEMBER 기록만 수정할 수 있다.
- distance 단위는 km이다.
- averageSpeed 단위는 km/h이다.
- FINISHED 상태인 라이딩에 대해 기록 저장을 허용한다.
- 동일 사용자가 다시 전송하면 기존 기록을 업데이트한다.

개인 기록 저장 정책(2026-11-02):

- 성공 시 HTTP 200과 rideSessionId, distance, averageSpeed를 반환한다.
- distance와 averageSpeed는 모두 필수이며 0 이상, 정수 최대 8자리·소수 최대 2자리이다. 초과·음수·누락은 400(INVALID_REQUEST)으로 거부하고 자동 반올림하지 않는다.
- 미인증은 401, 없는 세션은 404(RIDE_SESSION_NOT_FOUND), 비참여자는 403(FORBIDDEN), READY·RIDING 세션은 409(RIDE_NOT_FINISHED)이다.
- 선두·후미 모두 JWT 사용자 자신의 기존 참여 행만 갱신한다. 요청에 다른 사용자 ID를 넣어도 저장 대상을 바꾸지 않는다.
- 재전송은 기존 두 값을 갱신한다. 같은 참여 행의 동시 저장은 잠금으로 직렬화하며 두 필드를 한 트랜잭션에서 반영한다. 최종 값은 마지막으로 반영된 요청의 값이다.

---

## 12. 그룹 가입 알림 팝업 (그룹장 전용)

### 12.1 내가 소유한 그룹의 가입 신청 목록 조회

**GET `/api/v1/groups/join-requests`**

Request: 없음

Response

```json
[
  {
    "groupMemberId": 15,
    "groupId": 1,
    "groupName": "한강모임",
    "userId": 5,
    "nickname": "김철수",
    "status": "PENDING"
  },
  {
    "groupMemberId": 18,
    "groupId": 3,
    "groupName": "B그룹",
    "userId": 8,
    "nickname": "이영희",
    "status": "PENDING"
  }
]
```

처리 규칙:

- JWT로 현재 사용자를 식별한다.
- RIDING_GROUP.owner_id가 현재 사용자 ID와 일치하는 그룹을 조회한다.
- 해당 그룹들의 GROUP_MEMBER.status가 PENDING인 신청자를 조회한다.
- 하나의 사용자가 여러 그룹의 OWNER인 경우 모든 그룹의 신청을 통합하여 반환한다.
- 각 신청에는 그룹 이름과 신청자 닉네임을 포함한다.
- 대기 신청이 없으면 빈 배열을 반환한다.
- 소유 그룹이 없는 사용자는 조회할 수 없다.

화면 표시 예시:

- 한강모임 그룹에 김철수 님이 가입 신청했습니다.
- B그룹에 이영희 님이 가입 신청했습니다.

### 12.2 그룹 가입 승인

**POST `/api/v1/groups/{groupId}/requests/{userId}/approve`**

Request: 없음

Response

```json
{
  "groupId": 1,
  "userId": 5,
  "status": "APPROVED"
}
```

처리 규칙:

- 해당 그룹의 OWNER만 승인할 수 있다.
- PENDING 상태의 신청만 승인한다.
- 승인 전 그룹 최대 인원을 확인한다.
- GROUP_MEMBER.status를 APPROVED로 변경한다.
- joined_at에 승인 시각을 기록한다.

### 12.3 그룹 가입 거절

**POST `/api/v1/groups/{groupId}/requests/{userId}/reject`**

Request: 없음

Response

```json
{
  "groupId": 1,
  "userId": 5,
  "status": "REJECTED"
}
```

처리 규칙:

- 해당 그룹의 OWNER만 거절할 수 있다.
- PENDING 상태의 신청만 거절한다.
- GROUP_MEMBER.status를 REJECTED로 변경한다.
- 승인 또는 거절 완료 후 가입 신청 목록에서 제외한다.

---

## 13. WebSocket 통신 구조

### 13.1 접속 상태 및 초대 관리

**연결 경로: `/ws/login`**

용도:

- 로그인한 사용자 연결 관리
- 온라인/오프라인 상태 확인
- 라이딩 초대 전달
- 라이딩 초대 수락 및 거절
- 선두 사용자에게 참여 현황 전달
- 라이딩 시작 알림 전달

접속 규칙:

1. 로그인 성공 후 JWT 인증을 거쳐 WebSocket에 연결한다.
2. 서버는 인증된 사용자 ID와 WebSocket 세션을 메모리에서 관리한다.
3. 연결이 유지되는 사용자를 온라인으로 판단한다.
4. 로그아웃 등으로 연결 종료를 감지하면 서버는 해당 WebSocket 세션과 사용자 연결 정보를 정리한다.
5. 라이딩 초대는 현재 연결된 사용자에게 전달한다.
6. 네트워크 단절 시 서버의 연결 종료 감지가 지연될 수 있으며, 앱의 로컬 로그아웃 완료와 서버의 연결 정리 시점은 다를 수 있다.

접속 인증 방식:

- Android는 최초 WebSocket HTTP 연결 요청에 `Authorization: Bearer JWT_TOKEN` 헤더를 보낸다.
- 기존 REST JWT 필터로 서명·만료 시각·사용자 존재 여부를 검증한다.
- 토큰 누락은 HTTP 401, 만료·변조·잘못된 토큰 및 삭제된 사용자는 HTTP 401로 연결을 거부한다.
- URL 쿼리의 토큰은 인증에 사용하지 않는다. 사용자 ID는 인증된 principal에서 가져온다.
- 별도 STOMP/SockJS 없이 표준 WebSocket을 사용한다.

접속 관리 정책(2단계 구현 완료):

- 사용자별 복수 세션을 허용하며, 유효한 연결이 하나 이상이면 온라인이다.
- 마지막 연결 종료 시 오프라인으로 전환하고, 이전 세션 종료가 새 세션을 제거하지 않도록 세션별로 정리한다.
- 연결 후 JWT 만료 시 해당 연결을 종료한다. 새 로그인 토큰으로 재연결한다.
- 서버 Ping 간격은 초기 30초, 마지막 Pong 이후 90초 초과 시 연결을 종료한다.
- 연결 직후에는 연결 시각을 최초 생존 기준으로 사용한다. 정리 작업은 1초 간격으로 실행하므로 실제 종료에는 스케줄링 지연이 있을 수 있다.
- 통신 오류·Ping 전송 실패 시 해당 세션을 정리한다. 서버 종료 시 모든 연결을 정리한다.
- 접속 정보는 서버 메모리에서만 관리하며 DB 스키마는 변경하지 않는다.

현재 구현 범위:

- `/ws/login` 연결 경로와 JWT 인증, 세션의 인증 사용자 ID 연결까지 구현했다.
- 온라인 상태 관리, Ping/Pong 생존 확인, 연결 후 JWT 만료 처리를 구현했다.
- JWT 원문을 접속 레지스트리에 보관하지 않고 인증된 사용자 ID와 만료 시각을 사용한다.
- online이 포함된 그룹원 REST 조회를 구현했다. 해당 그룹의 승인된 멤버만 호출할 수 있다.
- 초대 생성 REST와 서버 발신 `RIDE_INVITE`·최초 `RIDE_INVITE_STATUS`를 구현했다. 메모리 초대는 생성 후 30분에 만료되며 60초 간격으로 정리한다.
- `RIDE_INVITE_RESPONSE` 수락·거절, 선두에게 전체 참여 현황 갱신, 요청 연결에 대한 `ERROR` 응답, 재접속 복구를 구현했다. 일반적인 메시지 검증 오류로 연결을 종료하지 않는다.
- 클라이언트 메시지 처리 전에 해당 연결의 등록 여부·JWT 만료·생존 상태를 확인한다. 다른 기기의 유효한 연결이 있더라도 만료된 연결로 응답할 수 없다.

### 13.2 라이딩 실시간 통신

**연결 경로: `/ws/ride/{rideSessionId}`**

용도:

- 선두 사용자의 수신호 전송
- 후미 사용자에게 수신호 전달
- 라이딩 종료 알림 전달

접속 규칙:

1. 라이딩이 시작된 후 해당 경로에 연결한다.
2. JWT 인증 및 해당 세션 참여 여부를 검증한다.
3. 선두의 수신호를 같은 세션의 후미에게 전달한다.
4. 다른 라이딩 세션의 사용자에게는 메시지를 전달하지 않는다.
5. 라이딩 종료 시 해당 세션의 WebSocket 연결을 정리한다.
6. 로그아웃 등으로 개별 연결 종료를 감지하면 해당 WebSocket 연결 정보를 정리한다. 연결 종료 자체를 라이딩 종료 요청으로 처리하지 않는다.

### 13.3 WebSocket 메시지 목록

라이딩 WebSocket 1단계 구현 정책(2026-11-02):

- `/ws/ride/{rideSessionId}`는 JWT 인증 후 RIDING 세션의 참여자만 등록한다. 그룹 가입 여부가 아닌 RIDE_SESSION_MEMBER를 기준으로 검사한다.
- 미인증은 HTTP handshake에서 거부한다. 인증 후 잘못된 세션 ID, 없는 세션, 비참여자, READY·FINISHED 세션은 ERROR를 전송하고 1008로 연결을 종료한다. 코드는 각각 INVALID_REQUEST, RIDE_SESSION_NOT_FOUND, FORBIDDEN, RIDE_NOT_ACTIVE이다.
- 수신호는 명세의 9종만 허용한다. senderId와 대상 세션은 인증 정보·접속 경로로 정하며 메시지의 임의 사용자·세션 ID는 사용하지 않는다. LEADER_CHANGE는 신호만 전달하고 역할을 변경하지 않는다.
- 선두의 메시지는 동일 세션 MEMBER의 모든 유효한 연결로 전달한다. 후미 발신은 FORBIDDEN, 잘못된 JSON·신호는 INVALID_MESSAGE, 다른 type은 UNSUPPORTED_MESSAGE_TYPE 오류를 발신 연결에 반환한다.
- 라이딩 연결은 `/ws/login`과 별도로 관리한다. JWT 만료, 30초 Ping·90초 초과 Pong 미응답, 전송 실패 시 해당 연결만 정리한다. 연결 종료로 세션 상태·참여 기록을 변경하지 않는다.
- 재접속은 현재 상태·권한을 다시 검사한다. 수신호를 DB에 저장하거나 재접속 때 재전송하지 않는다.
- 라이딩 종료 REST·RIDE_FINISHED 전송·종료 시 일괄 연결 정리는 2단계에서 구현했다(10.3절). 개인 기록 저장은 3단계에서 구현했다(11.3절).

| 경로 | type | 기능 |
|---|---|---|
| `/ws/login` | RIDE_INVITE | 라이딩 초대 전달 |
| `/ws/login` | RIDE_INVITE_RESPONSE | 초대 수락·거절 |
| `/ws/login` | RIDE_INVITE_STATUS | 참여 현황 갱신 |
| `/ws/login` | RIDE_STARTED | 라이딩 시작 알림 |
| `/ws/login` | RIDE_INVITE_CANCELLED | 시작 전 초대 취소 알림 (기본 취소 구현 완료) |
| `/ws/ride/{rideSessionId}` | RIDE_SIGNAL | 실시간 수신호 |
| `/ws/ride/{rideSessionId}` | RIDE_FINISHED | 라이딩 종료 알림 |

### 13.4 invitationId 관리

- invitationId는 라이딩 초대 요청마다 생성하는 고유 식별자이다.
- invitationId와 초대 상태는 서버 메모리에서 관리한다.
- invitationId는 데이터베이스에 저장하지 않는다.
- 실제 라이딩 시작 시 별도의 rideSessionId가 생성된다.
- 서버 재시작 시 진행 중인 임시 초대 요청은 취소된 것으로 처리한다.
- 초대는 서버 생성 시각부터 30분 동안 유효하다. 응답 또는 재접속으로 유효기간을 연장하지 않는다.
- 만료 시각 이상인 초대는 응답·재전송·시작 처리에서 즉시 제외하고, 주기적인 정리로 메모리에서도 제거한다.
- 라이딩 시작 성공 후 임시 데이터를 정리한다. 시작 실패 시에는 유효기간 내 초대를 유지한다.
- 기본 초대 취소 API와 취소 알림은 8.5·9.4절대로 구현했다(2026-11-07). 취소 성공 시 임시 데이터를 제거한다. 취소 결과 보관·오프라인 취소 알림 복구·만료 알림은 MVP 범위에 포함하지 않는다. 취소·만료·서버 재시작 후에는 새 초대를 생성해야 한다.

### 13.5 초대 연결 및 재접속 정책

아래 서버의 초대 전송·응답·재접속 복구 정책은 2단계까지 구현했다. 3단계에서 로컬 MySQL과 실제 HTTP·WebSocket으로 초대 흐름을 검증했다. Android의 화면 처리 및 외부 네트워크 검증은 별도로 구분한다.

- `RIDE_INVITE`는 초대 대상의 모든 유효한 연결로, `RIDE_INVITE_STATUS`는 선두의 모든 유효한 연결로 전송한다.
- 각 연결의 Ping과 텍스트 메시지 전송을 직렬화한다. 한 연결의 전송 실패가 다른 연결의 전송을 중단하지 않도록 하고, 실패한 연결은 기존 정책대로 정리한다.
- 연결 종료만으로 PENDING·ACCEPTED·REJECTED 상태를 변경하거나 초대 전체를 취소하지 않는다.
- 새 `/ws/login` 연결을 등록하면 해당 연결에만 복구 메시지를 보낸다. 선두에게는 자신이 생성한 유효한 초대들의 최신 `RIDE_INVITE_STATUS`를, 초대 대상에게는 아직 PENDING인 유효한 초대들의 `RIDE_INVITE`를 재전송한다.
- 이미 ACCEPTED·REJECTED로 응답한 대상에게 초대 팝업을 다시 보내지 않는다. 응답 확정 상태를 대상의 다른 기기에 동기화하는 별도 메시지는 추가하지 않는다.
- Android는 `invitationId`를 기준으로 중복 초대 팝업을 방지하고, 참여 현황은 수신한 전체 목록으로 갱신한다. `INVITATION_NOT_FOUND` 오류를 받으면 해당 초대 화면을 정리한다.
- REST 생성 응답보다 WebSocket 알림이 먼저 도착할 수 있으므로 Android는 `invitationId`별로 메시지를 처리한다.
- 자동 재전송은 재접속 시에만 수행한다. 메시지별 수신 확인, 영구 큐, 오프라인 푸시는 도입하지 않는다.
- 기본 취소 구현 후에도 복구 대상은 유효한 초대뿐이다. `RIDE_INVITE_CANCELLED`는 연결 중인 사용자에게만 전달하고 재접속 시 재전송하지 않는다(9.4절).

### 13.6 라이딩 초대 구현 단계

- 0단계: 그룹장 권한 및 MVP 정책 명세 확정(2026-11-01). 코드·DB 변경 없음.
- 1단계(구현 완료): 초대 생성 REST, 메모리 상태·30분 만료 정리, 초대 및 최초 현황 전송. 검증 범위는 RIDE_INVITATION_VERIFICATION.md를 참고한다.
- 2단계(구현 완료): 수락·거절, 현황 갱신, WebSocket 오류 처리, 재접속 복구. 초대별 잠금으로 최초 알림·응답·복구·만료 정리의 순서를 보호한다.
- 3단계(검증 완료): 실제 REST·WebSocket 초대 통합 테스트 3개 추가. 전체 82개 통과(실패·오류·건너뜀 0), 빌드 성공. 상세 시나리오 및 검증 한계는 RIDE_INVITATION_VERIFICATION.md를 참고한다.
- 각 구현 단계에 관련 테스트를 포함하고, 단계 완료 후 다음 단계는 별도 승인을 받아 진행한다.
- 기본 취소(2026-11-07, 구현 완료): 8.5·9.4절의 취소 REST·웹소켓 알림·동시 요청 처리를 구현했다. 검증 결과와 한계는 RIDE_CANCEL_VERIFICATION.md를 참고한다. 오프라인 취소 복구는 제외한다.
- 라이딩 시작·주행 조회·`/ws/ride`·종료·기록·통계 구현은 위 1~3단계 이후 별도 작업이다.

---

## 14. 공통 오류 처리

REST API 실패 시 다음 구조로 응답한다.

```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "존재하지 않는 그룹입니다."
}
```

주요 HTTP 상태 코드:

| HTTP 상태 | 의미 |
|---|---|
| 400 | 요청 데이터 오류 |
| 401 | 인증 실패 또는 토큰 오류 |
| 403 | 접근 권한 없음 |
| 404 | 요청한 데이터 없음 |
| 409 | 중복 신청 또는 상태 충돌 |
| 500 | 서버 내부 오류 |

WebSocket 메시지 처리 오류는 다음 형식을 사용한다.

```json
{
  "type": "ERROR",
  "code": "INVITATION_NOT_FOUND",
  "message": "존재하지 않는 라이딩 초대입니다."
}
```

---

## 15. API 전체 목록

### REST API

| HTTP | 엔드포인트 | 기능 |
|---|---|---|
| POST | `/api/v1/auth/login` | 로그인 |
| GET | `/api/v1/auth/check-login-id` | 아이디 중복 확인 |
| GET | `/api/v1/auth/check-nickname` | 닉네임 중복 확인 |
| POST | `/api/v1/auth/signup` | 회원가입 |
| GET | `/api/v1/users/me/home` | 메인 화면 정보 |
| GET | `/api/v1/groups` | 전체 그룹 조회 |
| POST | `/api/v1/groups/{groupId}/join` | 그룹 가입 신청 |
| POST | `/api/v1/groups` | 그룹 생성 |
| GET | `/api/v1/groups/my` | 내 그룹 조회 |
| GET | `/api/v1/groups/{groupId}/members` | 그룹 멤버 조회 |
| POST | `/api/v1/ride-invitations` | 라이딩 초대 생성 |
| POST | `/api/v1/ride-invitations/{invitationId}/start` | 라이딩 시작 |
| POST | `/api/v1/ride-invitations/{invitationId}/cancel` | 시작 전 초대 취소 (기본 취소 구현 완료) |
| GET | `/api/v1/rides/{rideSessionId}` | 주행 화면 정보 |
| POST | `/api/v1/rides/{rideSessionId}/finish` | 라이딩 종료 |
| PUT | `/api/v1/rides/{rideSessionId}/record` | 개인 주행 기록 저장 |
| GET | `/api/v1/groups/join-requests` | 그룹 가입 신청 통합 조회 |
| POST | `/api/v1/groups/{groupId}/requests/{userId}/approve` | 그룹 가입 승인 |
| POST | `/api/v1/groups/{groupId}/requests/{userId}/reject` | 그룹 가입 거절 |

**위 목록의 REST API 총 19개 (취소 명세 포함, 구현 완료 개수 아님)**

### WebSocket

| 경로 | 목적 |
|---|---|
| `/ws/login` | 로그인 접속 상태 및 라이딩 초대 관리 |
| `/ws/ride/{rideSessionId}` | 실시간 수신호 및 라이딩 종료 알림 |

**WebSocket 연결 경로 총 2개**

---

## 16. 구현 원칙

1. 본 문서는 Android와 Spring Boot 간 통신 명세를 정의한다.
2. 기존 API 명세와 데이터베이스 문서의 구조를 유지한다.
3. 요청·응답 필드 및 엔드포인트를 임의로 변경하지 않는다.
4. REST API와 WebSocket의 역할을 구분한다.
5. DB가 필요한 기능은 JPA Repository 및 Service를 통해 구현한다.
6. 실시간 임시 상태는 서버 메모리에서 관리한다.
7. API 권한 및 비즈니스 검증은 서버에서 수행한다.
8. 명세 변경이 필요한 경우 변경 사유와 영향 범위를 먼저 확인한다.

**최종 원칙:** RideMate는 MVP 시연을 목표로 하며, 현재 UI 구현에 필요한 기능을 우선 개발한다. 불필요한 기능 확장이나 복잡한 구조 도입은 지양한다.
