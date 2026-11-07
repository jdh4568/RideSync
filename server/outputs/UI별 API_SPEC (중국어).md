# 1. 注册页面
![[Pasted image 20261106225704.png]]

### 各按钮的接口说明
#### 检查账号是否可用按钮
请求)
```http
GET /api/v1/auth/check-login-id?loginId="입력값"
```

响应)
```json
{
  "available": true
}
```
- `true`: 显示“该账号可以使用。”
- `false`: 显示“该账号已被使用。”

#### 注册按钮
请求)
```http
POST /api/v1/auth/signup
Content-Type: application/json
```
请求 body)
```json
{
  "loginId": "donghyun",
  "password": "1234",
  "passwordConfirm": "1234",
  "nickname": "동현"
}
```

响应(成功时)
```json
{
  "userId": 1,
  "nickname": "동현"
}
```
响应(失败时)
```json
{
  "code": "PASSWORD_MISMATCH",
  "message": "비밀번호 확인이 일치하지 않습니다."
}
```

| 错误码                | 应用提示示例                        |
| -------------------- | ------------------------------ |
| `PASSWORD_MISMATCH`  | 两次输入的密码不一致。               |
| `INVALID_PASSWORD`   | 请检查密码长度。           |
| `DUPLICATE_LOGIN_ID` | 该账号已被使用。               |
| `DUPLICATE_NICKNAME` | 该昵称已被使用。               |
| `DUPLICATE_USER`     | 账号或昵称已被使用，请重新确认。 |

#### 输入要求
所有字段均为必填，且不能仅输入空白字符。账号和昵称最多为 50 个字符，密码按 UTF-8 编码最多为 72 字节，且必须与确认密码一致。

注册完成后跳转至登录页面。

----

# 2. 登录页面

![[Pasted image 20261106231128.png]]




#### 登录按钮

请求)
```http
POST /api/v1/auth/login
Content-Type: application/json
```

请求 body)
```json
{
  "loginId": "donghyun",
  "password": "1234"
}
```

响应 — 成功时 HTTP 200)
```json
{
  "accessToken": "JWT_TOKEN",
  "userId": 1,
  "nickname": "동현"
}
```

应用处理)
1. 在应用中保存 `accessToken`, `userId`, `nickname`。
2. 使用收到的令牌连接 `/ws/login` WebSocket。
3. **WebSocket 连接建立后跳转至主页面。**

