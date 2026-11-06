# 1. 회원가입 화면
![[Pasted image 20261106225704.png]]

### 버튼별 명세
#### 중복확인 버튼
요청)
```http
GET /api/v1/auth/check-login-id?loginId="입력값"
```

응답)
```json
{
  "available": true
}
```
- `true`: “사용 가능한 아이디입니다.” 표시
- `false`: “이미 사용 중인 아이디입니다.” 표시

#### 가입하기 버튼
요청)
```http
POST /api/v1/auth/signup
Content-Type: application/json
```
요청 body)
```json
{
  "loginId": "donghyun",
  "password": "1234",
  "passwordConfirm": "1234",
  "nickname": "동현"
}
```

응답(성공시)
```json
{
  "userId": 1,
  "nickname": "동현"
}
```
응답(실패시)
```json
{
  "code": "PASSWORD_MISMATCH",
  "message": "비밀번호 확인이 일치하지 않습니다."
}
```

| 실패 코드                | 앱 안내 예시                        |
| -------------------- | ------------------------------ |
| `PASSWORD_MISMATCH`  | 비밀번호가 일치하지 않습니다.               |
| `INVALID_PASSWORD`   | 비밀번호 입력 길이를 확인해 주세요.           |
| `DUPLICATE_LOGIN_ID` | 이미 사용 중인 아이디입니다.               |
| `DUPLICATE_NICKNAME` | 이미 사용 중인 닉네임입니다.               |
| `DUPLICATE_USER`     | 아이디 또는 닉네임이 중복됩니다. 다시 확인해 주세요. |

#### 입력조건
모든 입력은 필수이며 공백만 입력할 수 없다. 아이디·닉네임은 최대 50자, 비밀번호는 UTF-8 기준 최대 72바이트이며 비밀번호 확인과 일치해야 한다.

회원가입 완료 후 로그인화면으로 이동한다.

----

# 2. 로그인 화면

![[Pasted image 20261106231128.png]]




#### 로그인 버튼

요청)
```http
POST /api/v1/auth/login
Content-Type: application/json
```

요청 body)
```json
{
  "loginId": "donghyun",
  "password": "1234"
}
```

응답 — 성공 시 HTTP 200)
```json
{
  "accessToken": "JWT_TOKEN",
  "userId": 1,
  "nickname": "동현"
}
```

앱 처리)
1. `accessToken`, `userId`, `nickname`을 앱에 저장한다.
2. 받은 토큰으로 `/ws/login` 웹소켓에 연결한다.
3. **웹소켓 연결이 완료되면 메인 화면으로 이동한다.**

응답 — 실패 시 HTTP 401)

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 올바르지 않습니다."
}
```

- 로그인 화면을 유지하고 오류 안내를 표시한다.
- 요청 중에는 로그인 버튼의 중복 클릭을 막는다.

#### 로그인 후 웹소켓 연결

연결 주소)

```text
ws://서버주소:포트/ws/login
```


연결 요청 헤더)
```http
Authorization: Bearer JWT_TOKEN
```

- `JWT_TOKEN`에는 로그인 응답의 `accessToken` 값을 넣는다.
- **최초 웹소켓 연결 요청의 헤더**에 설정한다. 토큰을 URL이나 연결 후 JSON 메시지로 보내지 않는다.
- 표준 WebSocket을 사용한다. STOMP/SockJS는 사용하지 않는다.
- 연결 성공은 앱의 웹소켓 **연결 완료 콜백**으로 확인한다. 별도의 로그인 성공 JSON 메시지를 기다리지 않는다.

연결 후 처리)
- 서버는 `/ws/login` 연결이 유지되는 사용자를 온라인으로 판단한다.
- **메인 화면으로 이동해도 연결을 유지한다.**
- 이후 서버가 보내는 라이딩 초대·참여 현황·라이딩 시작 메시지를 수신한다.
- 화면 이동 때마다 새로 연결하지 않도록 앱에서 연결을 공통 관리한다.

연결 유지 및 오류 처리)

|상황|앱 처리|
|---|---|
|서버의 Ping 수신|WebSocket Pong으로 응답한다. 라이브러리의 자동 응답 지원 여부를 확인한다.|
|네트워크 문제로 연결 실패·끊김|연결 상태를 안내하고, 유효한 토큰으로 간격을 두고 재연결한다.|
|연결 요청이 HTTP 401로 거부됨|저장된 토큰을 정리하고 다시 로그인하도록 안내한다.|
|로그아웃|저장된 토큰·사용자 정보를 삭제하고 웹소켓을 종료하며 자동 재연결을 중단한다.|

- 서버는 **30초 간격으로 Ping**을 보내며, **마지막 Pong 이후 90초를 초과하면 연결을 종료**한다. Ping/Pong은 JSON 메시지가 아닌 웹소켓 제어 프레임이다.
- 토큰 기본 유효기간은 **1시간**이며, 연결 중 토큰이 만료되어도 서버가 연결을 종료한다. 현재 토큰 갱신 API는 없으므로 다시 로그인해야 한다.
- 로그인 직후 웹소켓 연결에 실패했다면, **메인 화면으로 이동하지 않고 연결 재시도를 안내하는 방식**을 권장한다.

이후 REST API의 인증)
로그인 이후 인증이 필요한 API에는 저장한 토큰을 다음 헤더로 보낸다.

```
Authorization: Bearer JWT_TOKEN
```
- 웹소켓에 연결되어 있어도 REST API 요청에는 매번 인증 헤더가 필요하다.

회원가입 버튼)
- 회원가입 화면으로 이동한다.
- 버튼 클릭 자체에는 서버 요청이 필요하지 않다.

---
# 3. 메인화면 - "주등비"님이 작업 예정.
![[Pasted image 20261107014530.png|400]]

현재 기능구현이 되어있지 않습니다.
주등비님이 작업하실 영역이고 추후에 주등비님이 아래 요청 API완성되면 그쪽으로 연결하시면 됩니다.
예시 API)
```http
GET /api/v1/users/me/home
Authorization: Bearer JWT_TOKEN
```

현재 개발단계에서는 


---
# 4. 그룹 리스트

![[Pasted image 20261106231857.png]]

#### 화면 진입 — 그룹 목록 조회

요청)

```http
GET /api/v1/groups
Authorization: Bearer JWT_TOKEN
```

요청 body 없음.

응답 — 성공 시 HTTP 200)

```json
[
  {
    "groupId": 2,
    "groupName": "B그룹",
    "description": "한강 중심 라이딩",
    "memberCount": 8,
    "maxMembers": 10,
    "membershipStatus": null,
    "myRole": null
  },
  {
    "groupId": 3,
    "groupName": "C그룹",
    "description": "한강 중심 라이딩",
    "memberCount": 8,
    "maxMembers": 10,
    "membershipStatus": "PENDING",
    "myRole": "MEMBER"
  },
  {
    "groupId": 4,
    "groupName": "한강 모임",
    "description": "한강 중심 라이딩",
    "memberCount": 8,
    "maxMembers": 10,
    "membershipStatus": "APPROVED",
    "myRole": "MEMBER"
  }
]
```

화면 표시)

|응답 필드|UI 표시|
|---|---|
|`groupName`|그룹 이름|
|`description`|그룹 설명|
|`memberCount` / `maxMembers`|인원 표시: `8 / 10`|
|`membershipStatus`|가입 버튼 상태 결정|
|`groupId`|해당 그룹에 가입 신청할 때 사용|

버튼 상태)

|`membershipStatus`|버튼 표시|클릭 가능 여부|
|---|---|---|
|`null`|가입 신청|가능|
|`PENDING`|신청중|불가능|
|`APPROVED`|가입됨|불가능|
|`REJECTED`|가입 신청|가능 — 재신청|

- `memberCount`는 **승인된 그룹원 수**이며, 신청 대기자는 포함하지 않는다.
- 응답이 빈 배열 `[]`이면 “등록된 그룹이 없습니다.”를 표시한다.

---

#### 가입 신청 버튼

요청)

```http
POST /api/v1/groups/2/join
Authorization: Bearer JWT_TOKEN
```

- `2`에는 선택한 그룹의 `groupId`를 넣는다.
- 요청 body 없음. 신청자는 JWT로 식별하므로 사용자 ID를 보내지 않는다.

응답 — 성공 시 HTTP 200)

```json
{
  "groupId": 2,
  "status": "PENDING"
}
```

앱 처리)
- 현재 화면을 유지한다.
- 해당 그룹의 버튼을 **‘신청중’으로 변경하고 비활성화**한다.
- 아직 가입 승인이 아니므로 인원수는 증가시키지 않는다.
- 요청 중에는 해당 버튼의 중복 클릭을 막는다.

응답 — 실패 예시)

```json
{
  "code": "GROUP_JOIN_CONFLICT",
  "message": "이미 가입했거나 처리 중인 가입 신청이 있습니다."
}
```

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|로그인 필요 안내 후 로그인 화면으로 이동|
|404|`GROUP_NOT_FOUND`|존재하지 않는 그룹 안내 후 목록 재조회|
|409|`GROUP_JOIN_CONFLICT`|이미 가입했거나 신청 중임을 안내하고 목록 재조회|

#### 목록 갱신 및 웹소켓
- 로그인 때 연결한 `/ws/login`은 유지한다. 이 화면을 위해 새 웹소켓을 연결하지 않는다.
- 화면 재진입 또는 새로고침 시 `GET /api/v1/groups`를 다시 호출해 버튼 상태와 인원수를 갱신한다.

#### 그룹 생성 버튼 / 뒤로 가기 버튼
- **그룹 생성:** 그룹 생성 팝업을 연다. 버튼 클릭 자체에는 서버 요청이 없다. 생성 완료 후 목록을 다시 조회한다.
- **뒤로 가기:** 이전 화면으로 이동한다. `/ws/login` 연결은 유지한다.

----
# 5. 그룹 생성
![[Pasted image 20261106232256.png]]

#### 입력 항목

|UI 입력란|요청 필드|입력 조건|
|---|---|---|
|그룹 이름|`groupName`|필수, 공백만 입력 불가, 최대 100자|
|그룹 설명|`description`|선택, 최대 500자|
|최대 인원|`maxMembers`|필수, 2~50 사이의 정수|
- 최대 인원에는 그룹장 본인도 포함한다.

#### 그룹 생성 버튼
요청)
```http
POST /api/v1/groups
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

