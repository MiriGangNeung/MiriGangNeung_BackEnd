# Project Status

Last Updated: 2026-08-24 14:30 KST
Last Updated By: Codex

기준일: 2026-08-08

## Repository

- 프로젝트: `MiriGangNeung_BackEnd`
- Java/Spring Boot/Gradle 프로젝트가 루트에 있다.
- Gradle Wrapper가 포함되어 있다.
- 구현·Docker·API 검증 변경은 `46fbdd7` (`feat: add Docker deployment and verify P0 API flow`)에 기록되어 있다.
- 최신 상태 문서 커밋은 `fb78ec5` (`docs: record final Docker and API verification`)이다. 이후 문서 보완 작업은 별도 commit으로 기록한다.
- 최신 AI 에이전트 인수인계 문서 커밋은 `0b6090f` (`docs: improve AI agent onboarding`)이다.

## 현재 코드에 존재하는 영역

- 공통: CORS, RedisTemplate, 전역 예외 응답
- Place: `Place`, `PlaceImage`, Repository, Service, DTO, Controller
- 관광공사: `TourApiClient`, Korean API adapter와 JSON/XML 응답 정규화
- 관광지 이미지: KorService2 대표/상세 이미지와 관광사진 정보 GW의 장소명 일치 이미지를 합쳐 장소별 최대 5장 노출. KorService2는 `cpyrhtDivCd=Type1`만 허용하고, 제1유형 전용인 관광사진 정보 GW는 별도 저작권 코드 필터 없이 사용
- Composition: `CompositionJob`, 업로드, 상태 조회, retry/download API, `AiGenerationClient` 인터페이스, 로컬 임시 이미지 저장소, 만료 정리 Job
- Course: `Course`, `CourseStop`, 저장/조회/삭제/공유 API
- Recommendation: `RuleBasedCourseRecommendationEngine`
- Route: `KakaoRouteClient`와 REST adapter, normalized route response
- Docker: MySQL/Redis/app을 위한 `Dockerfile`, `docker-compose.yml`, `.dockerignore`

## 현재 API Controller

구현된 Controller 경로는 다음과 같다.

- `/api/v1/places`
- `/api/v1/compositions`
- `/api/v1/courses`
- `/api/v1/share/courses`
- `/api/v1/routes/walking`
- `/api/v1/award-photos`
- `/api/v1/tourism-photos`

