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
2. 로그인과 회원가입을 제외한 API는 JWT 인증을 사용한다.
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
- Android는 JWT를 저장한다.
- 로그인 성공 후 `/ws/login` WebSocket에 연결한다.
- 연결이 완료되면 메인 화면으로 이동한다.

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
- 사용자는 목록에서 라이딩을 진행할 그룹을 선택한다.
- 선택된 사용자는 해당 라이딩 세션의 선두 주자가 된다.

---

## 8. 멤버 선택 및 라이딩 참여 현황

### 8.1 그룹 멤버 조회

**GET `/api/v1/groups/{groupId}/members`**

Response

```json
[
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
```

처리 규칙:

- APPROVED 상태의 그룹원만 조회한다.
- `online`은 WebSocket 접속 상태를 기준으로 판단한다.
- 현재 로그인한 사용자는 초대 대상에서 제외한다.
- 그룹 멤버 목록은 DB에서, 온라인 상태는 서버 메모리에서 조회한다.

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

- 요청한 사용자가 선두 주자가 된다.
- 해당 그룹의 APPROVED 멤버만 초대할 수 있다.
- 초대 대상은 본인을 제외한 사용자이다.
- 서버는 고유한 invitationId를 생성한다.
- 초대 대상과 참여 상태를 서버 메모리에 저장한다.
- 초대 대상에게 WebSocket으로 참여 요청을 전달한다.
- 이 단계에서는 RIDE_SESSION을 생성하지 않는다.

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

- 초대 생성자만 라이딩을 시작할 수 있다.
- 선두 포함 수락 인원이 2명 이상이어야 한다.
- RIDE_SESSION을 생성하고 RIDING 상태로 저장한다.
- ACCEPTED 상태의 사용자만 RIDE_SESSION_MEMBER에 등록한다.
- 선두는 LEADER, 나머지 참여자는 MEMBER로 저장한다.
- 서버는 확정된 참여자들에게 WebSocket으로 라이딩 시작을 알린다.
- 라이딩 시작 후 해당 invitationId의 임시 상태는 정리한다.

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
4. 연결이 종료되면 해당 사용자 연결 정보를 정리한다.
5. 라이딩 초대는 현재 연결된 사용자에게 전달한다.

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

### 13.3 WebSocket 메시지 목록

| 경로 | type | 기능 |
|---|---|---|
| `/ws/login` | RIDE_INVITE | 라이딩 초대 전달 |
| `/ws/login` | RIDE_INVITE_RESPONSE | 초대 수락·거절 |
| `/ws/login` | RIDE_INVITE_STATUS | 참여 현황 갱신 |
| `/ws/login` | RIDE_STARTED | 라이딩 시작 알림 |
| `/ws/ride/{rideSessionId}` | RIDE_SIGNAL | 실시간 수신호 |
| `/ws/ride/{rideSessionId}` | RIDE_FINISHED | 라이딩 종료 알림 |

### 13.4 invitationId 관리

- invitationId는 라이딩 초대 요청마다 생성하는 고유 식별자이다.
- invitationId와 초대 상태는 서버 메모리에서 관리한다.
- invitationId는 데이터베이스에 저장하지 않는다.
- 실제 라이딩 시작 시 별도의 rideSessionId가 생성된다.
- 서버 재시작 시 진행 중인 임시 초대 요청은 취소된 것으로 처리한다.
- 라이딩 시작 및 초대 취소 시 불필요한 임시 데이터를 정리한다.

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
| GET | `/api/v1/rides/{rideSessionId}` | 주행 화면 정보 |
| POST | `/api/v1/rides/{rideSessionId}/finish` | 라이딩 종료 |
| PUT | `/api/v1/rides/{rideSessionId}/record` | 개인 주행 기록 저장 |
| GET | `/api/v1/groups/join-requests` | 그룹 가입 신청 통합 조회 |
| POST | `/api/v1/groups/{groupId}/requests/{userId}/approve` | 그룹 가입 승인 |
| POST | `/api/v1/groups/{groupId}/requests/{userId}/reject` | 그룹 가입 거절 |

**REST API 총 18개**

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