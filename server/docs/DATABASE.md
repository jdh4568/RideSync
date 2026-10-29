## 1. 데이터베이스 개요

- **DBMS:** MySQL

- **ORM:** Spring Data JPA (Hibernate)

- **테이블 수:** 5개

- **기본키:** BIGINT 기반 식별자


### 테이블 목록

|테이블|역할|
|---|---|
|USER|사용자 계정 정보 관리|
|RIDING_GROUP|라이딩 그룹 정보 관리|
|GROUP_MEMBER|그룹 소속 및 가입 신청 상태 관리|
|RIDE_SESSION|라이딩 세션 정보 관리|
|RIDE_SESSION_MEMBER|세션 참여자 및 개인 주행 기록 관리|

본 문서는 기존 확정 ERD를 기준으로 작성하며, 테이블 및 컬럼 구조를 임의로 변경하지 않는다.

---

## 2. 테이블 상세 구조

### 2.1 USER

사용자의 계정 정보를 저장하는 테이블이다.

|컬럼명|데이터 타입|키|설명|
|---|---|---|---|
|user_id|BIGINT|PK|사용자 고유 ID|
|login_id|VARCHAR|UK|로그인 아이디|
|password|VARCHAR|-|암호화된 비밀번호|
|nickname|VARCHAR|UK|사용자 닉네임|
|created_at|DATETIME|-|계정 생성일|

**주요 규칙**

- login_id와 nickname은 각각 중복을 허용하지 않는다.

- password는 BCrypt 등 안전한 방식으로 해싱하여 저장한다.

- user_id는 사용자 식별을 위한 기본키이다.


### 2.2 RIDING_GROUP

라이딩 그룹의 기본 정보를 저장하는 테이블이다.

|컬럼명|데이터 타입|키|설명|
|---|---|---|---|
|group_id|BIGINT|PK|그룹 고유 ID|
|group_name|VARCHAR|-|그룹 이름|
|description|VARCHAR|-|그룹 설명|
|max_members|INT|-|그룹 최대 인원|
|owner_id|BIGINT|FK|그룹장 USER ID|
|created_at|DATETIME|-|그룹 생성일|

**주요 규칙**

- 그룹 최대 인원은 2~50명이다.

- 그룹을 생성한 사용자가 그룹장이 된다.

- owner_id는 USER.user_id를 참조한다.

- 그룹 생성 시 그룹장을 GROUP_MEMBER에도 등록한다.


### 2.3 GROUP_MEMBER

사용자의 그룹 가입 관계 및 가입 신청 상태를 관리하는 테이블이다.

|컬럼명|데이터 타입|키|설명|
|---|---|---|---|
|group_member_id|BIGINT|PK|그룹 멤버 고유 ID|
|group_id|BIGINT|FK|소속 그룹 ID|
|user_id|BIGINT|FK|사용자 ID|
|role|VARCHAR|-|그룹 내 역할|
|status|VARCHAR|-|그룹 가입 상태|
|joined_at|DATETIME|-|그룹 가입 승인일|

**role 값**

|값|의미|
|---|---|
|OWNER|그룹장|
|MEMBER|일반 그룹원|

**status 값**

|값|의미|
|---|---|
|PENDING|가입 승인 대기|
|APPROVED|가입 승인 완료|
|REJECTED|가입 거절|

**주요 규칙**

- 한 사용자는 동일 그룹에 중복 등록될 수 없다.

- `UNIQUE(group_id, user_id)` 적용을 권장한다.
    - 한 사용자가 여러 그룹에 가입할 수 있지만 동일한 그룹에는 중복 가입할 수 없도록 하는 제약조건

- 그룹 생성자는 OWNER / APPROVED 상태로 등록한다.

- 일반 사용자가 가입 신청하면 MEMBER / PENDING 상태로 등록한다.

- 그룹장이 승인하면 APPROVED로 변경하고 joined_at을 기록한다.

- 그룹장이 거절하면 REJECTED로 변경한다.

- APPROVED 상태인 사용자만 정식 그룹원으로 취급한다.

- 가입 승인 인원이 그룹 최대 인원을 초과할 수 없다.


### 2.4 RIDE_SESSION

라이딩 그룹에서 진행하는 개별 라이딩 세션을 관리하는 테이블이다.

|컬럼명|데이터 타입|키|설명|
|---|---|---|---|
|ride_session_id|BIGINT|PK|라이딩 세션 ID|
|group_id|BIGINT|FK|라이딩 그룹 ID|
|leader_id|BIGINT|FK|선두 주자 USER ID|
|status|VARCHAR|-|라이딩 진행 상태|
|started_at|DATETIME|-|라이딩 시작 시간|
|ended_at|DATETIME|-|라이딩 종료 시간|
|created_at|DATETIME|-|세션 생성 시간|

**status 값**

|값|의미|
|---|---|
|READY|라이딩 준비|
|RIDING|라이딩 진행 중|
|FINISHED|라이딩 종료|