요청 body)
```json
{
  "groupName": "한강모임",
  "description": "한강 중심 라이딩",
  "maxMembers": 10
}
```
- `maxMembers`는 따옴표 없는 숫자로 보낸다.
- 설명을 입력하지 않았다면 `description`은 빈 문자열 `""`로 보내도 된다.

응답 — 성공 시 HTTP 201)
```json
{
  "groupId": 1,
  "groupName": "한강모임"
}
```

앱 처리 - 그룹생성 시)
1. 그룹 생성 팝업을 닫는다.
2. `GET /api/v1/groups`를 다시 호출하여 그룹 목록을 갱신한다.
3. 생성한 그룹은 본인이 **그룹장으로 자동 가입**되므로 ‘가입됨’으로 표시한다. 최초 인원은 `1 / 최대 인원`이다.

응답 — 실패 시)

```json
{
  "code": "INVALID_REQUEST",
  "message": "최대 인원은 2명 이상이어야 합니다."
}
```

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|400|`INVALID_REQUEST`|팝업과 입력값을 유지하고 `message` 표시|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|로그인 필요 안내 후 로그인 화면으로 이동|
|500|`INTERNAL_SERVER_ERROR`|입력값을 유지하고 서버 오류 안내|

#### 취소 버튼
- 서버 요청 없이 팝업을 닫고 그룹 목록으로 돌아간다.

#### 웹소켓
- 이 팝업에서는 웹소켓 메시지를 보내지 않는다.
- 기존 `/ws/login` 연결은 유지한다.

----
# 6. 알림 내역 팝업화면
![[Pasted image 20261106232911.png|400]]

이 팝업은 **그룹장이 자신이 소유한 그룹의 가입 신청을 확인하고 승인·거절하는 화면**입니다. 처리 완료된 과거 내역은 포함하지 않습니다.

![[Pasted image 20261106233149.png]]메인화면 알림 버튼 — 가입 신청 목록 조회
- 
요청)
```http
GET /api/v1/groups/join-requests
Authorization: Bearer JWT_TOKEN
```

요청 body 없음.

응답 — 성공 시 HTTP 200)

```json
[
  {
    "groupMemberId": 15,
    "groupId": 1,
    "groupName": "A그룹",
    "userId": 5,
    "nickname": "홍길동",
    "status": "PENDING"
  },
  {
    "groupMemberId": 18,
    "groupId": 1,
    "groupName": "A그룹",
    "userId": 8,
    "nickname": "이순신",
    "status": "PENDING"
  }
]
```

