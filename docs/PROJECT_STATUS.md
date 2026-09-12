# Project Status

Last Updated: 2026-09-12 11:27 KST
Last Updated By: Codex

기준일: 2026-09-08

## Repository

- 프로젝트: `MiriGangNeung_BackEnd`
- Java/Spring Boot/Gradle 프로젝트가 루트에 있다.
- Gradle Wrapper가 포함되어 있다.
- 구현·Docker·API 검증 변경은 `46fbdd7` (`feat: add Docker deployment and verify P0 API flow`)에 기록되어 있다.
- 최신 상태 문서 커밋은 `fb78ec5` (`docs: record final Docker and API verification`)이다. 이후 문서 보완 작업은 별도 commit으로 기록한다.
- 최신 AI 에이전트 인수인계 문서 커밋은 `0b6090f` (`docs: improve AI agent onboarding`)이다.

## 현재 코드에 존재하는 영역

- `/api/v1/places` 응답에 백엔드 관리 `shortDescription`을 추가했다. Agent가 분석한 합성 가능 장소와 별개로, 현재 큐레이션 대상 23개 장소의 소개 문구를 exact-name 카탈로그에서 제공한다.

- 공통: CORS, RedisTemplate, 전역 예외 응답
- Place: `Place`, `PlaceImage`, Repository, Service, DTO, Controller
- 관광공사: `TourApiClient`, Korean API adapter와 JSON/XML 응답 정규화
- 관광지 이미지: KorService2 대표/상세 이미지와 관광사진 정보 GW의 장소명 일치 이미지를 합쳐 장소별 최대 5장 노출. KorService2는 `cpyrhtDivCd=Type1`만 허용하고, 제1유형 전용인 관광사진 정보 GW는 별도 저작권 코드 필터 없이 사용
- 이미지 전달: 동기화 시 원본을 로컬 저장소에 한 번 저장하고 카드용 JPEG 썸네일과 합성용 원본 storage key를 `place_images`에 보존. 목록·상세 요청은 저장된 URL만 반환하며 Redis에는 JSON만 저장
- Composition: 업로드, Type1 배경 원본 resolve, FastAPI Agent generation 생성, providerJobId 저장, 상태 polling, DONE 결과 임시 저장·다운로드, 오류·retry 처리
- Composition preset: `GET /api/v1/composition-models`와 `/image`로 기본 여성 AI 모델을 제공하고, `modelPresetId`를 기존 composition 입력 대체 경로로 지원
- Course: `Course`, `CourseStop`, 저장/조회/삭제/공유 API
- Course preference: 여행 타입·세부 취향 저장과 Kakao 주변 장소 추천 점수/반경 확장
- KTO-Kakao binding: `tourContentId` 기준 수기 CSV 매핑과 선택적 Kakao Local 자동 보완으로 관광지 Kakao 장소 URL을 연결
- Recommendation: `RuleBasedCourseRecommendationEngine` + `CoursePreferenceScorer`로 `types`·`companion` 조건 점수와 거리 fallback을 적용
- Route: `KakaoRouteClient`와 REST adapter, normalized route response
- Docker: MySQL/Redis/app을 위한 `Dockerfile`, `docker-compose.yml`, `.dockerignore`. MySQL 호스트 공개 포트는 `MYSQL_PORT`를 사용하며 미설정 시 3307, 컨테이너 내부 연결은 3306이다.

## 2026-08-25 코스 장소 관리 구현