**주요 규칙**

- 라이딩 세션은 하나의 그룹에 소속된다.

- leader_id는 USER.user_id를 참조한다.

- 선두 주자는 해당 그룹의 승인된 멤버여야 한다.

- 라이딩 시작에 필요한 최소 참여 인원은 선두를 포함한 2명이다.

- 라이딩이 시작되면 started_at을 기록하고 RIDING 상태로 변경한다.

- 라이딩 종료는 리더만 수행할 수 있다.

- 종료 시 ended_at을 기록하고 FINISHED 상태로 변경한다.


### 2.5 RIDE_SESSION_MEMBER

라이딩 세션에 참여하는 사용자와 개인별 주행 기록을 관리하는 테이블이다.

|컬럼명|데이터 타입|키|설명|
|---|---|---|---|
|ride_session_member_id|BIGINT|PK|세션 멤버 고유 ID|
|ride_session_id|BIGINT|FK|라이딩 세션 ID|
|user_id|BIGINT|FK|참여 사용자 ID|
|role|VARCHAR|-|라이딩 내 역할|
|distance|DECIMAL|-|개인 주행 거리|
|average_speed|DECIMAL|-|개인 평균 주행 속도|
|joined_at|DATETIME|-|세션 참여 시간|

**role 값**

|값|의미|
|---|---|
|LEADER|선두 주자|
|MEMBER|후미 주자|

**주요 규칙**

- 동일 사용자는 동일 세션에 중복 등록될 수 없다.

- `UNIQUE(ride_session_id, user_id)` 적용을 권장한다.

- 세션에 참여하는 사용자는 해당 그룹의 승인된 멤버여야 한다.

- 라이딩 참여를 수락한 사용자만 세션 멤버로 등록한다.

- 라이딩 시작 시 참여자 레코드를 생성한다.

- distance와 average_speed는 기록 저장 전까지 NULL을 허용한다.

- Android에서 GPS를 기반으로 주행 거리와 평균 속도를 계산한다.

- 라이딩 종료 시 계산한 기록을 서버에 전송하여 업데이트한다.


**단위**

- distance: km

- average_speed: km/h


---

## 3. 테이블 관계

|부모 테이블|자식 테이블|관계|설명|
|---|---|---|---|
|USER|RIDING_GROUP|1|사용자가 여러 그룹을 생성|
|USER|GROUP_MEMBER|1|사용자가 여러 그룹에 가입|
|RIDING_GROUP|GROUP_MEMBER|1|그룹에 여러 멤버 소속|
|RIDING_GROUP|RIDE_SESSION|1|그룹에서 여러 라이딩 진행|
|USER|RIDE_SESSION|1|사용자가 여러 라이딩의 선두 가능|
|USER|RIDE_SESSION_MEMBER|1|사용자가 여러 라이딩에 참여|
|RIDE_SESSION|RIDE_SESSION_MEMBER|1|세션에 여러 사용자 참여|

---

## 4. Mermaid ERD

아래 Mermaid는 기존 확정 ERD의 테이블 구조 및 관계를 그대로 반영한다.

```
---
config:
  theme: redux-color
---
erDiagram
    direction LR

    USER {
        BIGINT user_id PK
        VARCHAR login_id UK
        VARCHAR password
        VARCHAR nickname UK
        DATETIME created_at
    }

    RIDING_GROUP {
        BIGINT group_id PK
        VARCHAR group_name
        VARCHAR description
        INT max_members
        BIGINT owner_id FK
        DATETIME created_at
    }

    GROUP_MEMBER {
        BIGINT group_member_id PK
        BIGINT group_id FK
        BIGINT user_id FK
        VARCHAR role
        VARCHAR status
        DATETIME joined_at
    }

    RIDE_SESSION {
        BIGINT ride_session_id PK
        BIGINT group_id FK
        BIGINT leader_id FK
        VARCHAR status
        DATETIME started_at
        DATETIME ended_at
        DATETIME created_at
    }

    RIDE_SESSION_MEMBER {
        BIGINT ride_session_member_id PK
        BIGINT ride_session_id FK
        BIGINT user_id FK
        VARCHAR role
        DECIMAL distance
        DECIMAL average_speed
        DATETIME joined_at
    }

    USER ||--o{ RIDING_GROUP : "그룹 소유"
    USER ||--o{ GROUP_MEMBER : "그룹 가입"
    RIDING_GROUP ||--o{ GROUP_MEMBER : "그룹 구성원"
    RIDING_GROUP ||--o{ RIDE_SESSION : "라이딩 세션"
    USER ||--o{ RIDE_SESSION : "선두 라이더"
    USER ||--o{ RIDE_SESSION_MEMBER : "라이딩 참여"
    RIDE_SESSION ||--o{ RIDE_SESSION_MEMBER : "세션 참여자"
```

---