앱 처리)
- 각 항목을 **“{nickname}님이 {groupName}에 가입을 요청하였습니다.”**로 표시한다.
- 소유한 그룹이 여러 개라면 모든 그룹의 대기 신청이 함께 반환된다.
- 빈 배열 `[]`이면 “대기 중인 가입 신청이 없습니다.”를 표시한다.
- 소유한 그룹이 없는 사용자는 **HTTP 403(`FORBIDDEN`)**을 받는다. 이 경우 “그룹장만 가입 신청을 확인할 수 있습니다.”를 안내한다.

---

#### 수락 버튼
요청)
```http
POST /api/v1/groups/1/requests/5/approve
Authorization: Bearer JWT_TOKEN
```
- `1`: 해당 항목의 `groupId`
- `5`: 신청자의 `userId`
- 요청 body 없음.

응답 — 성공 시 HTTP 200)
```json
{
  "groupId": 1,
  "userId": 5,
  "status": "APPROVED"
}
```

앱 처리)
- 팝업을 유지하고 처리한 신청을 목록에서 제거한다.
- 신청자는 정식 그룹원이 된다.

---

#### 거절 버튼
요청)
```http
POST /api/v1/groups/1/requests/5/reject
Authorization: Bearer JWT_TOKEN
```
- 요청 body 없음.

응답 — 성공 시 HTTP 200)

```json
{
  "groupId": 1,
  "userId": 5,
  "status": "REJECTED"
}
```

앱 처리)
- 팝업을 유지하고 처리한 신청을 목록에서 제거한다.
- 그룹 인원은 증가하지 않는다.
- 거절된 사용자는 이후 다시 가입 신청할 수 있다.

---

응답 — 실패 시)
```json
{
  "code": "GROUP_FULL",
  "message": "그룹 최대 인원에 도달했습니다."
}
```

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|로그인 필요 안내 후 로그인 화면으로 이동|
|403|`FORBIDDEN`|해당 그룹장만 처리할 수 있음을 안내|
|404|`GROUP_NOT_FOUND`, `JOIN_REQUEST_NOT_FOUND`|대상이 없음을 안내하고 신청 목록 재조회|
|409|`JOIN_REQUEST_CONFLICT`|이미 처리된 신청임을 안내하고 목록 재조회|
|409|`GROUP_FULL`|정원 초과 안내. 신청은 대기 상태로 유지|

---

**알림 배지와 웹소켓**
- 기존 `/ws/login` 연결은 유지한다.


---
# 7. 라이딩 준비(1) - 그룹 선택
![[Pasted image 20261106233759.png]]

이 화면에서는 **본인이 그룹장인 그룹 중 라이딩할 그룹 하나를 선택**합니다. 초대 생성자인 그룹장이 선두 주자가 됩니다.

#### 화면 진입 — 그룹 목록 조회
요청)
```http
GET /api/v1/groups/owned
Authorization: Bearer JWT_TOKEN
```
- 요청 body 없음.

응답 — 성공 시 HTTP 200)
```json
[
  {
    "groupId": 1,
    "groupName": "한강 모임",
    "description": "한강 중심 라이딩",
    "memberCount": 13,
    "maxMembers": 20,
    "membershipStatus": "APPROVED",
    "myRole": "OWNER"
  },
  {
    "groupId": 2,
    "groupName": "B그룹",
    "description": "샘플용 그룹",
    "memberCount": 8,
    "maxMembers": 10,
    "membershipStatus": "APPROVED",
    "myRole": "OWNER"
  }
]
```

화면 표시)

|응답 필드|UI 표시|
|---|---|
|`groupName`|그룹 이름|
|`description`|그룹 설명|
|`memberCount` / `maxMembers`|인원 표시: `13 / 20`|
|`groupId`|선택한 그룹 식별 및 다음 화면 조회에 사용|

- 인원수는 **그룹장을 포함한 승인된 그룹원 수**이며, 현재 온라인 인원이나 라이딩 참여 인원이 아니다.
- 빈 배열 `[]`이면 “라이딩을 시작할 수 있는 그룹이 없습니다. 그룹을 생성해 주세요.”를 표시하고 선택 완료 버튼을 비활성화한다.

---

#### 그룹 선택
- 한 번에 하나의 그룹만 선택한다.
- 선택한 `groupId`를 앱에서 보관한다.
- 선택 표시만 변경하며 서버 요청은 보내지 않는다.
- 선택한 그룹이 없으면 ‘선택 완료’ 버튼을 비활성화한다.

---

#### 선택 완료 버튼

앱 처리)
1. 선택한 `groupId`를 다음 화면에 전달한다.
2. **멤버 선택 화면으로 이동**한다.
3. 다음 화면에서 해당 그룹의 멤버 목록을 조회한다.

``` http
GET /api/v1/groups/1/members
Authorization: Bearer JWT_TOKEN
```
- `1`에는 선택한 `groupId`를 넣는다.
- 이 단계에서는 그룹만 선택한다. **라이딩 초대 전송과 실제 라이딩 시작은 이후 단계에서 처리한다.**

---

#### 목록 조회 실패 시

|상황|앱 처리|
|---|---|
|HTTP 401 — `UNAUTHORIZED`, `INVALID_TOKEN`|로그인 필요 안내 후 로그인 화면으로 이동|
|서버 오류 또는 네트워크 오류|조회 실패 안내와 재시도 제공. 목록이 없다는 안내와 구분|

#### **뒤로 가기 / 웹소켓**
- 뒤로 가기는 이전 화면으로 이동하며 별도 서버 요청은 없다.
- 기존 `/ws/login` 연결을 유지한다.
- 이 화면에서 보내는 웹소켓 메시지는 없다. 이후 라이딩 초대를 생성할 때 `/ws/login` 연결이 필요하다.

----
# 8. 라이딩 준비(2) - 멤버 선택
![[Pasted image 20261106234429.png]]

#### 화면 진입 — 그룹 정보 및 멤버 조회

요청)
```http
GET /api/v1/groups/1/members
Authorization: Bearer JWT_TOKEN
```
- `1`에는 이전 화면에서 선택한 `groupId`를 넣는다.
- 요청 body 없음.

응답 — 성공 시 HTTP 200)
```json
{
  "groupId": 1,
  "groupName": "한강 모임",
  "memberCount": 4,
  "description": "한강에서 즐기는 라이딩 동호회",
  "members": [
    {
      "userId": 1,
      "nickname": "홍길동",
      "role": "OWNER",
      "online": true
    },
    {
      "userId": 2,
      "nickname": "이순신",
      "role": "MEMBER",
      "online": false
    },
    {
      "userId": 3,
      "nickname": "장발장",
      "role": "MEMBER",
      "online": true
    },
    {
      "userId": 4,
      "nickname": "김철수",
      "role": "MEMBER",
      "online": true
    }
  ]
}
```