- `feat/course-place-management` 브랜치에서 Kakao Local 카테고리 어댑터를 추가했다. 음식점 `FD6`, 카페 `CE7`을 백엔드에서만 조회하며 기본 반경은 2km다.
- 코스의 관광지 정거장 전체를 기준으로 주변 장소를 조회하고, Kakao 외부 장소 ID로 중복 제거한 뒤 최소 거리순으로 반환한다.
- 코스에 추가한 음식점·카페는 `course_external_places` snapshot과 `course_stops`로 MySQL에 저장한다. 전역 KTO 장소 카탈로그에는 추가하지 않는다.
- 코스 결과에서 주변 장소 추가, 원픽을 제외한 삭제, 전체 stopId 기반 순서 변경 API를 제공한다. 변경 후 도보 거리·시간·routeSegments를 다시 계산한다.
- Course 생성/조회 응답의 mock 의존을 제거하고 프론트는 반환된 `courseId`를 sessionStorage에 보관한다. 새로고침 시 백엔드에서 코스를 복원한다.
- 백엔드 API: `GET /api/v1/courses/{courseId}/nearby-places`, `POST /api/v1/courses/{courseId}/stops/external`, `DELETE /api/v1/courses/{courseId}/stops/{stopId}`, `PUT /api/v1/courses/{courseId}/stops/order`.
- 설계 결정은 [`docs/adr/2026-08-25-kakao-course-place-snapshots.md`](./adr/2026-08-25-kakao-course-place-snapshots.md)에 기록했다.
- 코스 응답의 `arrivalTime`은 현재 stop 순서 기준으로 09:00부터 체류시간과 확인된 도보 구간 시간을 누적해 계산한다. 경로가 unavailable이어도 모든 stop이 같은 09:00으로 반환되지 않는다.
- Kakao 도보 Client는 공식 Affiliate Walking endpoint(`/affiliate/walking/v1/directions`)의 `origin`, `destination`, `priority`, `summary` 계약을 사용한다. 현재 등록 키로 실제 호출한 결과는 HTTP 403이며, 코드가 아닌 Kakao 도보 API 제휴/권한 승인 문제로 `routeStatus=UNAVAILABLE`이 유지된다.

## 현재 API Controller

구현된 Controller 경로는 다음과 같다.

- `/api/v1/places` (KorService2 장소 카드와 저장된 보충 이미지 조회)
- AI 합성 요청에 `sessionId`(프론트의 익명 브라우저 세션 ID)를 함께 전달한다. 없으면 AI 서비스가 호출자 IP로 대체하는데, 그 IP는 이 서버 하나뿐이라 전 사용자가 시간당 생성 한도를 공유하게 된다. `CompositionJob`에 저장해 재시도에도 같은 값을 쓴다.
- 최초 장소 선택 API는 AI 에이전트의 배경 사진 VLM 판정을 통과한 **23개 장소**만 허용한다. 목록은 에이전트 산출물 사본인 `src/main/resources/data/viable-places.json`에서 읽는다(직접 편집 금지). 장소별 사진도 판정을 통과한 것만 노출한다.
- KTO 관광지의 Kakao 장소 연결은 `src/main/resources/data/kakao-place-mappings.csv`를 최종 기준으로 사용한다. `KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`인 경우에만 이름·좌표 기반 자동 보완을 수행하며, 최종 CSV 매핑이 우선한다.
- `/media/images/{storageKey}` (CDN으로 교체 가능한 이미지 origin endpoint)
- `/api/v1/compositions`
- `/api/v1/courses`
- `/api/v1/courses/{courseId}/nearby-places`
- `/api/v1/courses/{courseId}/stops/external`
- `/api/v1/courses/{courseId}/stops/{stopId}`
- `/api/v1/courses/{courseId}/stops/order`
- `/api/v1/share/courses`
- `/api/v1/routes/walking`