세부 request/response 계약은 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`를 기준으로 한다.

## 현재 검증 결과

2026-08-24 기준 `bash gradlew test` 실행 결과는 `BUILD SUCCESSFUL`이다.

Docker Desktop을 실행한 현재 환경에서 app, MySQL, Redis 컨테이너가 실행 중이다. app은 `localhost:8080`, MySQL은 호스트 `3307`, Redis는 호스트 `6379`에 연결된다. `/actuator/health`는 `UP`이며 `/api/v1/places?page=0&size=2`에서 강릉 관광지 응답을 확인했다.

관광사진 정보 GW의 `강릉` 검색 결과 1,623건 중 촬영지가 강릉인 1,610건을 장소명 기준으로 정규화했다. 전체 동기화 전 DB 장소 100개 기준으로 사진이 있는 화면 카드는 18개에서 34개로 증가했다.

`TOUR_API_SYNC_ON_STARTUP=true`로 서버를 한 번 시작하면 KorService2 강릉 목록을 마지막 페이지까지 읽어 전체 장소를 DB에 적재한다. 이 대량 동기화는 장소별 상세사진 API를 연쇄 호출하지 않으며, Type1 대표사진과 관광사진 정보 GW 매칭 사진을 장소당 최대 5장까지 저장한다. 기본값은 `false`다.

2026-08-24 실제 Docker 동기화 결과는 전체 장소 1,004개, Type1 이미지가 있어 화면 카드로 노출되는 장소 97개다. 장소 목록 API는 사진 없는 장소를 DB 조회 단계에서 제외하며 첫 페이지 20개 모두 이미지가 포함되는 것을 확인했다.

올바른 JSON으로 `POST /api/v1/courses`를 실행해 Course 생성과 원픽 포함 응답을 확인했다.

## 필요한 환경변수 및 등록 상태

실제 값은 이 문서와 Git에 기록하지 않는다. 등록 상태는 이 작업 시점의 현재 PowerShell 프로세스 환경변수를 기준으로 한다.

| 환경변수 | 용도 | 현재 등록 상태 |
|---|---|---|
| `TOUR_API_KEY` | 한국관광공사 OpenAPI 인증키 | 루트 `.env`에 등록됨. `.gitignore`로 Git 제외 |
| `TOUR_AWARD_API_KEY` | 한국관광공사 공모전 사진 API 인증키 | 미등록 시 `TOUR_API_KEY` fallback |
| `TOUR_PHOTO_GALLERY_API_KEY` | 한국관광공사 관광사진갤러리 API 인증키 | 미등록 시 `TOUR_API_KEY` fallback |
| `KAKAO_API_KEY` | Kakao REST API 인증키 | 미등록 |
| `AI_API_KEY` | 선택된 AI Provider 인증키 | Provider 미정 및 미등록 |

관련 endpoint/base URL 설정은 `TOUR_API_BASE_URL`, `KAKAO_API_BASE_URL`, `AI_BASE_URL`로 관리한다. DB/Redis 접속 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, `REDIS_HOST`, `REDIS_PORT` 환경변수를 사용한다.

## 확인된 미완성/제한

- 실제 AI Provider 구현체와 비동기 Provider polling은 없다. `AiGenerationClient` 인터페이스만 존재한다.
- Composition Job은 Provider가 연결되지 않은 현재 코드에서 실제 DONE 결과를 생성하지 않는다.
- Place 목록/상세 응답은 Redis에 서로 다른 TTL로 캐시된다. 캐시가 없거나 만료되면 DB에서만 다시 읽어 Redis에 저장하며, 화면 요청으로 관광공사 API를 호출하지 않는다.
- CourseResponse의 route 거리/시간은 아직 추천 결과에 통합되지 않는다.
- Controller 통합 테스트와 MySQL/Redis 통합 테스트는 없다.
- rate limit, 상세 metrics, Swagger/OpenAPI 문서는 아직 없다.
- Gradle test는 로컬 Gradle 실행 파일로 재실행해 통과했다. Gradle Wrapper는 배포본 재다운로드가 필요한 환경에서 네트워크 권한 문제가 발생할 수 있다.
- HTTP 400 원인은 기존 요청의 `areaCode=32` 파라미터였다. 공식 가이드 기준 강릉 필터인 `lDongRegnCd=51`, `lDongSignguCd=150`으로 수정했고, 동일 키로 `resultCode=0000`, `resultMsg=OK` 및 강릉 관광지 2건을 확인했다.
- Docker 초기 기동에서 RedisTemplate Bean 중복과 관광공사 base URL 결합 문제가 발견되었고 수정했다.
- Postman에서 JSON 속성명 따옴표가 빠진 malformed JSON은 `HttpMessageNotReadableException`으로 400 처리하도록 보완했다.
- KorService2 데이터셋의 개별 이미지 저작권 코드는 Type1/Type3가 섞여 내려온다. 현재 코드는 API 매핑, 저장, 목록, 상세 단계에서 Type1만 허용하고 기존 Type3 데이터는 재동기화 시 제거한다.
- 관광사진 정보 GW 매칭은 공백·괄호·지역 접두어·해변/해수욕장 표기를 정규화하고 검증된 별칭만 허용한다. 단순 문자열 유사도는 사용하지 않아 경포대/경포해변 같은 인접 장소의 오매칭을 방지한다.
- 관광사진 API는 명시적인 전체 장소 동기화 중에만 호출된다. 목록/상세 화면 요청은 Redis와 DB만 사용하므로 Redis 만료와 관광공사 데이터 갱신은 서로 연결되지 않는다.

이 문서는 계획이 아니라 현재 코드 확인 결과를 기록한다. 변경 시 실제 코드와 테스트를 다시 확인해 갱신한다.