화면 표시)

|응답 필드|UI 처리|
|---|---|
|`groupName`|상단 그룹 이름|
|`description`|상단 그룹 설명. `null` 또는 빈 문자열이면 설명 생략|
|`memberCount`|상단 그룹 인원: `4명`|
|`members`|멤버 선택 목록|
|`members[].nickname`|멤버 이름|
|`members[].online`|`true`: 접속 중 / `false`: 오프라인|

- 응답은 **객체**이며, 멤버 목록은 내부의 `members` 배열에서 읽는다.
- 승인된 그룹원만 포함되며, `memberCount`는 **본인을 포함한 `members` 배열 길이**이다.
- 로그인한 본인의 `userId`는 선택 목록에서 제외한다. 상단 인원수는 그대로 표시한다.
- 위 예시에서 로그인 사용자가 `userId: 1`이면 선택 목록에는 이순신·장발장·김철수만 표시한다.
- 온라인 상태는 조회 시점의 상태이다. 새로고침 시 같은 API를 다시 호출한다.

---

#### 멤버 선택
- 접속 중인 멤버만 선택할 수 있도록 한다.
- 여러 명 선택할 수 있으며, 선택한 `userId`를 앱에서 보관한다.
- 본인을 제외하고 **최소 1명**을 선택해야 ‘선택 완료’를 활성화한다.
- 체크할 때마다 서버 요청을 보내지는 않는다.

---

#### 선택 완료 버튼 — 라이딩 초대 메시지 전송

요청)
```http
POST /api/v1/ride-invitations
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

요청 body)
```json
{
  "groupId": 1,
  "inviteeIds": [3, 4]
}
```
- `inviteeIds`에 선택한 멤버의 `userId`를 넣는다. 본인ID와 중복 ID는 제외한다.
- 해당 그룹의 그룹장만 초대를 생성할 수 있다.
- **본인과 초대 대상 모두 `/ws/login`에 연결되어 있어야 한다.**

응답 — 성공 시 HTTP 201)
```
{
  "invitationId": "inv-100",
  "groupId": 1,
  "status": "PENDING",
  "invitedCount": 2
}
```

앱 처리)
1. `invitationId`를 보관한다.
2. **라이딩 참여 현황 화면으로 이동**한다.
3. 웹소켓으로 받는 참여 현황을 표시한다.
    - `invitedCount`는 본인을 제외한 초대 대상 수이다.
    - 아직 라이딩이 시작된 것은 아니며, `rideSessionId`는 생성되지 않는다.
    - 요청 중에는 ‘선택 완료’ 버튼의 중복 클릭을 막는다.

---

#### 초대 생성 후 웹소켓 수신

기존 `/ws/login` 연결을 유지한다.

|수신자|메시지|앱 처리|
|---|---|---|
|초대 대상|`RIDE_INVITE`|라이딩 초대 팝업 표시|
|초대한 그룹장|`RIDE_INVITE_STATUS`|참여 현황 표시·갱신|

그룹장이 받는 최초 현황 예시)
```json
{
  "type": "RIDE_INVITE_STATUS",
  "invitationId": "inv-100",
  "participants": [
    {
      "userId": 1,
      "nickname": "홍길동",
      "status": "ACCEPTED"
    },
    {
      "userId": 3,
      "nickname": "장발장",
      "status": "PENDING"
    },
    {
      "userId": 4,
      "nickname": "김철수",
      "status": "PENDING"
    }
  ],
  "acceptedCount": 1,
  "canStart": false
}
```
- 그룹장 본인은 자동으로 `ACCEPTED`이다.
- 수락·거절이 발생하면 전체 현황이 다시 전송되므로, 수신한 목록으로 화면을 갱신한다.
- **웹소켓 메시지가 REST 성공 응답보다 먼저 도착할 수 있다.** 화면 이동 전부터 메시지를 수신하고 `invitationId`별로 보관한다.
- 참여 현황을 조회하는 별도 REST API는 없다.

---

#### 요청 실패 시

실패 응답 예시 — 초대 생성 시 HTTP 409)
```json
{
  "code": "USER_OFFLINE",
  "message": "..."
}
```

`message`는 서버가 반환한 안내 문구를 사용한다.

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|400|`INVALID_REQUEST`|초대 인원·중복 ID·본인 포함 여부 확인|
|400|`INVALID_INVITEE`|초대할 수 없는 멤버 안내 후 목록 재조회|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|로그인 필요 안내 후 로그인 화면으로 이동|
|403|`FORBIDDEN`|조회 권한 또는 초대 생성 권한이 없음을 안내|
|404|`GROUP_NOT_FOUND`|그룹이 없음을 안내하고 그룹 선택 화면으로 이동|
|409|`USER_OFFLINE`|본인 웹소켓 연결 확인 및 멤버 목록 재조회|

- 조회 후 멤버가 오프라인으로 바뀌면 **초대 생성 요청 전체가 거부**된다.
- 네트워크 오류로 생성 결과를 받지 못했을 때 자동으로 요청을 반복하지 않는다. 재호출하면 별도의 초대가 생성될 수 있다.

#### 뒤로 가기
- 초대 생성 전에는 서버 요청 없이 그룹 선택 화면으로 돌아간다.
- 기존 `/ws/login` 연결은 유지한다.

----
# 9. 라이딩 참여 현황 팝업
![[Pasted image 20261107004647.png]]

#### 화면 진입 및 참여 현황 수신
- 초대 생성 성공 후 메인화면으로 자동 이동 후 팝업을 표시한다.
- 이전 단계에서 받은 `invitationId`를 사용한다.
- 별도의 조회 API 없이 기존 `/ws/login`에서 현황을 받는다.

서버 → 리더)
```json
{
  "type": "RIDE_INVITE_STATUS",
  "invitationId": "inv-100",
  "participants": [
    {
      "userId": 1,
      "nickname": "홍길동",
      "status": "ACCEPTED"
    },
    {
      "userId": 3,
      "nickname": "장발장",
      "status": "ACCEPTED"
    },
    {
      "userId": 4,
      "nickname": "김철수",
      "status": "PENDING"
    },
    {
      "userId": 5,
      "nickname": "이순신",
      "status": "REJECTED"
    }
  ],
  "acceptedCount": 2,
  "canStart": true
}
```

- 현재 팝업의 `invitationId`와 일치하는 메시지만 반영한다.
- 수락·거절 시 전체 현황이 다시 오므로 **기존 값에 더하지 말고 새 목록으로 갱신**한다.
- 화면 진입 전에 수신한 현황이 있다면 해당 값으로 초기 표시한다.

#### 요청·수락·대기·거절 인원 표시

|UI 항목|계산 방법|
|---|---|
|요청|`participants`에서 본인을 제외한 인원|
|수락|본인을 제외한 `ACCEPTED` 인원|
|대기|`PENDING` 인원|
|거절|`REJECTED` 인원|

서버의 `participants`와 `acceptedCount`에는 **자동 수락된 리더 본인도 포함**된다.

---

#### 라이딩 시작 버튼

- 서버의 `canStart: true`이면 활성화한다.
- `canStart: false`이면 비활성화한다.
- 리더를 포함해 수락자가 2명 이상이면 시작할 수 있다. **모두의 응답을 기다릴 필요는 없다.**
- 현황을 아직 받지 못했다면 버튼을 비활성화한다.

요청)
```http
POST /api/v1/ride-invitations/inv-100/start
Authorization: Bearer JWT_TOKEN
```

- `inv-100`에는 현재 `invitationId`를 넣는다.
- 요청 body 없음.
- **시작 또는 취소 요청 중에는 라이딩 시작·라이딩 취소 버튼을 모두 비활성화한다.**

응답 — 성공 시 HTTP 200)
```json
{
  "rideSessionId": 10,
  "groupId": 1,
  "status": "RIDING",
  "startedAt": "2026-11-07T14:00:00"
}
```

앱 처리)
1. `rideSessionId`를 보관하고 대기 팝업을 닫는다.
2. 리더 주행 화면으로 이동한다.
3. 주행 화면 정보를 조회하고 라이딩 웹소켓을 연결한다.

```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