响应 — 失败时 HTTP 401)

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 올바르지 않습니다."
}
```

- 保持在登录页面并显示错误提示。
- 请求期间防止重复点击登录按钮。

#### 登录后建立 WebSocket 连接

连接地址)

```text
ws://서버주소:포트/ws/login
```


连接请求头)
```http
Authorization: Bearer JWT_TOKEN
```

- 将登录响应中的 `accessToken` 值填入 `JWT_TOKEN`。
- 在**首次 WebSocket 连接请求的请求头**中设置。不要通过 URL 或连接建立后的 JSON 消息发送令牌。
- 使用标准 WebSocket，不使用 STOMP/SockJS。
- 通过应用的 WebSocket **连接成功回调**确认连接成功。无需等待单独的登录成功 JSON 消息。

连接建立后的处理)
- 服务器将保持 `/ws/login` 连接的用户视为在线。
- **跳转至主页面后也要保持连接。**
- 后续接收服务器发送的骑行邀请、参与情况和骑行开始消息。
- 在应用中统一管理连接，避免每次切换页面时重新连接。

连接维护及错误处理)

|情况|应用处理|
|---|---|
|收到服务器的 Ping|回复 WebSocket Pong。确认所用库是否支持自动回复。|
|因网络问题连接失败或断开|提示连接状态，并使用有效令牌间隔重连。|
|连接请求被 HTTP 401 拒绝|清除已保存的令牌，并提示重新登录。|
|退出登录|删除已保存的令牌和用户信息，关闭 WebSocket，并停止自动重连。|

- 服务器**每隔 30 秒发送一次 Ping**，**距最后一次收到 Pong 超过 90 秒时关闭连接**。Ping/Pong 是 WebSocket 控制帧，并非 JSON 消息。
- 令牌的默认有效期为 **1 小时**，连接期间令牌过期时，服务器也会关闭连接。目前没有令牌刷新 API，因此需要重新登录。
- 如果登录后立即建立 WebSocket 连接失败，建议**暂不跳转至主页面，而是提示重试连接**。

后续 REST API 的身份验证)
登录后，调用需要身份验证的 API 时，通过以下请求头发送已保存的令牌。

```
Authorization: Bearer JWT_TOKEN
```
- 即使已连接 WebSocket，每次 REST API 请求仍需携带身份验证请求头。

注册按钮)
- 跳转至注册页面。
- 点击按钮本身无需向服务器发送请求。

---
# 3. 主页面 - 计划由“주등비”负责开发。
![[Pasted image 20261107014530.png|400]]

目前尚未实现此功能。
此部分由 주등비 负责，待其完成下方请求 API 后，对接该 API 即可。
示例 API)
```http
GET /api/v1/users/me/home
Authorization: Bearer JWT_TOKEN
```

在当前开发阶段 


---
# 4. 群组列表

![[Pasted image 20261106231857.png]]

#### 进入页面 — 查询群组列表

请求)

```http
GET /api/v1/groups
Authorization: Bearer JWT_TOKEN
```

无请求 body。

响应 — 成功时 HTTP 200)

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

页面显示)

|响应字段|UI 显示|
|---|---|
|`groupName`|群组名称|
|`description`|群组说明|
|`memberCount` / `maxMembers`|人数显示: `8 / 10`|
|`membershipStatus`|决定加入按钮的状态|
|`groupId`|申请加入该群组时使用|

按钮状态)

|`membershipStatus`|按钮显示|是否可点击|
|---|---|---|
|`null`|申请加入|可以|
|`PENDING`|申请中|不可以|
|`APPROVED`|已加入|不可以|
|`REJECTED`|申请加入|可以 — 重新申请|

- `memberCount` 为**已获批准的群组成员数**，不包含等待审批的申请者。
- 若响应为空数组 `[]`，则显示“暂无已创建的群组。”

---

#### 申请加入按钮

请求)

```http
POST /api/v1/groups/2/join
Authorization: Bearer JWT_TOKEN
```

- 将所选群组的 `groupId` 填入 `2` 的位置。
- 无请求 body。服务器通过 JWT 识别申请者，因此无需发送用户 ID。

响应 — 成功时 HTTP 200)

```json
{
  "groupId": 2,
  "status": "PENDING"
}
```

应用处理)
- 保持在当前页面。
- 将该群组的按钮**改为‘申请中’并禁用**。
- 此时尚未获准加入，因此不增加人数。
- 请求期间防止重复点击该按钮。

响应 — 失败示例)

```json
{
  "code": "GROUP_JOIN_CONFLICT",
  "message": "이미 가입했거나 처리 중인 가입 신청이 있습니다."
}
```

|HTTP 状态|错误码|应用处理|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示需要登录后跳转至登录页面|
|404|`GROUP_NOT_FOUND`|提示群组不存在后重新查询列表|
|409|`GROUP_JOIN_CONFLICT`|提示已加入或正在申请中，并重新查询列表|

#### 列表刷新及 WebSocket
- 保持登录时建立的 `/ws/login` 连接，不为此页面新建 WebSocket 连接。
- 重新进入页面或刷新时，再次调用 `GET /api/v1/groups`，更新按钮状态和人数。

#### 创建群组按钮 / 返回按钮
- **创建群组:** 打开创建群组弹窗。点击按钮本身不发送服务器请求。创建完成后重新查询列表。
- **返回:** 返回上一页面。保持 `/ws/login` 连接。

----
# 5. 创建群组
![[Pasted image 20261106232256.png]]

#### 输入项

|UI 输入框|请求字段|输入要求|
|---|---|---|
|群组名称|`groupName`|必填，不能仅输入空白字符，最多 100 个字符|
|群组说明|`description`|选填，最多 500 个字符|
|人数上限|`maxMembers`|必填，2~50 之间的整数|
- 人数上限包含群主本人。

#### 创建群组按钮
请求)
```http
POST /api/v1/groups
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

请求 body)
```json
{
  "groupName": "한강모임",
  "description": "한강 중심 라이딩",
  "maxMembers": 10
}
```
- `maxMembers` 以不带引号的数字发送。
- 如果未填写说明，`description` 可以发送空字符串 `""`。

响应 — 成功时 HTTP 201)
```json
{
  "groupId": 1,
  "groupName": "한강모임"
}
```

应用处理 - 创建群组时)
1. 关闭创建群组弹窗。
2. 再次调用 `GET /api/v1/groups`，刷新群组列表。
3. 创建群组后，本人会**自动以群主身份加入**，因此显示为‘已加入’。初始人数为 `1 / 人数上限`。

响应 — 失败时)

```json
{
  "code": "INVALID_REQUEST",
  "message": "최대 인원은 2명 이상이어야 합니다."
}
```

|HTTP 状态|错误码|应用处理|
|---|---|---|
|400|`INVALID_REQUEST`|保留弹窗和输入内容，并显示 `message`|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示需要登录后跳转至登录页面|
|500|`INTERNAL_SERVER_ERROR`|保留输入内容，并提示服务器错误|

#### 取消按钮
- 无需发送服务器请求，关闭弹窗并返回群组列表。

#### WebSocket
- 此弹窗不发送 WebSocket 消息。
- 保持现有的 `/ws/login` 连接。

----
# 6. 通知列表弹窗
![[Pasted image 20261106232911.png|400]]

此弹窗是**供群主查看并批准或拒绝自己所拥有群组的加入申请的页面**。不包含已处理的历史记录。

![[Pasted image 20261106233149.png]]主页面通知按钮 — 查询加入申请列表
- 
请求)
```http
GET /api/v1/groups/join-requests
Authorization: Bearer JWT_TOKEN
```

无请求 body。

响应 — 成功时 HTTP 200)

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

应用处理)
- 每一项显示为**“{nickname} 申请加入 {groupName}。”**
- 如果拥有多个群组，将一并返回所有群组的待审批申请。
- 若为空数组 `[]`，则显示“暂无待审批的加入申请。”
- 未拥有任何群组的用户会收到 **HTTP 403(`FORBIDDEN`)**。此时提示“只有群主可以查看加入申请。”

---

