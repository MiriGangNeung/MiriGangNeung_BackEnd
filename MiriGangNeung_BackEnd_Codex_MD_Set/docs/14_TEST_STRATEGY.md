# 14 Test Strategy

## Unit Test

필수:

- recommendation score
- duration calculation
- candidate filter
- route feasibility
- onePick inclusion
- custom date validation
- share token validation
- image expiry calculation
- external response normalization

## Service Test

Mock:

- TourApiClient
- KakaoRouteClient
- AiGenerationClient
- TemporaryImageStorage
- Redis

## Controller Test

필수 endpoint:

```text
GET /places
GET /places/{id}

POST /compositions
GET /compositions/{jobId}
POST /compositions/{jobId}/retry
GET /compositions/{jobId}/download

POST /courses
GET /courses/{id}
DELETE /courses/{id}

POST /courses/{id}/share
GET /share/courses/{token}
DELETE /courses/{id}/share

POST /routes/walking
```

## Integration

MySQL Testcontainers 또는 팀 환경에 맞는 integration DB를 사용한다.

Redis도 integration test에서 실제 Redis container를 사용하는 것을 권장한다.

## External API

실제 키를 CI에 노출하지 않는다.

WireMock/MockWebServer 등으로 contract-like response를 테스트한다.

## Acceptance

### Composition

- invalid image rejected
- valid image creates job
- job polling works
- done result downloadable
- failed result returns normalized error
- expired result unavailable

### Course

- onePick always included
- only Gangneung candidates
- duration constraint respected
- route calculated
- no invalid place IDs
- share token works
- revoked share fails
