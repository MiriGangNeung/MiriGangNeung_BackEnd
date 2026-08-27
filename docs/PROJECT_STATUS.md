# Project Status

Last Updated: 2026-08-27 12:55 KST
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
- 이미지 전달: 동기화 시 원본을 로컬 저장소에 한 번 저장하고 카드용 JPEG 썸네일과 합성용 원본 storage key를 `place_images`에 보존. 목록·상세 요청은 저장된 URL만 반환하며 Redis에는 JSON만 저장
- Composition: `CompositionJob`, 업로드, 상태 조회, retry/download API, `AiGenerationClient` 인터페이스, 로컬 임시 이미지 저장소, 만료 정리 Job
- Course: `Course`, `CourseStop`, 저장/조회/삭제/공유 API
- Recommendation: `RuleBasedCourseRecommendationEngine`
- Nearby recommendation: 코스에 저장된 여행 타입·동행 유형을 바탕으로 Kakao 주변 장소에 설명 가능한 0~100점과 추천 이유를 계산하고 추천순/거리순을 제공
- Route: `KakaoRouteClient`와 REST adapter, normalized route response
- Docker: MySQL/Redis/app을 위한 `Dockerfile`, `docker-compose.yml`, `.dockerignore`. MySQL 호스트 공개 포트는 `MYSQL_PORT`를 사용하며 미설정 시 3307, 컨테이너 내부 연결은 3306이다.

## 2026-08-26 KTO 동기화 후 고정 Kakao URL 매핑

- `src/main/resources/data/kakao-place-mappings.csv`에 현재 화면에 노출되는 69개 KTO 장소의 `tourContentId`별 Kakao ID·상세 URL을 저장했다. 68개는 URL을 지정하고 `강릉 명주동 거리`는 빈 매핑으로 명시해 `NULL`을 유지한다.
- `TOUR_API_SYNC_ON_STARTUP=true`이면 KTO 장소 동기화 후 CSV 매핑 runner가 기존 `places` 행만 일괄 upsert한다. 매핑은 idempotent하며 새 카드를 생성하지 않고, DB에 아직 없는 행은 로그의 `missing`으로 남긴다.
- 고정 매핑 적용에는 Kakao API 호출이 필요하지 않다. Kakao Local 자동 보강은 `KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`일 때만 별도로 실행되고, CSV 매핑이 그 뒤에 적용되어 수동값이 최종 기준이 된다.

## 2026-08-25 코스 장소 관리 구현

- `feat/course-place-management` 브랜치에서 Kakao Local 카테고리 어댑터를 추가했다. 음식점 `FD6`, 카페 `CE7`을 백엔드에서만 조회하며 기본 반경은 2km다.
- 코스의 관광지 정거장 전체를 기준으로 주변 장소를 조회하고, Kakao 외부 장소 ID로 중복 제거한 뒤 최소 거리순으로 반환한다.
- 코스에 추가한 음식점·카페는 `course_external_places` snapshot과 `course_stops`로 MySQL에 저장한다. 전역 KTO 장소 카탈로그에는 추가하지 않는다.
- 코스 결과에서 주변 장소 추가, 원픽을 제외한 삭제, 전체 stopId 기반 순서 변경 API를 제공한다. 변경 후 도보 거리·시간·routeSegments를 다시 계산한다.
- Course 생성/조회 응답의 mock 의존을 제거하고 프론트는 반환된 `courseId`를 sessionStorage에 보관한다. 새로고침 시 백엔드에서 코스를 복원한다.
- 백엔드 API: `GET /api/v1/courses/{courseId}/nearby-places`, `POST /api/v1/courses/{courseId}/stops/external`, `DELETE /api/v1/courses/{courseId}/stops/{stopId}`, `PUT /api/v1/courses/{courseId}/stops/order`.
- 설계 결정은 [`docs/adr/2026-08-25-kakao-course-place-snapshots.md`](./adr/2026-08-25-kakao-course-place-snapshots.md)에 기록했다.

## 2026-08-27 이슈 #13 장소 맞춤 추천 구현