#### 接受按钮
请求)
```http
POST /api/v1/groups/1/requests/5/approve
Authorization: Bearer JWT_TOKEN
```
- `1`: 该条目的 `groupId`
- `5`: 申请者的 `userId`
- 无请求 body。

响应 — 成功时 HTTP 200)
```json
{
  "groupId": 1,
  "userId": 5,
  "status": "APPROVED"
}
```

应用处理)
- 保持弹窗打开，并从列表中移除已处理的申请。
- 申请者成为正式群组成员。

---

#### 拒绝按钮
请求)
```http
POST /api/v1/groups/1/requests/5/reject
Authorization: Bearer JWT_TOKEN
```
- 无请求 body。

响应 — 成功时 HTTP 200)

```json
{
  "groupId": 1,
  "userId": 5,
  "status": "REJECTED"
}
```

应用处理)
- 保持弹窗打开，并从列表中移除已处理的申请。
- 群组人数不增加。
- 被拒绝的用户之后可以重新申请加入。

---

响应 — 失败时)
```json
{
  "code": "GROUP_FULL",
  "message": "그룹 최대 인원에 도달했습니다."
}
```

|HTTP 状态|错误码|应用处理|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示需要登录后跳转至登录页面|
|403|`FORBIDDEN`|提示只有该群组的群主可以处理|
|404|`GROUP_NOT_FOUND`, `JOIN_REQUEST_NOT_FOUND`|提示目标不存在，并重新查询申请列表|
|409|`JOIN_REQUEST_CONFLICT`|提示申请已处理，并重新查询列表|
|409|`GROUP_FULL`|提示人数已达上限。申请保持待审批状态|

---

**通知角标与 WebSocket**
- 保持现有的 `/ws/login` 连接。


---
# 7. 骑行准备(1) - 选择群组
![[Pasted image 20261106233759.png]]

在此页面，**从自己担任群主的群组中选择一个进行骑行**。创建邀请的群主将成为领骑者。

#### 进入页面 — 查询群组列表
请求)
```http
GET /api/v1/groups/owned
Authorization: Bearer JWT_TOKEN
```
- 无请求 body。

响应 — 成功时 HTTP 200)
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

页面显示)

|响应字段|UI 显示|
|---|---|
|`groupName`|群组名称|
|`description`|群组说明|
|`memberCount` / `maxMembers`|人数显示: `13 / 20`|
|`groupId`|用于标识所选群组及下一页面的数据查询|

- 人数为**包含群主在内的已获批准的群组成员数**，并非当前在线人数或骑行参与人数。
- 若为空数组 `[]`，则显示“没有可发起骑行的群组，请先创建群组。”，并禁用完成选择按钮。

---

#### 选择群组
- 每次只能选择一个群组。
- 在应用中保存所选的 `groupId`。
- 仅更新选中状态，不发送服务器请求。
- 未选择群组时，禁用‘完成选择’按钮。

---

#### 完成选择按钮

应用处理)
1. 将所选的 `groupId` 传递给下一页面。
2. **跳转至成员选择页面**。
3. 在下一页面查询该群组的成员列表。

``` http
GET /api/v1/groups/1/members
Authorization: Bearer JWT_TOKEN
```
- 将所选的 `groupId` 填入 `1` 的位置。
- 此阶段仅选择群组。**发送骑行邀请和正式开始骑行在后续阶段处理。**

---

#### 列表查询失败时

|情况|应用处理|
|---|---|
|HTTP 401 — `UNAUTHORIZED`, `INVALID_TOKEN`|提示需要登录后跳转至登录页面|
|服务器错误或网络错误|提示查询失败并提供重试。需与列表为空的提示区分|

#### **返回 / WebSocket**
- 点击返回时回到上一页面，无需额外发送服务器请求。
- 保持现有的 `/ws/login` 连接。
- 此页面不发送 WebSocket 消息。后续创建骑行邀请时需要 `/ws/login` 连接。

----
# 8. 骑行准备(2) - 选择成员
![[Pasted image 20261106234429.png]]

#### 进入页面 — 查询群组信息及成员

请求)
```http
GET /api/v1/groups/1/members
Authorization: Bearer JWT_TOKEN
```
- 将上一页面所选的 `groupId` 填入 `1` 的位置。
- 无请求 body。

响应 — 成功时 HTTP 200)
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

页面显示)

|响应字段|UI 处理|
|---|---|
|`groupName`|顶部的群组名称|
|`description`|顶部的群组说明。若为 `null` 或空字符串则不显示说明|
|`memberCount`|顶部的群组人数: `4人`|
|`members`|成员选择列表|
|`members[].nickname`|成员名称|
|`members[].online`|`true`: 在线 / `false`: 离线|

- 响应为**对象**，成员列表从其中的 `members` 数组读取。
- 仅包含已获批准的群组成员，`memberCount` 为**包含本人在内的 `members` 数组长度**。
- 将当前登录用户本人的 `userId` 从选择列表中排除。顶部人数保持原样显示。
- 在上述示例中，若当前登录用户为 `userId: 1`，选择列表仅显示 이순신、장발장、김철수。
- 在线状态为查询时的状态。刷新时再次调用同一 API。

---

