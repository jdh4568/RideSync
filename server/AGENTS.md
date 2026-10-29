# RideMate Backend - AGENTS.md

## 1. 프로젝트 개요

RideMate는 자전거 그룹 라이딩을 지원하는 Android 애플리케이션이다.

백엔드는 사용자 인증, 그룹 관리, 라이딩 세션 관리, 실시간 통신 및 개인 주행 기록 관리를 담당한다.

### 기술 스택

- Java 17
- Spring Boot 4.1.1
- Spring Data JPA (Hibernate)
- Spring Security + JWT
- MySQL
- Spring WebSocket
- Gradle
- Lombok

## 2. 프로젝트 구조

기본 패키지: `com.ridesync.server`

계층형 패키지 구조를 사용한다.

```text
com.ridesync.server
├── config/        # Spring 및 WebSocket 설정
├── controller/    # REST API 처리
├── dto/           # 요청 및 응답 DTO
├── entity/        # JPA Entity
├── exception/     # 예외 처리
├── repository/    # DB 접근
├── security/      # 인증 및 JWT 처리
├── service/       # 비즈니스 로직
└── websocket/     # 실시간 통신
```

기존 패키지 구조를 우선 사용하며 불필요하게 새로운 계층이나 패키지를 추가하지 않는다.

## 3. 설계 문서

프로젝트의 주요 설계 문서는 다음과 같다.

- `docs/DATABASE.md`: 데이터베이스 구조 및 관련 비즈니스 규칙
- `docs/API_SPEC.md`: REST API 및 WebSocket 통신 명세

관련 기능을 구현하기 전에 해당 문서를 확인한다.

구현 과정에서 문서와 기존 코드가 충돌하면 임의로 판단하여 변경하지 않고 사용자에게 확인한다.

## 4. 데이터베이스 및 JPA 규칙

1. 기존 MySQL 데이터베이스와 JPA Entity를 사용한다.
2. 테이블 및 컬럼 구조를 임의로 변경하지 않는다.
3. 기존 Entity의 필드 및 연관관계 매핑을 유지한다.
4. Entity와 API DTO를 분리한다.
5. JPA 연관관계는 기본적으로 LAZY 로딩을 사용한다.
6. 데이터베이스 접근은 Repository 계층을 사용한다.
7. 비즈니스 검증과 트랜잭션 처리는 Service 계층에서 담당한다.
8. `spring.jpa.hibernate.ddl-auto=validate` 설정을 유지한다.
9. 스키마 변경이 필요하면 변경 사유를 먼저 설명하고 승인을 받는다.

## 5. API 및 WebSocket 규칙

1. REST API는 `docs/API_SPEC.md`에 정의된 엔드포인트와 요청·응답 형식을 따른다.
2. Controller는 요청 및 응답 처리를 담당하고 비즈니스 로직은 Service에서 구현한다.
3. API 요청과 응답에는 DTO를 사용하며 Entity를 직접 반환하지 않는다.
4. 인증이 필요한 API는 Spring Security와 JWT를 이용한다.
5. WebSocket 통신은 REST API와 구분하여 구현한다.
6. 라이딩 참여 요청 상태는 서버 메모리에서 관리한다.
7. 실시간 라이딩 신호는 WebSocket으로 송수신한다.

## 6. 코딩 규칙

1. Java 클래스명은 PascalCase, 메서드 및 필드명은 camelCase를 사용한다.
2. 패키지 이름은 소문자로 작성한다.
3. 기존 프로젝트의 코딩 스타일을 우선 따른다.
4. 생성자 주입을 사용하며 필드 주입은 지양한다.
5. 불필요한 추상화, 인터페이스, 디자인 패턴은 도입하지 않는다.
6. 기존 기능에 영향을 주지 않도록 변경 범위를 최소화한다.
7. 구현에 필요한 코드만 작성하고 불필요한 기능을 추가하지 않는다.

## 7. 개발 및 작업 원칙

1. MVP 구현과 정상적인 시연을 최우선 목표로 한다.
2. 요청받은 기능의 범위 내에서 작업한다.
3. 기존 코드와 관련 설계 문서를 확인한 후 구현한다.
4. 이미 구현된 기능이 있다면 중복 작성하지 않고 재사용한다.
5. 단순한 기능은 가능한 한 간결하게 구현한다.
6. 새로운 라이브러리는 필요한 경우에만 추가한다.
7. 기존 API, DB 스키마 및 프로젝트 구조를 변경해야 한다면 먼저 사용자에게 설명하고 승인을 받는다.
8. 작업 후 변경한 파일과 구현 내용을 간단히 요약한다.
9. 가능한 경우 빌드 및 관련 테스트를 실행하고 결과를 보고한다.
10. 설명과 작업 결과는 한국어로 작성한다.
