# Project Status

Last Updated: 2026-08-08 16:13 KST
Last Updated By: Codex

기준일: 2026-08-08

## Repository

- 프로젝트: `MiriGangNeung_BackEnd`
- Java/Spring Boot/Gradle 프로젝트가 루트에 있다.
- Gradle Wrapper가 포함되어 있다.
- 현재 작업 트리에는 아직 구현 파일에 대한 신규 commit이 없다. `HEAD`의 최신 commit은 문서 세트 추가 commit이다.

## 현재 코드에 존재하는 영역

- 공통: CORS, RedisTemplate, 전역 예외 응답
- Place: `Place`, `PlaceImage`, Repository, Service, DTO, Controller
- 관광공사: `TourApiClient`, Korean API adapter와 JSON/XML 응답 정규화
- Composition: `CompositionJob`, 업로드, 상태 조회, retry/download API, `AiGenerationClient` 인터페이스, 로컬 임시 이미지 저장소, 만료 정리 Job
- Course: `Course`, `CourseStop`, 저장/조회/삭제/공유 API
- Recommendation: `RuleBasedCourseRecommendationEngine`
- Route: `KakaoRouteClient`와 REST adapter, normalized route response

## 현재 API Controller

구현된 Controller 경로는 다음과 같다.

- `/api/v1/places`
- `/api/v1/compositions`
- `/api/v1/courses`
- `/api/v1/share/courses`
- `/api/v1/routes/walking`

세부 request/response 계약은 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`를 기준으로 한다.

## 현재 검증 결과

2026-08-08 기준 `./gradlew.bat test` 실행 결과는 `BUILD SUCCESSFUL`이며 단위 테스트 2개가 통과했다.

## 확인된 미완성/제한

- 실제 AI Provider 구현체와 비동기 Provider polling은 없다. `AiGenerationClient` 인터페이스만 존재한다.
- Composition Job은 Provider가 연결되지 않은 현재 코드에서 실제 DONE 결과를 생성하지 않는다.
- `RedisCache` helper는 존재하지만 Place 조회 캐시 흐름에 연결되어 있지 않다.
- CourseResponse의 route 거리/시간은 아직 추천 결과에 통합되지 않는다.
- Controller 통합 테스트와 MySQL/Redis 통합 테스트는 없다.
- rate limit, 상세 metrics, Swagger/OpenAPI 문서는 아직 없다.

이 문서는 계획이 아니라 현재 코드 확인 결과를 기록한다. 변경 시 실제 코드와 테스트를 다시 확인해 갱신한다.