#### 选择成员
- 仅允许选择在线成员。
- 可以选择多人，并在应用中保存所选的 `userId`。
- 除本人外，必须**至少选择 1 人**，才启用‘完成选择’按钮。
- 不需要每次勾选时都发送服务器请求。

---

#### 完成选择按钮 — 发送骑行邀请消息

请求)
```http
POST /api/v1/ride-invitations
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

请求 body)
```json
{
  "groupId": 1,
  "inviteeIds": [3, 4]
}
```
- 将所选成员的 `userId` 放入 `inviteeIds`。排除本人 ID 和重复 ID。
- 只有该群组的群主可以创建邀请。
- **本人和所有受邀者都必须连接到 `/ws/login`。**

响应 — 成功时 HTTP 201)
```
{
  "invitationId": "inv-100",
  "groupId": 1,
  "status": "PENDING",
  "invitedCount": 2
}
```

应用处理)
1. 保存 `invitationId`。
2. **跳转至骑行参与情况页面**。
3. 显示通过 WebSocket 收到的参与情况。
    - `invitedCount` 为不包含本人的受邀人数。
    - 此时骑行尚未开始，也不会生成 `rideSessionId`。
    - 请求期间防止重复点击‘完成选择’按钮。

---

#### 创建邀请后接收 WebSocket 消息

保持现有的 `/ws/login` 连接。

|接收者|消息|应用处理|
|---|---|---|
|受邀者|`RIDE_INVITE`|显示骑行邀请弹窗|
|发出邀请的群主|`RIDE_INVITE_STATUS`|显示并更新参与情况|

群主收到的初始参与情况示例)
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
- 群主本人自动处于 `ACCEPTED` 状态。
- 有人接受或拒绝时，服务器会重新发送完整的参与情况，因此应使用收到的列表刷新页面。
- **WebSocket 消息可能先于 REST 成功响应到达。** 应在页面跳转前就开始接收消息，并按 `invitationId` 分别保存。
- 没有单独用于查询参与情况的 REST API。

---

#### 请求失败时

失败响应示例 — 创建邀请时 HTTP 409)
```json
{
  "code": "USER_OFFLINE",
  "message": "..."
}
```

`message` 使用服务器返回的提示内容。

|HTTP 状态|错误码|应用处理|
|---|---|---|
|400|`INVALID_REQUEST`|检查受邀人数、重复 ID 以及是否包含本人|
|400|`INVALID_INVITEE`|提示存在无法邀请的成员，并重新查询列表|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示需要登录后跳转至登录页面|
|403|`FORBIDDEN`|提示没有查询权限或创建邀请的权限|
|404|`GROUP_NOT_FOUND`|提示群组不存在，并跳转至群组选择页面|
|409|`USER_OFFLINE`|检查本人的 WebSocket 连接，并重新查询成员列表|

- 如果成员在查询后变为离线，**整个创建邀请请求将被拒绝**。
- 因网络错误未收到创建结果时，不要自动重复请求。再次调用可能创建另一条邀请。

#### 返回
- 创建邀请前，可直接返回群组选择页面，无需发送服务器请求。
- 保持现有的 `/ws/login` 连接。

----
# 9. 骑行参与情况弹窗
![[Pasted image 20261107004647.png]]

#### 进入页面及接收参与情况
- 创建邀请成功后，自动跳转至主页面并显示弹窗。
- 使用上一步收到的 `invitationId`。
- 无需单独调用查询 API，通过现有的 `/ws/login` 接收参与情况。

服务器 → 领队)
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

- 仅处理与当前弹窗的 `invitationId` 一致的消息。
- 有人接受或拒绝时会重新收到完整的参与情况，因此应**用新列表替换更新，不要累加到原有值上**。
- 如果进入页面前已收到参与情况，则使用该数据进行初始显示。

#### 显示邀请、接受、等待及拒绝人数

|UI 项目|计算方式|
|---|---|
|邀请|`participants` 中除本人以外的人数|
|接受|除本人以外处于 `ACCEPTED` 状态的人数|
|等待|处于 `PENDING` 状态的人数|
|拒绝|处于 `REJECTED` 状态的人数|

服务器返回的 `participants` 和 `acceptedCount` **包含自动接受邀请的领队本人**。

---

#### 开始骑行按钮

- 服务器返回 `canStart: true` 时启用。
- 返回 `canStart: false` 时禁用。
- 包含领队在内，接受邀请的人数达到 2 人即可开始。**无需等待所有人回复。**
- 尚未收到参与情况时，禁用按钮。

请求)
```http
POST /api/v1/ride-invitations/inv-100/start
Authorization: Bearer JWT_TOKEN
```

- 将当前的 `invitationId` 填入 `inv-100` 的位置。
- 无请求 body。
- **发送开始或取消请求期间，同时禁用开始骑行和取消骑行按钮。**

响应 — 成功时 HTTP 200)
```json
{
  "rideSessionId": 10,
  "groupId": 1,
  "status": "RIDING",
  "startedAt": "2026-11-07T14:00:00"
}
```

应用处理)
1. 保存 `rideSessionId`，并关闭等待弹窗。
2. 跳转至领队骑行页面。
3. 查询骑行页面信息，并建立骑行 WebSocket 连接。

```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

```text
ws://서버주소:포트/ws/ride/10
```

首次 WebSocket 连接请求头)