세부 request/response 계약은 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`를 기준으로 한다.

## 2026-09-08 AI 이미지 합성 Agent 연동

- `HttpAiGenerationClient`가 Agent의 `POST /v1/generations`, 상태 조회, 결과 다운로드, 취소 계약을 구현한다.
- `CompositionService.create()`는 사용자 사진을 `TemporaryImageStorage`에 저장하고 Agent Job을 생성한 뒤 `providerJobId`와 provider/model/prompt metadata를 MySQL에 저장한다.
- `CompositionPollingJob`은 기본 2초 간격으로 활성 Job을 조회한다. Agent가 DONE이면 결과 이미지 바이트를 백엔드 `TemporaryImageStorage`에 저장한 뒤 `downloadUrl`을 노출한다.
- Agent의 `error.code`, `message`, `retryable`과 `safety.status`, `reasonCode`, 경고를 백엔드 상태 응답에 정규화한다. 현재 DB에는 첫 번째 safety warning을 저장한다.
- retry API는 `FAILED && error.retryable=true`인 Job에서 기존 사용자 원본으로 새 Agent generation을 만들고 새 `providerJobId`를 저장한다.
- 배경은 `PlaceImage.copyrightCode=Type1`만 허용한다. `originalStorageKey`는 사용자 업로드 저장소가 아니라 `PlaceImageStorage.open()`으로 읽으며, 로컬 파일이 사라졌을 때 기존 `ImageAssetCacheService`로 같은 원본 URL을 복구한다.
- 현재 코스 선택 프론트 흐름의 `onePickId`는 `Place.id` UUID다. `kto-award:*`, `kto-gallery:*` 같은 표시용 ID는 UUID로 변환하지 않고 `INVALID_ONE_PICK_ID`로 거부한다.
- 선택적인 `backgroundImageUrl` multipart 필드를 추가했다. 생략하면 첫 Type1 이미지를 사용하므로 기존 요청 필드는 유지된다.
- 실제 AI Provider 선택은 Agent의 `AI_PROVIDER` 설정 책임이며 백엔드는 Provider나 모델을 하드코딩하지 않는다.

## 현재 검증 결과

2026-09-08 기준 `RUN_AI_MOCK_E2E=true`로 전체 98개 테스트를 실행해 실패 0, 오류 0으로
`BUILD SUCCESSFUL`이다. `AiCompositionMockE2ETest`는 테스트 내부의 자체 HTTP Mock Agent를 사용해
Backend의 Agent 계약 왕복을 검증한다.

현재 Mock Agent E2E는 `POST /api/v1/compositions` → providerJobId 저장 → 상태 polling → Agent
`DONE` → 결과 이미지 다운로드·백엔드 저장 → 상태 조회 → 백엔드 결과 다운로드까지 검증한다.
실제 Agent 프로세스 E2E는 별도 검증 대상이다. 기존 단색 테스트 이미지로 실제 Agent를 재실행했을 때
Agent의 정상적인 `NO_PERSON_DETECTED` 검증이 발생했으며, Backend 결함으로 분류하지 않았다.
실제 Gemini Provider 호출과 Docker 기반 전체 왕복은 아직 검증하지 않았다.

이전 로컬 Docker 검증에서는 app이 `localhost:8080`, MySQL이 호스트 `3307`, Redis가 호스트 `6379`에 연결되었고 `/actuator/health`와 장소 API 응답을 확인했다. 이번 최종 검토에서는 Docker 전체 왕복을 재실행하지 않았다.

관광사진 정보 GW의 `강릉` 검색 결과는 동기화 때만 내부 호출한다. KorService2 장소명과 매칭되는 사진만 기존 장소 카드에 보충하고, 매칭되지 않는 사진은 별도 카드로 만들지 않는다.

### CDN 호환 이미지 전달 검증 — 2026-08-24 21:31~21:34 KST

새 `feat/image-cdn` 백엔드를 8081 포트에서 기존 로컬 MySQL/Redis에 연결하고 `TOUR_API_SYNC_ON_STARTUP=true`로 한 번 동기화했다. KorService2 원본 1,004개 중 배경용 237개를 처리했고, `withImages=69`, 깨진 이미지 제외 9개였다. 화면 응답에는 69개 장소와 221개 이미지 URL이 반환됐고, 반환된 이미지 URL 221/221개가 `http://localhost:8081/media/images/...` storage URL이었다. 원본과 썸네일 파일은 총 472개(각 236세트), 약 122MB가 저장됐다.