```text
ws://서버주소:포트/ws/ride/10
```

웹소켓 최초 연결 요청 헤더)

```http
Authorization: Bearer JWT_TOKEN
```

- 기존 `/ws/login` 연결도 유지한다.
- **시작 시점에 `ACCEPTED`인 사용자만 참여자로 확정**된다. 대기·거절 사용자는 제외된다.

---

#### 라이딩 시작 알림

서버는 리더를 포함한 확정 참여자에게 `/ws/login`으로 다음 메시지도 보낸다.

```json
{
  "type": "RIDE_STARTED",
  "rideSessionId": 10,
  "groupId": 1
}
```

- 리더는 REST 성공 응답과 이 알림을 모두 받을 수 있다.
- **같은 `rideSessionId`에 대한 화면 이동과 웹소켓 연결은 한 번만 처리**한다.
- 초대를 수락한 멤버는 이 알림을 받아 주행 화면으로 이동한다.

---

#### 시작 요청 실패 시

```json
{
  "code": "INSUFFICIENT_PARTICIPANTS",
  "message": "..."
}
```

`message`는 서버가 반환한 안내 문구를 사용한다.

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|다시 로그인하도록 안내|
|403|`FORBIDDEN`|시작 권한이 없음을 안내|
|404|`INVITATION_NOT_FOUND`|취소·만료·시작 등으로 더 이상 유효하지 않은 초대임을 안내하고 해당 팝업·대기 상태 정리|
|404|`GROUP_NOT_FOUND`|그룹이 없음을 안내하고 그룹 목록으로 이동|
|409|`INSUFFICIENT_PARTICIPANTS`|수락 인원이 부족함을 안내하고 대기 유지|
|409|`INVALID_PARTICIPANT`|참여자의 그룹 가입 상태가 변경되었음을 안내|

- 이미 시작 성공 응답이나 `RIDE_STARTED`를 처리했다면, 뒤늦게 받은 실패 응답으로 주행 화면을 되돌리지 않는다.

**대기 중 주의사항**

- 초대는 **생성 후 30분간 유효**하며, 별도의 만료 알림은 없다.
- `/ws/login` 재연결 시 유효한 초대의 최신 참여 현황을 다시 받는다.
- 연결 끊김이 수락·거절 상태를 바꾸지는 않는다. `canStart`는 현재 온라인 인원 기준이 아니다.
- 시작 알림과 취소 알림은 오프라인 보관·재전송되지 않는다.
- 취소된 초대 자체는 다시 전달되지 않지만, 취소 순간 연결이 끊긴 사용자의 기존 팝업·대기 상태가 자동으로 정리되는 것은 보장하지 않는다.

---

#### 라이딩 취소 버튼

- 라이딩 시작 전에는 수락 인원과 관계없이 취소할 수 있다.
- 취소 확인창에서 확인하면 다음 API를 호출한다.

요청)

```http
POST /api/v1/ride-invitations/inv-100/cancel
Authorization: Bearer JWT_TOKEN
```

- `inv-100`에는 현재 `invitationId`를 넣는다.
- 요청 body 없음.
- **시작 또는 취소 요청 중에는 라이딩 시작·라이딩 취소 버튼을 모두 비활성화한다.**

응답 — 성공 시 HTTP 200)
```json
{
  "invitationId": "inv-100",
  "status": "CANCELLED"
}
```

앱 처리)
- 해당 참여 현황 팝업과 대기 상태를 정리하고 메인 화면을 유지한다.
- 기존 `/ws/login` 연결은 유지한다.
- 취소된 초대로 다시 시작할 수 없다. 다시 진행하려면 새 초대를 생성한다.

#### 라이딩 취소 알림
리더도 기존 `/ws/login`으로 다음 메시지를 받는다.

```json
{
  "type": "RIDE_INVITE_CANCELLED",
  "invitationId": "inv-100",
  "groupId": 1
}
```

- 현재 `invitationId`와 일치하면 참여 현황 팝업과 대기 상태를 정리한다.
- REST 응답보다 알림이 먼저 올 수 있다. **REST 성공 응답과 알림을 모두 받아도 한 번만 처리**한다.
- 다른 초대나 진행 중인 주행 화면은 변경하지 않는다.

#### 취소 요청 실패 시

|HTTP 상태|오류 코드|앱 처리|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|다시 로그인하도록 안내|
|403|`FORBIDDEN`|초대 생성자만 취소할 수 있음을 안내|
|404|`INVITATION_NOT_FOUND`|취소·만료·시작 등으로 더 이상 유효하지 않은 초대임을 안내하고 해당 팝업·대기 상태 정리|

- **404를 ‘취소 성공’으로 판단하지 않는다.** 이미 취소·만료·시작된 경우를 구분할 수 없다.
- 시작이 먼저 성공했다면 취소되지 않는다. 이미 시작 성공 응답이나 `RIDE_STARTED`를 처리했다면 주행 화면을 유지한다.
- 네트워크 오류로 응답을 받지 못하면 취소 완료로 단정하지 않는다.