```http
Authorization: Bearer JWT_TOKEN
```

- 同时保持现有的 `/ws/login` 连接。
- **仅在开始时处于 `ACCEPTED` 状态的用户会被确认为参与者**。等待中和已拒绝的用户不包含在内。

---

#### 骑行开始通知

服务器还会通过 `/ws/login` 向包括领队在内的已确认参与者发送以下消息。

```json
{
  "type": "RIDE_STARTED",
  "rideSessionId": 10,
  "groupId": 1
}
```

- 领队可能同时收到 REST 成功响应和此通知。
- **对于同一 `rideSessionId`，页面跳转和 WebSocket 连接只处理一次**。
- 已接受邀请的成员收到此通知后跳转至骑行页面。

---

#### 开始请求失败时

```json
{
  "code": "INSUFFICIENT_PARTICIPANTS",
  "message": "..."
}
```

`message` 使用服务器返回的提示内容。

|HTTP 状态|错误码|应用处理|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示重新登录|
|403|`FORBIDDEN`|提示没有开始骑行的权限|
|404|`INVITATION_NOT_FOUND`|提示邀请因取消、过期或已开始等原因不再有效，并清理对应弹窗及等待状态|
|404|`GROUP_NOT_FOUND`|提示群组不存在，并跳转至群组列表|
|409|`INSUFFICIENT_PARTICIPANTS`|提示接受邀请的人数不足，并保持等待状态|
|409|`INVALID_PARTICIPANT`|提示参与者的群组加入状态已发生变化|

- 如果已处理开始成功响应或 `RIDE_STARTED`，不要因随后收到的失败响应而退出骑行页面。

**等待期间注意事项**

- 邀请**自创建起 30 分钟内有效**，没有单独的过期通知。
- 重新连接 `/ws/login` 时，会再次收到有效邀请的最新参与情况。
- 连接断开不会改变接受或拒绝状态。`canStart` 并非以当前在线人数为依据。
- 开始通知和取消通知不会离线保存，也不会补发。
- 已取消的邀请本身不会再次发送，但无法保证在取消瞬间断线的用户，其原有弹窗和等待状态会被自动清理。

---

#### 取消骑行按钮

- 骑行开始前，无论已有多少人接受邀请，均可取消。
- 在取消确认框中确认后，调用以下 API。

请求)

```http
POST /api/v1/ride-invitations/inv-100/cancel
Authorization: Bearer JWT_TOKEN
```

- 将当前的 `invitationId` 填入 `inv-100` 的位置。
- 无请求 body。
- **发送开始或取消请求期间，同时禁用开始骑行和取消骑行按钮。**

响应 — 成功时 HTTP 200)
```json
{
  "invitationId": "inv-100",
  "status": "CANCELLED"
}
```

应用处理)
- 清理对应的参与情况弹窗和等待状态，并保持在主页面。
- 保持现有的 `/ws/login` 连接。
- 已取消的邀请无法再次用于开始骑行。如需继续，请创建新邀请。

#### 骑行取消通知
领队也会通过现有的 `/ws/login` 收到以下消息。

```json
{
  "type": "RIDE_INVITE_CANCELLED",
  "invitationId": "inv-100",
  "groupId": 1
}
```

- 若与当前的 `invitationId` 一致，则清理参与情况弹窗和等待状态。
- 通知可能先于 REST 响应到达。**即使同时收到 REST 成功响应和通知，也只处理一次**。
- 不更改其他邀请或正在进行的骑行页面。

#### 取消请求失败时

|HTTP 状态|错误码|应用处理|
|---|---|---|
|401|`UNAUTHORIZED`, `INVALID_TOKEN`|提示重新登录|
|403|`FORBIDDEN`|提示只有邀请创建者可以取消|
|404|`INVITATION_NOT_FOUND`|提示邀请因取消、过期或已开始等原因不再有效，并清理对应弹窗及等待状态|

- **不要将 404 视为‘取消成功’。** 无法据此区分已取消、已过期或已开始的情况。
- 如果开始请求先成功，则不会取消。如果已处理开始成功响应或 `RIDE_STARTED`，应保持在骑行页面。
- 因网络错误未收到响应时，不要断定已完成取消。


----
# 10. 骑行邀请接收弹窗
![[Pasted image 20261107010850.png]]

#### 收到骑行邀请 — 显示弹窗

通过现有的 `/ws/login` 连接自动接收邀请消息，无需额外的 REST 请求。

服务器 → 受邀者)
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

页面显示)

|响应字段|UI 显示及用途|
|---|---|
|`groupName`|提示文案及群组名称|
|`leaderNickname`|领队名称|
|`invitationId`|标识要接受或拒绝的邀请|

应用处理)
- 在应用的公共消息接收模块中接收消息，并**在当前页面上方显示弹窗**。
- 无需跳转至主页面。
- 不重复显示同一 `invitationId` 的弹窗。

---

#### 接受按钮
通过现有的 `/ws/login` 发送以下 JSON。
应用 → 服务器)
```json
{
  "type": "RIDE_INVITE_RESPONSE",
  "invitationId": "inv-100",
  "accepted": true
}
```
- `invitationId` 使用收到的邀请中的值。
- 不要在消息正文中加入用户 ID 和 JWT，因为服务器会通过已认证的 WebSocket 连接识别用户。