미디어 endpoint 실측 결과는 HTTP 200, `Content-Type: image/jpeg`, 안정된 `Content-Length`, `Cache-Control: public, max-age=31536000, immutable`이었다. 프론트는 카드 이미지를 `loading="lazy"`, `decoding="async"`로 요청하고, 첫 화면 hero만 eager로 유지한다. 선택된 장소 이미지의 썸네일·원본 URL·순번은 같은 인덱스로 유지된다.

성능 harness(`samplePlaces=10`, 장소당 최대 5장, 2 passes)의 warmed list 측정값:

| 지표 | 적용 전 외부 원본 | 적용 후 로컬 origin | 변화 |
|---|---:|---:|---:|
| 이미지 평균 bytes | 515,816 | 46,912 | 90.91% 감소 |
| 이미지 총 bytes | 40,233,660 | 3,659,136 | 90.91% 감소 |
| 이미지 TTFB p50 | 67.497ms | 0.683ms | 98.99% 감소 |
| 이미지 TTFB p95 | 125.438ms | 0.927ms | 99.26% 감소 |
| 이미지 전체 응답 p50 | 143.182ms | 0.736ms | 99.49% 감소 |
| 이미지 전체 응답 p95 | 338.940ms | 1.253ms | 99.63% 감소 |
| 성공률 | 100% (39장) | 100% (39장) | 동일 |

목록 JSON은 `originalImageUrls` 병렬 배열이 추가되어 36,866 bytes에서 65,003 bytes로 76.32% 증가했다. 목록 warmed TTFB는 26.901ms에서 33.805ms로 6.904ms 증가했다. 이 측정은 글로벌 CDN 배포 전 로컬 디스크 origin과 외부 관광공사 URL을 비교한 결과이므로, 실제 CDN edge 성능과는 다를 수 있다.

`TOUR_API_SYNC_ON_STARTUP=true`로 서버를 한 번 시작하면 KorService2 강릉 목록을 마지막 페이지까지 읽어 전체 장소를 DB에 적재한다. 이 대량 동기화는 장소별 상세사진 API를 연쇄 호출하지 않으며, Type1 대표사진과 관광사진 정보 GW 매칭 사진을 장소당 최대 5장까지 저장한다. 기본값은 `false`다.

2026-08-24 실제 Docker 동기화에서 KorService2 원본 1,004개 중 배경 합성용 카테고리인 관광지·문화시설·레포츠 237개만 저장·갱신했다. 카테고리 제외는 767개였고, 기존 DB에 남아 있던 음식점 482개는 앞선 정리에서 삭제되어 이번 동기화의 `foodDeleted=0`이 됐다. 깨진 이미지 URL 10개를 제외한 뒤 기본 화면 카드 69개가 남았으며, 카테고리별로 관광지 51개·문화시설 11개·레포츠 7개다.

같은 동기화에서 PhotoGalleryService1 사진을 장소명으로 정규화했다. 이전에 검수용으로 생성된 별도 `gallery` 카드 47개를 삭제했고, 새 `gallery` 카드는 생성하지 않았다. 이후에는 매칭된 이미지만 기존 KorService2 카드에 저장한다. 실제 동기화 로그는 `galleryOnlyDeleted=47`이었다.

공개 원문 사진 엔드포인트인 `award-photos`와 `tourism-photos`는 제거했다. 공모전 API는 더 이상 사용하지 않으며, PhotoGalleryService1은 동기화 내부에서만 호출한다. 화면 요청·Redis 만료는 관광공사 API 호출을 발생시키지 않고 DB 결과를 사용한다. 동기화가 성공하면 장소 목록·상세 Redis 캐시도 무효화한다.

올바른 JSON으로 `POST /api/v1/courses`를 실행해 Course 생성과 원픽 포함 응답을 확인했다.

## 필요한 환경변수 및 등록 상태

실제 값은 이 문서와 Git에 기록하지 않는다. 등록 상태는 이 작업 시점의 현재 PowerShell 프로세스 환경변수를 기준으로 한다.