----
# 10. 라이딩 요청 수신 팝업
![[Pasted image 20261107010850.png]]

#### 라이딩 요청 수신 — 팝업 표시

기존 `/ws/login` 연결로 초대 메시지를 자동으로 받는다. 별도의 REST 요청은 없다.

서버 → 초대 대상)
```json
{
  "type": "RIDE_INVITE",
  "invitationId": "inv-100",
  "groupId": 1,
  "groupName": "한강 모임",
  "leaderId": 1,
  "leaderNickname": "홍길동"
}
```

화면 표시)

|응답 필드|UI 표시 및 용도|
|---|---|
|`groupName`|안내 문구와 그룹명|
|`leaderNickname`|리더 이름|
|`invitationId`|수락·거절할 초대 식별|

앱 처리)
- 앱 공통 수신부에서 메시지를 받아 **현재 보고 있는 화면 위에 팝업을 표시**한다.
- 메인 화면으로 이동할 필요는 없다.
- 같은 `invitationId`의 팝업은 중복 표시하지 않는다.

---

#### 수락 버튼
기존 `/ws/login`으로 다음 JSON을 전송한다.
앱 → 서버)
```json
{
  "type": "RIDE_INVITE_RESPONSE",
  "invitationId": "inv-100",
  "accepted": true
}
```
- `invitationId`는 수신한 초대의 값을 사용한다.
- 사용자 ID와 JWT를 메시지 본문에 넣지 않는다. 서버가 인증된 웹소켓 연결로 사용자를 식별하기 때문이다.

앱 처리)
- 전송 중에는 수락·거절 버튼을 모두 비활성화한다.
- 전송 후 초대 팝업을 닫고 리더의 라이딩 시작을 기다리는 상태로 전환한다.
- 별도의 수락 성공 메시지는 없다. **전송 완료를 서버의 수락 확정으로 간주하지 않으며**, 이후 오류 메시지도 처리한다.
- 기존 `/ws/login` 연결을 유지하고 `ERROR`·`RIDE_STARTED`·`RIDE_INVITE_CANCELLED`를 계속 수신한다.
- **수락 버튼을 누른 시점에는 주행 화면으로 이동하지 않는다.**

---

#### 거절 버튼
앱 → 서버, `/ws/login`)
```json
{
  "type": "RIDE_INVITE_RESPONSE",
  "invitationId": "inv-100",
  "accepted": false
}
```

앱 처리)
- 전송 중에는 수락·거절 버튼을 모두 비활성화한다.
- 전송 후 팝업을 닫고 기존 화면을 유지한다.
- 별도의 거절 성공 메시지는 없다. 전송 완료를 서버의 거절 확정으로 간주하지 않으며, 이후 오류 메시지도 처리한다.
- 서버는 리더에게 변경된 참여 현황을 전달한다.

---

#### 수락 후 리더의 라이딩 시작
리더가 라이딩을 시작하면 `/ws/login`으로 다음 메시지를 받는다.
서버 → 확정 참여자)
```json
{
  "type": "RIDE_STARTED",
  "rideSessionId": 10,
  "groupId": 1
}
```

앱 처리)
1. 대기 안내를 정리하고 `rideSessionId`를 보관한 뒤 **후미 주행 화면으로 이동**한다.
2. 주행 화면 정보를 조회하고 라이딩 웹소켓을 연결한다.

주행 화면 정보 조회)
```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

라이딩 웹소켓 연결)
```text
ws://서버주소:포트/ws/ride/10
```

최초 연결 요청 헤더)
```json
Authorization: Bearer JWT_TOKEN
```

- 기존 `/ws/login` 연결도 유지한다.
- 같은 `rideSessionId`에 대한 화면 이동과 연결은 한 번만 처리한다.

---

**응답 처리 실패 시**

서버 → 요청한 연결, `/ws/login`)

```json
{
  "type": "ERROR",
  "code": "INVITATION_NOT_FOUND",
  "message": "존재하지 않는 라이딩 초대입니다."
}
```

|오류 코드|앱 처리|
|---|---|
|`INVITATION_NOT_FOUND`|취소·만료·시작 등으로 더 이상 유효하지 않은 초대임을 안내하고 해당 팝업·대기 상태 정리|
|`INVITATION_RESPONSE_CONFLICT`|이미 확정된 응답은 변경할 수 없음을 안내|
|`FORBIDDEN`|해당 초대에 응답할 권한이 없음을 안내|
|`INVALID_MESSAGE`|요청 JSON의 필드와 자료형 확인|
|`UNSUPPORTED_MESSAGE_TYPE`|메시지 `type` 확인|

- `accepted`는 문자열 `"true"`가 아닌 **boolean `true` 또는 `false`**로 보낸다.
- 현재 `ERROR`에는 `invitationId`가 없으므로, 앱에서 응답 처리 중인 초대를 관리해야 한다.
- 오류가 발생한 초대의 팝업·대기 상태만 정리하며, 다른 초대나 이미 시작된 주행 화면은 변경하지 않는다.
- 연결이 끊겼다면 성공으로 처리하지 말고 연결 상태와 재시도를 안내한다.

---

**연결 및 초대 유효기간**

- 초대는 서버 생성 시점부터 **30분간 유효**하다. 별도의 만료 알림은 없다.
- 재연결하면 아직 응답하지 않은 유효한 초대가 다시 전달된다.
- 이미 수락·거절한 초대는 재전송되지 않으므로, 재연결 중에도 앱의 응답·대기 상태를 유지한다.
- **`RIDE_STARTED`와 `RIDE_INVITE_CANCELLED`는 재접속 시 재전송되지 않는다.** 수락 후에도 연결 유지가 필요하다.
- 취소된 초대 자체는 다시 전달되지 않지만, 취소 순간 연결이 끊긴 사용자의 기존 팝업·수락 후 대기가 자동으로 정리되는 것은 보장하지 않는다.

---

#### 리더의 라이딩 취소

기존 `/ws/login`으로 다음 메시지를 받는다.

서버 → 초대 대상)

```json
{
  "type": "RIDE_INVITE_CANCELLED",
  "invitationId": "inv-100",
  "groupId": 1
}
```

앱 처리)
- 앱 공통 수신부에서 처리한다.
- **해당 `invitationId`의 초대 팝업·대기 목록·수락 후 대기 상태를 모두 정리**한다.
- “리더가 라이딩 요청을 취소했습니다.”를 안내하고 현재 화면을 유지한다.
- 기존 `/ws/login` 연결은 유지한다.
- 이미 정리된 초대의 중복 알림은 무시한다.
- 다른 초대나 진행 중인 라이딩은 변경하지 않는다.

취소 알림은 수락·대기·거절 여부와 관계없이 전체 초대 대상에게 전달된다. 거절 후에도 다른 기기에 남아 있는 팝업을 정리할 수 있도록 처리한다.

---

# 11. 선두 주행화면

![[Pasted image 20261107012240.png]]

#### 화면 진입 — 주행 정보 조회

라이딩 시작 응답 또는 `RIDE_STARTED`에서 받은 `rideSessionId`를 사용한다.

요청)
```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

