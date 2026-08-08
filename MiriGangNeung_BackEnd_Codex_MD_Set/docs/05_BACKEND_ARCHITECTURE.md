# 05 Backend Architecture

## 목표 구조

```text
React Frontend
      |
      | REST/JSON + multipart
      v
Spring Boot API
      |
      +-- Place Service
      |      +-- TourApiClient
      |      +-- Redis
      |      +-- MySQL
      |
      +-- Composition Service
      |      +-- AiGenerationClient
      |      +-- TemporaryImageStorage
      |      +-- Redis
      |
      +-- Course Service
      |      +-- RecommendationEngine
      |      +-- TourApiClient
      |      +-- KakaoRouteClient
      |      +-- Redis/MySQL
      |
      +-- Share Service
             +-- CourseRepository
             +-- ShareToken
```

## 패키지 권장

```text
com.mirigangneung
├── common
│   ├── error
│   ├── response
│   ├── config
│   └── util
├── place
│   ├── controller
│   ├── service
│   ├── domain
│   ├── repository
│   ├── dto
│   └── client
├── composition
├── course
├── share
├── infrastructure
│   ├── tourapi
│   ├── kakao
│   ├── ai
│   ├── storage
│   └── redis
└── MiriGangneungApplication
```

## 레이어 규칙

Controller
→ request validation
→ Service
→ Domain/Repository/Client
→ DTO mapping
→ Response

Controller에서 외부 API를 직접 호출하지 않는다.

Entity를 Controller에서 직접 반환하지 않는다.

## 외부 API Adapter

```java
interface TourApiClient
interface KakaoRouteClient
interface AiGenerationClient
interface TemporaryImageStorage
```

구현체는 infrastructure에 둔다.

## 비동기

AI 생성은 HTTP request에서 장시간 block하지 않는다.

```text
POST /compositions
→ jobId 즉시 반환

background processing
→ Redis/DB status

GET /compositions/{jobId}
→ 현재 상태
```

현재 MVP에서는 Spring의 `@Async` 등 단순한 비동기부터 시작할 수 있다. 실제 AI provider가 자체 queue를 제공하면 Adapter 내부에서 이를 사용한다.

## 트랜잭션

- 코스 생성 결과 저장: 적절한 transaction boundary
- 외부 API 호출은 DB transaction 안에서 장시간 수행하지 않는다.
- 외부 API 성공 후 필요한 데이터만 transaction으로 저장한다.