| 환경변수 | 용도 | 현재 등록 상태 |
|---|---|---|
| `TOUR_API_KEY` | 한국관광공사 OpenAPI 인증키 | 루트 `.env`에 등록됨. `.gitignore`로 Git 제외 |
| `TOUR_PHOTO_GALLERY_API_KEY` | 한국관광공사 관광사진갤러리 API 인증키 | 미등록 시 `TOUR_API_KEY` fallback |
| `KAKAO_API_KEY` | Kakao REST API 인증키 | 미등록 |
| `AI_BASE_URL` | 백엔드가 호출할 Agent base URL | 루트 `.env` 미등록 |
| `AI_API_KEY` | 백엔드와 Agent가 공유하는 선택적 API 인증값 | 루트 `.env` 미등록 |
| `AI_CONNECT_TIMEOUT` | Agent 연결 제한 시간 | 기본 `5s` |
| `AI_READ_TIMEOUT` | Agent HTTP 응답 제한 시간 | 기본 `30s` |
| `AI_POLL_DELAY` | 백엔드의 Agent 상태 polling 간격 | 기본 `2s` |
| `IMAGE_CACHE_ENABLED` | 동기화 시 원본·썸네일 저장 사용 여부 | 기본 `true` |
| `IMAGE_STORAGE_DIR` | 이미지 저장 디렉터리 | Docker에서는 `/var/lib/mirigangneung/images` |
| `IMAGE_PUBLIC_BASE_URL` | 저장 이미지 공개 base URL | 기본 `http://localhost:8080/media/images`, CDN 도메인으로 교체 가능 |

관련 endpoint/base URL 설정은 `TOUR_API_BASE_URL`, `KAKAO_API_BASE_URL`, `AI_BASE_URL`로 관리한다. DB/Redis 접속 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, `REDIS_HOST`, `REDIS_PORT` 환경변수를 사용한다.

## 확인된 미완성/제한