应用处理)
- 发送期间同时禁用接受和拒绝按钮。
- 发送后关闭邀请弹窗，进入等待领队开始骑行的状态。
- 没有单独的接受成功消息。**不能将发送完成视为服务器已确认接受邀请**，还需处理后续错误消息。
- 保持现有的 `/ws/login` 连接，继续接收 `ERROR`、`RIDE_STARTED` 和 `RIDE_INVITE_CANCELLED`。
- **点击接受按钮时，不跳转至骑行页面。**

---

#### 拒绝按钮
应用 → 服务器, `/ws/login`)
```json
{
  "type": "RIDE_INVITE_RESPONSE",
  "invitationId": "inv-100",
  "accepted": false
}
```

应用处理)
- 发送期间同时禁用接受和拒绝按钮。
- 发送后关闭弹窗，并保持在原页面。
- 没有单独的拒绝成功消息。不能将发送完成视为服务器已确认拒绝邀请，还需处理后续错误消息。
- 服务器会将更新后的参与情况发送给领队。

---

#### 接受邀请后，领队开始骑行
领队开始骑行后，会通过 `/ws/login` 收到以下消息。
服务器 → 已确认的参与者)
```json
{
  "type": "RIDE_STARTED",
  "rideSessionId": 10,
  "groupId": 1
}
```

应用处理)
1. 清理等待提示，保存 `rideSessionId`，然后**跳转至跟骑者骑行页面**。
2. 查询骑行页面信息，并建立骑行 WebSocket 连接。

查询骑行页面信息)
```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

建立骑行 WebSocket 连接)
```text
ws://서버주소:포트/ws/ride/10
```

首次连接请求头)
```json
Authorization: Bearer JWT_TOKEN
```

- 同时保持现有的 `/ws/login` 连接。
- 对于同一 `rideSessionId`，页面跳转和连接只处理一次。

---

**回复处理失败时**

服务器 → 发起请求的连接, `/ws/login`)

```json
{
  "type": "ERROR",
  "code": "INVITATION_NOT_FOUND",
  "message": "존재하지 않는 라이딩 초대입니다."
}
```

|错误码|应用处理|
|---|---|
|`INVITATION_NOT_FOUND`|提示邀请因取消、过期或已开始等原因不再有效，并清理对应弹窗及等待状态|
|`INVITATION_RESPONSE_CONFLICT`|提示已确认的回复无法更改|
|`FORBIDDEN`|提示没有回复该邀请的权限|
|`INVALID_MESSAGE`|检查请求 JSON 的字段和数据类型|
|`UNSUPPORTED_MESSAGE_TYPE`|检查消息的 `type`|

- `accepted` 应发送为 **boolean `true` 或 `false`**，而非字符串 `"true"`。
- 当前 `ERROR` 不包含 `invitationId`，因此应用需管理正在回复的邀请。
- 仅清理发生错误的邀请对应的弹窗和等待状态，不更改其他邀请或已开始的骑行页面。
- 如果连接断开，不要按成功处理，应提示连接状态并引导重试。

---

**连接及邀请有效期**

- 邀请自服务器创建时起 **30 分钟内有效**，没有单独的过期通知。
- 重新连接后，尚未回复的有效邀请会再次发送。
- 已接受或拒绝的邀请不会再次发送，因此重连期间也应保留应用中的回复和等待状态。
- **重新连接时不会补发 `RIDE_STARTED` 和 `RIDE_INVITE_CANCELLED`。** 接受邀请后也需要保持连接。
- 已取消的邀请本身不会再次发送，但无法保证在取消瞬间断线的用户，其原有弹窗和接受后的等待状态会被自动清理。

---

#### 领队取消骑行

通过现有的 `/ws/login` 接收以下消息。

服务器 → 受邀者)

```json
{
  "type": "RIDE_INVITE_CANCELLED",
  "invitationId": "inv-100",
  "groupId": 1
}
```

应用处理)
- 在应用的公共消息接收模块中处理。
- **清理该 `invitationId` 对应的邀请弹窗、等待列表和接受后的等待状态**。
- 提示“领队已取消骑行邀请。”，并保持在当前页面。
- 保持现有的 `/ws/login` 连接。
- 忽略已清理邀请的重复通知。
- 不更改其他邀请或正在进行的骑行。

无论受邀者已接受、正在等待还是已拒绝，取消通知都会发送给所有受邀者。即使已拒绝，也应处理此通知，以清理其他设备上残留的弹窗。

---

# 11. 领骑者骑行页面

![[Pasted image 20261107012240.png]]

#### 进入页面 — 查询骑行信息

使用开始骑行响应或 `RIDE_STARTED` 中收到的 `rideSessionId`。

请求)
```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

无请求 body。

响应 — 成功时 HTTP 200)

```json
{
  "groupName": "테스트 그룹1",
  "participantCount": 5
}
```

| 数据                | UI 显示       |
| ------------------ | ----------- |
| `groupName`        | 顶部的群组名称    |
| `participantCount` | “当前有 5 人正在骑行” |

---

#### 建立骑行 WebSocket 连接

连接地址)
```text
ws://서버주소:포트/ws/ride/10
```

首次连接请求头)
```http
Authorization: Bearer JWT_TOKEN
```