## 5. 라이딩 참여 요청 관리

라이딩 초대 및 수락·거절 상태는 데이터베이스에 저장하지 않는다.

### 처리 구조

- WebSocket을 이용하여 라이딩 초대 메시지를 전송한다.

- 서버 메모리에서 참여 요청과 응답 상태를 관리한다.

- 라이딩 참여 요청은 정식 라이딩 세션 참여 기록과 구분한다.


**서버 메모리에서 관리하는 상태**

|상태|의미|
|---|---|
|PENDING|참여 요청 응답 대기|
|ACCEPTED|참여 수락|
|REJECTED|참여 거절|

### 처리 순서

1. 선두 주자가 그룹 멤버를 선택하여 참여 요청을 전송한다.

2. 서버가 메모리에 참여 요청과 상태를 기록한다.

3. 선택된 멤버에게 WebSocket으로 요청을 전달한다.

4. 각 멤버의 수락·거절 응답을 서버에서 관리한다.

5. 선두를 포함해 2명 이상 참여가 확정되면 라이딩을 시작할 수 있다.

6. 라이딩 시작 시 RIDE_SESSION과 RIDE_SESSION_MEMBER에 필요한 정보를 저장한다.

7. 참여 요청의 임시 상태 정보는 더 이상 필요하지 않으면 메모리에서 삭제한다.


**주의사항**

- 서버가 재시작되면 메모리의 임시 참여 요청 정보는 사라진다.

- 서버 재시작으로 초대 상태가 사라진 경우 해당 요청은 취소된 것으로 처리한다.

- 라이딩 참여 요청 상태와 GROUP_MEMBER.status는 서로 다른 개념이다.

- GROUP_MEMBER.status는 그룹 가입 신청 상태만 관리한다.


---

## 6. 주행 기록 관리

### 주행 중

- Android 클라이언트에서 GPS를 활용해 이동 거리를 계산한다.

- 평균 속도 역시 클라이언트에서 계산한다.

- 실시간 GPS 좌표는 MySQL에 저장하지 않는다.


### 주행 종료

- 선두 주자가 라이딩 종료를 요청한다.

- 서버가 라이딩 세션을 종료 처리한다.

- 각 참여자는 자신의 최종 주행 정보를 REST API로 전송한다.

- 서버는 RIDE_SESSION_MEMBER의 distance와 average_speed를 업데이트한다.


### 개인 통계 조회

기존 테이블에 저장된 기록을 활용하여 다음 정보를 조회한다.

- 총 주행 거리

- 평균 주행 속도

- 총 라이딩 횟수

- 마지막 라이딩 날짜

- 최근 라이딩 기록 3개


통계값은 별도 USER 컬럼을 추가하지 않고 기존 주행 기록 데이터를 집계하여 계산한다.

---

## 7. 데이터베이스 구현 및 관리 원칙

1. 확정된 5개 테이블과 컬럼명 및 데이터 타입을 유지한다.

2. MySQL에 생성된 실제 테이블 구조를 데이터베이스 스키마의 기준으로 사용한다.

3. 기존 JPA Entity를 재사용하며, Entity와 API DTO를 분리한다.

4. JPA 연관관계는 기본적으로 LAZY 로딩을 사용한다.

5. 양방향 연관관계는 필요한 경우에만 사용한다.

6. 그룹 및 라이딩 관련 비즈니스 검증은 Service 계층에서 처리한다.

7. 비밀번호는 BCrypt 등 안전한 해시 알고리즘을 사용하여 저장한다.

8. 가입 신청 및 라이딩 상태 변경은 필요한 경우 트랜잭션으로 처리한다.

9. 삭제 정책은 기존 FK 제약조건과 참조 무결성을 고려하여 결정한다.

10. 그룹 가입 요청과 라이딩 참여 요청은 서로 다른 상태 관리 체계를 사용한다.

11. 라이딩 참여 요청의 임시 상태는 서버 메모리에서 관리하며 별도 DB 테이블을 생성하지 않는다.


### 스키마 관리 원칙

- 기존 MySQL DDL에 정의된 VARCHAR 길이, DECIMAL 정밀도, NULL 허용 여부, 기본값 및 제약조건을 유지한다.

- 기존 테이블이나 컬럼을 임의로 추가·수정·삭제하지 않는다.

- JPA Entity 및 Repository는 기존에 생성된 코드를 우선 활용한다.

- `spring.jpa.hibernate.ddl-auto=validate`를 사용하여 Entity와 DB 스키마의 일치 여부를 검증한다.

- DB 구조 변경이 필요한 경우 직접 변경하지 않고 변경 사유와 영향을 먼저 설명한다.


**최종 원칙:** 데이터베이스 구조는 MySQL에 생성된 5개 테이블을 기준으로 유지한다. 별도의 명시적인 요청 없이 DB 스키마를 확장하거나 기존 구조를 변경하지 않는다.