요청 body 없음.

응답 — 성공 시 HTTP 200)

```json
{
  "groupName": "테스트 그룹1",
  "participantCount": 5
}
```

| 데이터                | UI 표시       |
| ------------------ | ----------- |
| `groupName`        | 상단 그룹 이름    |
| `participantCount` | “현재 5명 주행중” |

---

#### 주행 웹소켓 연결

연결 주소)
```text
ws://서버주소:포트/ws/ride/10
```

최초 연결 요청 헤더)
```http
Authorization: Bearer JWT_TOKEN
```

- `10`에는 현재 `rideSessionId`를 넣는다. 
- 이미 해당 라이딩 연결이 있다면 재사용한다.
- 기존 `/ws/login` 연결도 유지한다.

---

#### 수신호 버튼

버튼을 누르면 **라이딩 웹소켓으로 JSON 텍스트 메시지**를 보낸다. 
- HTTP POST 요청이 아니다.

앱 → 서버, `/ws/ride/10`)

```json
{
  "type": "RIDE_SIGNAL",
  "signal": "LEFT"
}
```

|UI 버튼|`signal` 값|
|---|---|
|노면 주의|`ROAD_HAZARD`|
|커브 주의|`CURVE_CAUTION`|
|좌회전|`LEFT`|
|우회전|`RIGHT`|
|한줄 주행|`SINGLE_FILE`|
|두줄 주행|`DOUBLE_FILE`|
|저속 주행|`SLOW`|
|정지|`STOP`|

- 사용자 ID나 세션 ID를 메시지에 넣지 않는다. 서버가 인증 정보와 연결 경로로 식별한다.
- 서버는 같은 라이딩의 후미 참여자들에게 신호를 전달한다.
- **선두에게 별도의 전송 성공 응답은 보내지 않는다.** 앱에서 전송했다고 모든 후미가 수신한 것으로 표시하면 안 된다.
- 수신호 발송을 확인하는 용도로 버튼을 누르는 동안 버튼의 색상을 노란색등으로 변경후 손을 때면 다시 기존 색상으로 돌아온다.

---

#### 현재 속도 / 이동 거리

서버 API로 조회하지 않고 **Android에서 GPS를 기반으로 계산**한다.

|UI 항목|데이터|
|---|---|
|현재 속도|현재 GPS 기반 속도, km/h|
|이동 거리|이번 라이딩의 누적 이동 거리, km|

- 위치 권한과 GPS 사용 상태를 앱에서 확인한다.
- 종료 후 저장할 **평균 속도도 앱에서 계산**한다. 현재 속도와 평균 속도는 구분한다.
- 이 화면의 수신호 메시지에 GPS나 속도·거리 값을 포함하지 않는다.

---

#### 라이딩 종료 버튼

요청)
```http
POST /api/v1/rides/10/finish
Authorization: Bearer JWT_TOKEN
```
- 요청 body 없음. 해당 라이딩의 선두만 호출할 수 있다.

응답 — 성공 시 HTTP 200)
```json
{
  "rideSessionId": 10,
  "status": "FINISHED",
  "endedAt": "2026-11-07T15:00:00"
}
```

서버는 선두와 후미의 `/ws/ride/10` 연결로 종료 메시지도 보낸다.

```json
{
  "type": "RIDE_FINISHED",
  "rideSessionId": 10
}
```

앱 처리)
1. 요청 중에는 종료 버튼의 중복 클릭을 막는다.
2. **종료 REST 성공 응답 또는 `RIDE_FINISHED` 수신 시** 수신호 전송과 주행 측정을 종료한다.
3. 최종 이동 거리와 평균 속도를 계산하여 개인 기록을 저장한다.
4. REST 응답과 웹소켓 알림을 모두 받아도 종료 처리는 한 번만 수행한다.
    - 서버는 종료 알림 후 해당 라이딩 웹소켓을 닫는다. 앱은 해당 연결의 자동 재연결을 중단한다.
    - `/ws/login`은 유지한다.
    - **단순한 웹소켓 끊김은 라이딩 종료가 아니다.**

---

#### 종료 후 — 개인 기록 저장

요청)
```http
PUT /api/v1/rides/10/record
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

요청 body)
```json
{
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

응답 — 성공 시 HTTP 200)
```json
{
  "rideSessionId": 10,
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

- `distance`: km, `averageSpeed`: km/h.
- 두 값 모두 필수이며 **0 이상, 정수 최대 8자리·소수 최대 2자리**로 보낸다.
- 선두 자신의 기록만 저장된다. 후미는 각자의 앱에서 별도로 저장한다.
- 저장 완료 후  메인 화면으로 이동한다.
- 저장에 실패하면 계산한 값을 앱에 보관하고 재시도를 제공한다. 같은 값을 다시 보내도 기록이 중복 생성되지는 않는다.

---

#### 오류 및 연결 끊김 처리

웹소켓 오류 예시)

```json
{
  "type": "ERROR",
  "code": "FORBIDDEN",
  "message": "선두만 수신호를 전송할 수 있습니다."
}
```

|구분|오류|앱 처리|
|---|---|---|
|REST / 연결 인증|HTTP 401|다시 로그인하도록 안내|
|REST / 웹소켓|`FORBIDDEN`|참여자 또는 선두 권한이 없음을 안내|
|REST / 웹소켓|`RIDE_SESSION_NOT_FOUND`|존재하지 않는 라이딩 안내|
|종료 REST / 웹소켓|`RIDE_NOT_ACTIVE`|진행 중인 라이딩이 아님을 안내하고 신호 전송 중단|
|수신호 웹소켓|`INVALID_MESSAGE`, `UNSUPPORTED_MESSAGE_TYPE`|JSON 형식과 `type`·`signal` 값 확인|
|기록 저장 REST|`INVALID_REQUEST`|기록 값의 범위와 소수 자릿수 확인|
|기록 저장 REST|`RIDE_NOT_FINISHED`|서버에서 종료가 완료됐는지 확인|

- 일시적인 연결 끊김은 재연결하되, **끊긴 동안의 수신호를 나중에 몰아서 보내지 않는다.**
- 서버의 Ping에 Pong으로 응답해야 하며, JWT 만료 시에는 다시 로그인이 필요하다.
- 종료 응답을 네트워크 문제로 받지 못했다면 종료 REST를 재요청할 수 있다. 이미 종료된 세션이면 기존 종료 결과를 반환한다.

----
# 12. 후미그룹 주행화면
![[Pasted image 20261107013251.png]]

#### 화면 진입 — 주행 정보 조회

`/ws/login`에서 받은 `RIDE_STARTED`의 `rideSessionId`를 사용한다.

요청)

```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