- 将当前的 `rideSessionId` 填入 `10` 的位置。 
- 如果已有该次骑行的连接，则复用。
- 同时保持现有的 `/ws/login` 连接。

---

#### 手势信号按钮

点击按钮时，**通过骑行 WebSocket 发送 JSON 文本消息**。 
- 不是 HTTP POST 请求。

应用 → 服务器, `/ws/ride/10`)

```json
{
  "type": "RIDE_SIGNAL",
  "signal": "LEFT"
}
```

|UI 按钮|`signal` 值|
|---|---|
|注意路面|`ROAD_HAZARD`|
|注意弯道|`CURVE_CAUTION`|
|左转|`LEFT`|
|右转|`RIGHT`|
|单列骑行|`SINGLE_FILE`|
|双列骑行|`DOUBLE_FILE`|
|减速骑行|`SLOW`|
|停止|`STOP`|

- 不要在消息中加入用户 ID 或会话 ID，服务器通过认证信息和连接路径进行识别。
- 服务器将信号转发给同一次骑行中的跟骑参与者。
- **不会向领骑者单独发送发送成功响应。** 不能仅因应用已发送，就显示所有跟骑者均已收到。
- 为反馈手势信号的发送操作，按住按钮时将按钮颜色改为黄色等颜色，松开后恢复原有颜色。

---

#### 当前速度 / 行驶距离

不通过服务器 API 查询，而是**在 Android 端基于 GPS 计算**。

|UI 项目|数据|
|---|---|
|当前速度|当前基于 GPS 的速度，km/h|
|行驶距离|本次骑行的累计行驶距离，km|

- 在应用中检查位置权限和 GPS 启用状态。
- 骑行结束后要保存的**平均速度也由应用计算**。需区分当前速度和平均速度。
- 此页面的手势信号消息不包含 GPS 或速度、距离值。

---

#### 结束骑行按钮

请求)
```http
POST /api/v1/rides/10/finish
Authorization: Bearer JWT_TOKEN
```
- 无请求 body。仅该次骑行的领骑者可以调用。

响应 — 成功时 HTTP 200)
```json
{
  "rideSessionId": 10,
  "status": "FINISHED",
  "endedAt": "2026-11-07T15:00:00"
}
```

服务器还会通过领骑者和跟骑者的 `/ws/ride/10` 连接发送结束消息。

```json
{
  "type": "RIDE_FINISHED",
  "rideSessionId": 10
}
```

应用处理)
1. 请求期间防止重复点击结束按钮。
2. **收到结束 REST 成功响应或 `RIDE_FINISHED` 时**，停止发送手势信号并结束骑行数据测量。
3. 计算最终行驶距离和平均速度，并保存个人记录。
4. 即使同时收到 REST 响应和 WebSocket 通知，结束处理也只执行一次。
    - 服务器在发送结束通知后会关闭该次骑行的 WebSocket。应用应停止该连接的自动重连。
    - 保持 `/ws/login` 连接。
    - **单纯的 WebSocket 断线不代表骑行结束。**

---

#### 结束后 — 保存个人记录

