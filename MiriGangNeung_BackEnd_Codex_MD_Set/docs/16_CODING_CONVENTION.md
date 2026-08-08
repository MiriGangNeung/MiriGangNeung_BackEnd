# 16 Coding Convention

## Java

- Java records for immutable API DTOs when appropriate
- Entity는 class
- enum은 의미가 명확한 경우 사용
- null 남발 금지
- Optional은 return value에 제한적으로 사용

## Naming

```text
PlaceController
PlaceService
PlaceRepository
TourApiClient

CreateCompositionRequest
CompositionStatusResponse

CreateCourseRequest
CourseResponse
```

## DTO

Request/Response DTO와 Entity를 분리한다.

외부 API DTO와 내부 DTO도 분리한다.

```text
TourApiResponse
→ PlaceMapper
→ Place
→ PlaceResponse
```

## Service

Controller에 비즈니스 로직을 넣지 않는다.

Repository에 비즈니스 규칙을 넣지 않는다.

## Entity

외부 API JSON을 그대로 Entity에 매핑하지 않는다.

## Transaction

`@Transactional`은 실제 DB transaction boundary에만 사용한다.

외부 API 호출을 장시간 transaction에 묶지 않는다.

## Exception

도메인 예외는 명확한 code를 갖는다.

Global exception handler에서 HTTP status로 변환한다.

## Configuration

외부 API URL/key/timeout/retry/TTL/추천 가중치는 configuration으로 분리한다.

## Logging

로그에:

- API key
- image URL
- raw image
- 개인정보
- full request body

를 기록하지 않는다.

## API versioning

`/api/v1` 사용.

Breaking change가 생기면 v2를 고려한다.

## Git

Commit은 하나의 논리적 변경 단위로 한다.

예:

```text
feat: implement place search API
feat: add composition job polling
fix: validate one pick belongs to selected places
test: add course feasibility tests
```