- 루트 `.env`의 `AI_BASE_URL`이 비어 있으면 Composition 생성은 `503 AI_NOT_CONFIGURED`를 반환한다. 실제 실행 전 Agent 주소를 등록해야 한다.
- `kto-award:*`, `kto-gallery:*` 사진 소스는 `Place.id` UUID와 Type1 저작권 근거가 없어 현재 Composition API에서 지원하지 않는다.
- 현재 schema migration 도구는 없고 기존 `JPA_DDL_AUTO=update` 방식으로 CompositionJob의 Agent 연동 컬럼을 추가한다. 운영 배포에서 명시적 migration 도구를 도입하면 해당 컬럼 migration이 필요하다.
- Place 목록/상세 응답은 Redis에 서로 다른 TTL로 캐시된다. 캐시가 없거나 만료되면 DB에서만 다시 읽어 Redis에 저장하며, 화면 요청으로 관광공사 API를 호출하지 않는다.
- Kakao REST 키가 없거나 도보 경로 호출이 실패하면 `CourseResponse.routeStatus=UNAVAILABLE`, 거리·시간 0으로 반환한다. 장소 CRUD는 계속 가능하다. 이때 응답 도착시간은 기본 시작 09:00과 장소별 체류 60분을 기준으로 순차 계산한다.
- Controller 통합 테스트와 MySQL/Redis 통합 테스트는 없다.
- rate limit, 상세 metrics, Swagger/OpenAPI 문서는 아직 없다.
- Gradle test는 로컬 Gradle 실행 파일로 재실행해 통과했다. Gradle Wrapper는 배포본 재다운로드가 필요한 환경에서 네트워크 권한 문제가 발생할 수 있다.
- HTTP 400 원인은 기존 요청의 `areaCode=32` 파라미터였다. 공식 가이드 기준 강릉 필터인 `lDongRegnCd=51`, `lDongSignguCd=150`으로 수정했고, 동일 키로 `resultCode=0000`, `resultMsg=OK` 및 강릉 관광지 2건을 확인했다.
- Docker 초기 기동에서 RedisTemplate Bean 중복과 관광공사 base URL 결합 문제가 발견되었고 수정했다.
- Postman에서 JSON 속성명 따옴표가 빠진 malformed JSON은 `HttpMessageNotReadableException`으로 400 처리하도록 보완했다.
- KorService2 데이터셋의 개별 이미지 저작권 코드는 Type1/Type3가 섞여 내려온다. 현재 코드는 API 매핑, 저장, 목록, 상세 단계에서 Type1만 허용하고 기존 Type3 데이터는 재동기화 시 제거한다.
- 관광사진 정보 GW 매칭은 공백·괄호·지역 접두어·해변/해수욕장 표기를 정규화하고 검증된 별칭만 허용한다. 단순 문자열 유사도는 사용하지 않아 경포대/경포해변 같은 인접 장소의 오매칭을 방지한다.
- 관광사진 API는 명시적인 전체 장소 동기화 중에만 호출된다. 목록/상세 화면 요청은 Redis와 DB만 사용하므로 Redis 만료와 관광공사 데이터 갱신은 서로 연결되지 않는다.
- 이미지 URL은 전체 동기화 시 HTTP 성공 응답과 `image/*` Content-Type을 확인한 뒤 원본·썸네일을 저장한다. 깨진 URL은 저장하지 않으며 유효 이미지가 없는 장소는 목록에서 제외한다. 캐시가 비활성화되면 기존 원본 URL 검증·저장 경로로 fallback한다.
- 배경 합성 장소 목록은 `nature`, `culture`, `active` 카테고리만 노출한다. 음식점 데이터는 동기화 시 관련 코스 참조와 이미지를 먼저 정리한 뒤 장소 레코드를 삭제한다.
- KTO 동기화 결과 중 판정 목록에 없는 장소는 초기 장소 선택 API에서 제외한다. `PromptPlaceCatalog`가 `/data/viable-places.json`을 읽어 관리하며, KTO 데이터가 갱신되어도 프론트에는 허용된 장소만 반환한다. 사진 단위 필터는 `PlaceCatalogSyncService.retainPortraitViableImages()`가 담당하고, 판정된 URL과 한 장도 일치하지 않으면(관광공사가 사진을 교체한 경우) 필터를 건너뛰고 경고만 남긴다.
- 코스 추천은 현재 선택된 `placeIds` 후보 안에서만 수행한다. 여행 유형·동행자 점수는 Place의 category/name/description 기반이며 운영시간·휴무일과 다일 일정은 아직 반영하지 않는다.
- 2026-08-26 추천 조건 고도화 브랜치에서 여행 유형·동행자 점수, 거리 fallback, CourseService 조건 전달 테스트를 추가했다. `day`와 `night1`의 기존 정거장 수 제한은 유지한다.
- 2026-08-26 Docker 앱을 현재 브랜치 코드로 재빌드하고 `/actuator/health`, `/api/v1/places`, 코스 생성·조회 API를 실제 호출했다. 관광지 조회와 코스 추천은 정상이고 코스 도착시간은 `09:00`, `10:00`, `11:00`으로 계산된다. Kakao 도보 endpoint는 HTTP 403으로 확인되어 현재 `UNAVAILABLE`이다.
- 코스 정거장 순서 변경 브라우저 요청을 위해 CORS 허용 메서드에 `PUT`을 추가했다.
- 2026-08-26 백엔드 전체 테스트 83개가 통과했다. 프론트 테스트 46개와 production build도 통과했다. Windows 전체 테스트에서 발생하던 이미지 저장소 파일 잠금은 테스트가 반환된 InputStream을 닫지 않던 문제를 수정해 해결했다.

이 문서는 계획이 아니라 현재 코드 확인 결과를 기록한다. 변경 시 실제 코드와 테스트를 다시 확인해 갱신한다.