응답 — 성공 시 HTTP 200)

```json
{
  "groupName": "테스트 그룹1",
  "participantCount": 5
}
```

| 데이터                | UI 표시    |
| ------------------ | -------- |
| `groupName`        | 상단 그룹 이름 |
| `participantCount` | 참여 인원    |

---

#### 주행 웹소켓 연결

연결 주소)
```text
ws://서버주소:포트/ws/ride/10
```

최초 연결 요청 헤더)
```http
Authorization: Bearer JWT_TOKEN
```

- `10`에는 현재 `rideSessionId`를 넣는다. 
- 이미 연결되어 있다면 재사용한다.
- 기존 `/ws/login` 연결도 유지한다.

---

#### 수신호 수신 — 알림창 표시

서버 → 후미, `/ws/ride/10`)
```
{
  "type": "RIDE_SIGNAL",
  "senderId": 1,
  "signal": "ROAD_HAZARD"
}
```

|`signal`|표시 문구|
|---|---|
|`ROAD_HAZARD`|노면 주의|
|`CURVE_CAUTION`|커브 주의|
|`LEFT`|좌회전|
|`RIGHT`|우회전|
|`SINGLE_FILE`|한 줄 주행|
|`DOUBLE_FILE`|두 줄 주행|
|`SLOW`|저속 주행|
|`STOP`|정지|
|`LEADER_CHANGE`|선두 교체 요청|

앱 처리)
1. 상단 수신호 알림창의 아이콘과 문구를 받은 신호로 변경한다.
2. **신호를 받을 때마다 알림창 배경이 회색 ↔ 노란색으로 번갈아 바뀌며 깜빡이도록 한다.**
3. 수신 시각을 표시하고 최근 신호 목록에 추가한다.
4. 음성 안내·진동이 켜져 있으면 해당 알림을 실행한다.

깜빡임 동작
- 배경을 **0.5초 간격으로 3회 왕복**한 뒤 노란색으로 유지한다.
- 깜빡이는 중 새 신호가 오면 최신 신호로 교체하고 효과를 처음부터 다시 시작한다.
- 같은 신호를 다시 받아도 새로운 수신으로 처리한다.

---

**최근 신호 목록**
- 수신한 신호를 앱 메모리에 보관하고 최신순으로 표시한다.
- 서버 메시지에는 시각이 없으므로 **앱에서 수신한 시각**을 기록한다.
- 신호 기록 조회 API는 없으며, 연결이 끊긴 동안 놓친 신호는 재전송되지 않는다.
- 새 라이딩 시작 시 이전 라이딩의 목록을 초기화한다.

---

**음성 안내 / 진동 버튼**

|버튼|앱 처리|
|---|---|
|음성 안내 ON/OFF|신호 수신 시 해당 문구의 음성 안내 여부 변경|
|진동 ON/OFF|신호 수신 시 진동 여부 변경|

- 설정은 앱에서 관리하며 서버 요청은 없다.
- 음성·진동을 꺼도 **알림창 표시와 깜빡임은 유지**한다.
- 음성은 1회 시그널 이름을 2회 나레이션으로 말한다.
- 진동은 2초간격 진동을 1회 발생시킨다.

---

**현재 속도 / 이동 거리**

- Android에서 GPS를 기반으로 계산한다.
- 현재 속도는 km/h, 누적 이동 거리는 km로 표시한다.
- 종료 시 저장할 평균 속도도 앱에서 별도로 계산한다.
- 후미는 수신호를 전송하지 않는다.

---

#### 리더가 라이딩 종료 — 메인 화면 이동

리더가 종료하면 서버가 `/ws/ride/10`으로 다음 메시지를 보낸다.
```json
{
  "type": "RIDE_FINISHED",
  "rideSessionId": 10
}
```

앱 처리)
1. 현재 라이딩의 종료 알림인지 확인한다.
2. 주행 측정, 깜빡임, 음성·진동을 종료한다.
3. 최종 이동 거리와 평균 속도를 앱에 보관하고 개인 기록 저장을 요청한다.
4. **주행 화면을 닫고 메인 화면으로 이동한다.**
5. 뒤로 가기로 종료된 주행 화면에 돌아가지 않도록 화면 기록을 정리한다.
    - 기록 저장 실패가 메인 화면 이동을 막지 않도록 하고, 저장할 값은 재시도를 위해 유지한다.
    - 종료 처리는 같은 `rideSessionId`에 대해 한 번만 수행한다.
    - 서버는 종료 알림 후 라이딩 웹소켓을 닫는다. 해당 연결의 자동 재연결을 중단한다.
    - 기존 `/ws/login` 연결은 유지한다.

---

#### 종료 후 — 개인 기록 저장

요청)
```http
PUT /api/v1/rides/10/record
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

요청 body)
```json
{
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

응답 — 성공 시 HTTP 200)

```json
{
  "rideSessionId": 10,
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

- JWT 사용자 자신의 기록만 저장한다. **리더가 후미의 기록까지 저장해 주지는 않는다.**
- 두 값 모두 필수이며 0 이상, 정수 최대 8자리·소수 최대 2자리로 보낸다.
- 저장에 실패하면 메인 화면에서 재시도를 안내한다. 재전송 시 기존 기록을 갱신한다.

---

**오류 및 연결 끊김 처리**

|상황|앱 처리|
|---|---|
|일시적인 웹소켓 끊김|“신호 연결 끊김” 표시 후 재연결 시도|
|인증 실패·JWT 만료|다시 로그인하도록 안내|
|`FORBIDDEN`|해당 라이딩 참여 권한이 없음을 안내|
|`RIDE_SESSION_NOT_FOUND`|존재하지 않는 라이딩 안내|
|`RIDE_NOT_ACTIVE`|진행 중인 세션이 아님을 안내하고 재연결 중단|
|기록 저장 `INVALID_REQUEST`|기록 값의 범위·소수 자릿수 확인|
|기록 저장 `RIDE_NOT_FINISHED`|서버에서 종료가 확정되지 않았음을 확인|