- 코스 생성 시 여행 타입 최대 2개와 동행 유형을 `courses.travel_types`, `courses.companion`에 저장하고 Course 응답에도 반환한다. 기존 선호값이 없는 코스는 빈 값으로 호환된다.
- 주변 장소 API는 `sort=recommended`를 기본으로 사용한다. 추천 점수는 거리 40점·여행 타입 30점·동행 유형 20점·정보 완성도 10점이며, 장소명·카테고리명·주소·좌표·Kakao URL만 사용한다.
- `recommendationScore`와 최대 3개의 `recommendationReasons`를 반환한다. 리뷰·별점·사진·인기도를 임의로 만들지 않으며, 장소는 사용자가 추가 버튼을 눌렀을 때만 코스 snapshot으로 저장한다.
- `sort=distance`는 기존 거리순 동작을 유지한다. 프론트 장소 추가 패널은 추천순을 기본으로 보여주고 거리순으로 전환할 수 있다.
- 관련 브랜치: backend `feat/issue-13-place-recommendation`, frontend `feat/issue-13-place-recommendation`

## 현재 API Controller

구현된 Controller 경로는 다음과 같다.

- `/api/v1/places` (KorService2 장소 카드와 저장된 보충 이미지 조회)
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

## 현재 검증 결과

2026-08-27 기준 백엔드 `bash gradlew test`는 `BUILD SUCCESSFUL`, 프론트 전체 Vitest는 30 files/75 tests 통과, `npm run build`는 성공했다. 프론트 lint는 오류 0건이며 기존 경고 4건이 남아 있다.

Docker Desktop을 실행한 현재 환경에서 app, MySQL, Redis 컨테이너가 실행 중이다. app은 `localhost:8080`, MySQL은 호스트 `3307`, Redis는 호스트 `6379`에 연결된다. `/actuator/health`는 `UP`이며 `/api/v1/places?page=0&size=2`에서 강릉 관광지 응답을 확인했다.

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
| `AI_API_KEY` | 선택된 AI Provider 인증키 | Provider 미정 및 미등록 |
| `IMAGE_CACHE_ENABLED` | 동기화 시 원본·썸네일 저장 사용 여부 | 기본 `true` |
| `IMAGE_STORAGE_DIR` | 이미지 저장 디렉터리 | Docker에서는 `/var/lib/mirigangneung/images` |
| `IMAGE_PUBLIC_BASE_URL` | 저장 이미지 공개 base URL | 기본 `http://localhost:8080/media/images`, CDN 도메인으로 교체 가능 |

관련 endpoint/base URL 설정은 `TOUR_API_BASE_URL`, `KAKAO_API_BASE_URL`, `AI_BASE_URL`로 관리한다. DB/Redis 접속 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, `REDIS_HOST`, `REDIS_PORT` 환경변수를 사용한다.

## 확인된 미완성/제한

- 실제 AI Provider 구현체와 비동기 Provider polling은 없다. `AiGenerationClient` 인터페이스만 존재한다.
- Composition Job은 Provider가 연결되지 않은 현재 코드에서 실제 DONE 결과를 생성하지 않는다.
- Place 목록/상세 응답은 Redis에 서로 다른 TTL로 캐시된다. 캐시가 없거나 만료되면 DB에서만 다시 읽어 Redis에 저장하며, 화면 요청으로 관광공사 API를 호출하지 않는다.
- Kakao REST 키가 없거나 도보 경로 호출이 실패하면 `CourseResponse.routeStatus=UNAVAILABLE`, 거리·시간 0으로 반환한다. 장소 CRUD는 계속 가능하다.
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
- KTO 장소의 사진·기본정보는 유지하면서 Kakao Local 키워드 검색으로 정규화 이름을 매칭해 `kakaoPlaceId`·`kakaoPlaceUrl`을 저장한다. 코스 응답은 이 URL을 원본 관광지에도 전달하므로 프론트의 기존 Kakao iframe 리뷰 버튼을 재사용할 수 있다. `KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`는 기존 DB를 한 번 보강하는 옵션이다.
- KTO 장소의 Kakao URL은 고정 CSV 매핑(`tourContentId` 기준)을 동기화 후 적용한다. CSV에 없는 신규/미매핑 장소만 선택적 Kakao Local 자동 보강 대상으로 남기며, CSV의 빈 값은 의도적인 `NULL` 억제값이다.

이 문서는 계획이 아니라 현재 코드 확인 결과를 기록한다. 변경 시 실제 코드와 테스트를 다시 확인해 갱신한다.
