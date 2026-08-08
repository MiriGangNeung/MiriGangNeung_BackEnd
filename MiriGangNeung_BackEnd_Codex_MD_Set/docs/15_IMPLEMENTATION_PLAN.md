# 15 Implementation Plan

## Phase 0 — Repository bootstrap

1. Spring Boot/Gradle project
2. JPA/MySQL
3. Redis
4. configuration profiles
5. global error response
6. Swagger/OpenAPI
7. test setup

## Phase 1 — Place

1. Place entity
2. PlaceImage
3. repositories
4. TourApiClient interface
5. Korean tourism API implementation
6. normalizer
7. cache
8. `/places`
9. `/places/{id}`
10. nearby/related

## Phase 2 — Composition

1. CompositionJob entity
2. TemporaryImageStorage
3. upload validation
4. AiGenerationClient interface
5. job creation
6. async processing
7. status polling
8. download
9. retry
10. cleanup

AI provider implementation is blocked until AI teammate provides the concrete contract. Do not invent one.

## Phase 3 — Recommendation

1. Candidate collector
2. hard filters
3. preference scoring
4. popularity/crowd scoring
5. distance scoring
6. diversity
7. course builder
8. time feasibility
9. Kakao route
10. final response

## Phase 4 — Course persistence/share

1. Course
2. CourseStop
3. anonymous course id
4. share token
5. share revoke
6. expiry

## Phase 5 — Frontend integration

Replace:

- mock places
- mock composition timer
- mock result
- mock course
- hardcoded distance/time

with real API calls.

## Phase 6 — Hardening

1. tests
2. rate limit
3. cleanup
4. external timeout
5. logs/metrics
6. CORS
7. deployment
8. README

## Important sequencing

Do not implement every future feature before the P0 flow works end-to-end.

The first milestone is:

```text
GET places
→ POST composition
→ polling
→ result
→ POST course
→ course result
→ Kakao route
```