请求)
```http
PUT /api/v1/rides/10/record
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

请求 body)
```json
{
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

响应 — 成功时 HTTP 200)
```json
{
  "rideSessionId": 10,
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

- `distance`: km, `averageSpeed`: km/h.
- 两个值均为必填，且须**大于等于 0，整数部分最多 8 位，小数部分最多 2 位**。
- 仅保存领骑者本人的记录。跟骑者需在各自的应用中单独保存。
- 保存完成后  跳转至主页面。
- 保存失败时，在应用中保留计算结果并提供重试。再次发送相同值不会重复创建记录。

---

#### 错误及断线处理

WebSocket 错误示例)

```json
{
  "type": "ERROR",
  "code": "FORBIDDEN",
  "message": "선두만 수신호를 전송할 수 있습니다."
}
```

|类别|错误|应用处理|
|---|---|---|
|REST / 连接认证|HTTP 401|提示重新登录|
|REST / WebSocket|`FORBIDDEN`|提示没有参与者或领骑者权限|
|REST / WebSocket|`RIDE_SESSION_NOT_FOUND`|提示骑行不存在|
|结束 REST / WebSocket|`RIDE_NOT_ACTIVE`|提示骑行不处于进行中状态，并停止发送信号|
|手势信号 WebSocket|`INVALID_MESSAGE`, `UNSUPPORTED_MESSAGE_TYPE`|检查 JSON 格式及 `type`、`signal` 的值|
|记录保存 REST|`INVALID_REQUEST`|检查记录值的范围和小数位数|
|记录保存 REST|`RIDE_NOT_FINISHED`|确认服务器是否已完成骑行结束处理|

- 临时断线时可以重连，但**不要在恢复连接后集中补发断线期间的手势信号。**
- 必须对服务器的 Ping 回复 Pong，JWT 过期时需要重新登录。
- 因网络问题未收到结束响应时，可以重新请求结束 REST API。如果会话已结束，则返回原有的结束结果。

----
# 12. 跟骑队伍骑行页面
![[Pasted image 20261107013251.png]]

#### 进入页面 — 查询骑行信息

使用通过 `/ws/login` 收到的 `RIDE_STARTED` 中的 `rideSessionId`。

请求)

```http
GET /api/v1/rides/10
Authorization: Bearer JWT_TOKEN
```

响应 — 成功时 HTTP 200)

```json
{
  "groupName": "테스트 그룹1",
  "participantCount": 5
}
```

| 数据                | UI 显示    |
| ------------------ | -------- |
| `groupName`        | 顶部的群组名称 |
| `participantCount` | 参与人数    |

---

#### 建立骑行 WebSocket 连接

连接地址)
```text
ws://서버주소:포트/ws/ride/10
```

首次连接请求头)
```http
Authorization: Bearer JWT_TOKEN
```

- 将当前的 `rideSessionId` 填入 `10` 的位置。 
- 如果已建立连接，则复用。
- 同时保持现有的 `/ws/login` 连接。

---

#### 接收手势信号 — 显示提示框

服务器 → 跟骑者, `/ws/ride/10`)
```
{
  "type": "RIDE_SIGNAL",
  "senderId": 1,
  "signal": "ROAD_HAZARD"
}
```

|`signal`|显示文案|
|---|---|
|`ROAD_HAZARD`|注意路面|
|`CURVE_CAUTION`|注意弯道|
|`LEFT`|左转|
|`RIGHT`|右转|
|`SINGLE_FILE`|单列骑行|
|`DOUBLE_FILE`|双列骑行|
|`SLOW`|减速骑行|
|`STOP`|停止|
|`LEADER_CHANGE`|请求更换领骑者|

应用处理)
1. 根据收到的信号，更新顶部手势信号提示框的图标和文案。
2. **每次收到信号时，让提示框背景在灰色 ↔ 黄色之间交替闪烁。**
3. 显示接收时间，并添加到最近信号列表。
4. 如果已开启语音提示或振动，则执行相应提醒。

闪烁行为
- 背景**每隔 0.5 秒切换一次，往返 3 次**，然后保持黄色。
- 闪烁期间收到新信号时，替换为最新信号，并从头重新播放效果。
- 即使再次收到相同信号，也按新收到的信号处理。

---

**最近信号列表**
- 将收到的信号保存在应用内存中，并按时间从新到旧显示。
- 服务器消息中没有时间，因此记录**应用收到消息的时间**。
- 没有信号记录查询 API，断线期间错过的信号不会补发。
- 开始新的骑行时，清空上一次骑行的列表。

---

**语音提示 / 振动按钮**

|按钮|应用处理|
|---|---|
|语音提示 ON/OFF|切换收到信号时是否播报相应文案|
|振动 ON/OFF|切换收到信号时是否振动|

- 设置由应用管理，无需发送服务器请求。
- 即使关闭语音和振动，也应**保留提示框显示及闪烁效果**。
- 每次语音提示将信号名称播报 2 遍。
- 按 2 秒间隔触发 1 次振动。

---

**当前速度 / 行驶距离**

- 在 Android 端基于 GPS 计算。
- 当前速度以 km/h 显示，累计行驶距离以 km 显示。
- 结束时要保存的平均速度也由应用单独计算。
- 跟骑者不发送手势信号。

---

#### 领队结束骑行 — 跳转至主页面

领队结束骑行后，服务器会通过 `/ws/ride/10` 发送以下消息。
```json
{
  "type": "RIDE_FINISHED",
  "rideSessionId": 10
}
```

应用处理)
1. 确认是否为当前骑行的结束通知。
2. 停止骑行数据测量、闪烁、语音和振动。
3. 在应用中保存最终行驶距离和平均速度，并请求保存个人记录。
4. **关闭骑行页面并跳转至主页面。**
5. 清理页面返回栈，避免通过返回操作回到已结束的骑行页面。
    - 记录保存失败不应阻止跳转至主页面，需保留待保存的值以便重试。
    - 对于同一 `rideSessionId`，结束处理只执行一次。
    - 服务器在发送结束通知后会关闭骑行 WebSocket。停止该连接的自动重连。
    - 保持现有的 `/ws/login` 连接。

---

#### 结束后 — 保存个人记录

请求)
```http
PUT /api/v1/rides/10/record
Authorization: Bearer JWT_TOKEN
Content-Type: application/json
```

请求 body)
```json
{
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

响应 — 成功时 HTTP 200)

```json
{
  "rideSessionId": 10,
  "distance": 6.3,
  "averageSpeed": 14.5
}
```

- 仅保存 JWT 对应用户本人的记录。**领队不会代为保存跟骑者的记录。**
- 两个值均为必填，且须大于等于 0，整数部分最多 8 位，小数部分最多 2 位。
- 保存失败时，在主页面提示重试。重新发送时会更新现有记录。

---

**错误及断线处理**

|情况|应用处理|
|---|---|
|WebSocket 临时断线|显示“信号连接已断开”后尝试重连|
|认证失败或 JWT 过期|提示重新登录|
|`FORBIDDEN`|提示没有参与该次骑行的权限|
|`RIDE_SESSION_NOT_FOUND`|提示骑行不存在|
|`RIDE_NOT_ACTIVE`|提示会话不处于进行中状态，并停止重连|
|记录保存 `INVALID_REQUEST`|检查记录值的范围和小数位数|
|记录保存 `RIDE_NOT_FINISHED`|确认服务器尚未确认骑行结束|

