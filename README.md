# MiriGangNeung_BackEnd
미리강릉 백엔드 개발 레포지토리

강릉 관광지 카탈로그, AI 사진 합성 중계, 여행 코스 추천·경로 API를 제공하는 Spring Boot 서비스다.
프론트엔드(`MiriGangNeung_FrontEnd`)의 유일한 서버이고, AI 합성은 `MiriGangNeung_Agent`(FastAPI)에
HTTP로 위임한다. 프론트는 Agent를 직접 부르지 않는다.

## 전체 파이프라인에서의 역할

```
[FrontEnd]  POST /api/v1/compositions (photo, onePickId, aspectRatio, backgroundImageUrl, sessionId)
    │                        GET  /api/v1/compositions/{id}   (1.5초 간격 폴링)
    ▼
[BackEnd]   ① 입력 검증  ② 배경 확정  ③ Job 기록  ④ Agent 호출  ⑤ 상태 폴링  ⑥ 결과 보관
    │   POST /v1/generations  →  GET /v1/generations/{id} (2초)  →  GET .../result
    ▼
[Agent]     검증 → 분석 → 합성 → 품질검사 → 마감      (상세: MiriGangNeung_Agent/README.md)
```

**① 입력 검증** — `photo`(jpeg/png/webp) 또는 `modelPresetId`(기본 AI 모델 이미지) 중 **정확히
하나**만 받는다. `aspectRatio`는 `1:1` / `4:5` / `9:16`(기본 `4:5`). 사진의 얼굴·화질 검증은
Agent가 맡는다.

**② 배경 확정** (`CompositionBackgroundResolver`) — `onePickId`는 관광지 `Place.id`(UUID)여야 한다.
AI 합성에는 한국관광공사 **Type1**(변경 허용) 이미지만 쓸 수 있어서, 그 장소의 Type1 이미지 중
요청한 `backgroundImageUrl`과 일치하는 것을 고른다(생략하면 첫 장). 그 장소의 Type1 이미지가
아니면 `BACKGROUND_IMAGE_NOT_FOUND`, Type1이 하나도 없으면 `EDITABLE_BACKGROUND_NOT_FOUND`(422)다.
원본은 로컬 저장소에 캐시해 두고 그 바이트를 Agent로 함께 보낸다.

**③④ Job 기록·Agent 호출** — 업로드 원본을 TTL이 있는 임시 저장소에 두고 `CompositionJob`을
저장한 뒤 Agent를 호출한다. 이때 `sessionId`를 그대로 넘긴다. Agent가 이 값으로 시간당 생성
횟수를 세기 때문에, 빠지면 전 사용자가 카운터를 공유한다.

**⑤ 상태 폴링** — 스케줄러(`ai.poll-delay`, 기본 2초)가 진행 중인 Job을 Agent에 조회해
`QUEUED → ANALYZING → COMPOSITING → QUALITY_CHECK → DONE | FAILED`를 그대로 반영한다.
일시적 조회 실패(retryable)는 다음 주기에 다시 본다.

**⑥ 결과 보관** — `DONE`이 되면 결과 이미지를 내려받아 저장하고 `downloadUrl`
(`GET /api/v1/compositions/{id}/download`)을 연다. Agent가 낸 **품질 경고**
(`safety.warnings`, 예: 얼굴·배경이 다소 다르게 표현됨)는 결과를 막지 않고 그대로 전달한다.
입력·결과는 `image.ttl`(기본 24시간)이 지나면 시간당 한 번 도는 정리 작업이 삭제하고, 이후
다운로드는 `COMPOSITION_EXPIRED`(410)다. 실패한 Job은 `retryable`이 true이고 원본이 남아
있으면 `POST /api/v1/compositions/{id}/retry`로 다시 돌릴 수 있다.

## 관광지 카탈로그

- `TOUR_API_SYNC_ON_STARTUP=true`(기본 false)로 기동하면 한국관광공사 Tour API에서 강릉 관광지와
  갤러리 이미지를 동기화하고, 썸네일을 로컬에 캐시해 `/media/images/{key}`로 서빙한다. 카카오 Local API로
  장소를 보강한다.
- **노출 장소는 AI 판정으로 정한다.** `src/main/resources/data/viable-places.json`은 Agent 레포의
  `assets/places/viable_places.json`을 복사한 것이다(인물 촬영 적합도, 드론 샷 제외, 설 수 있는
  표면). 손으로 고치지 말고 Agent 레포에서 `scripts/export_place_filter.py`를 다시 돌려 교체한다.

## 주요 API

| 영역 | 경로 |
|---|---|
| 관광지 | `GET /api/v1/places`, `/places/{id}`, `/places/{id}/nearby`, `/places/{id}/related` |
| 사진 합성 | `POST /api/v1/compositions`, `GET /compositions/{id}`, `POST /compositions/{id}/retry`, `GET /compositions/{id}/download` |
| 기본 모델 | `GET /api/v1/composition-models`, `/composition-models/{id}/image` |
| 코스 | `POST /api/v1/courses`, `GET/DELETE /courses/{id}`, `GET /courses/{id}/nearby-places`, `POST /courses/{id}/stops/external`, `PUT /courses/{id}/stops/order`, `POST /courses/{id}/stops/optimize`, `POST/DELETE /courses/{id}/share`, `GET /share/courses/{token}` |
| 도보 경로 | `POST /api/v1/routes/walking` |
| 이미지 | `GET /media/images/{storageKey}` |
| 상태 | `GET /actuator/health` |

전체 계약은 [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md), OpenAPI는 `docs/openapi.yaml`.

## 배포

`main`에 병합되면 CI가 테스트를 돌리고 컨테이너 이미지를 GHCR(`ghcr.io/mirigangneung/mirigangneung_backend`)에
올린다. 서버에서는 소스를 빌드하지 않고 이미지를 받아 실행한다.

## 실행

Java 17 이상과 Gradle을 사용한다.

```powershell
./gradlew bootRun
./gradlew test
```

기본값은 H2 메모리 DB와 localhost Redis이며, MySQL/Redis/외부 API는 환경변수로 주입한다.

프로젝트 루트의 `.env`는 로컬 Spring Boot 실행 시 optional config로 읽으며, Docker Compose도 동일한 파일을 환경변수 입력으로 사용한다. `.env`에는 실제 secret을 넣을 수 있지만 Git에는 커밋하지 않는다.

주요 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `TOUR_API_BASE_URL`, `TOUR_API_KEY`, `KAKAO_API_BASE_URL`, `KAKAO_API_KEY`, `AI_BASE_URL`, `AI_API_KEY`, `AI_CONNECT_TIMEOUT`, `AI_READ_TIMEOUT`, `AI_POLL_DELAY`, `IMAGE_TEMP_DIR`, `IMAGE_TTL_SECONDS`.

코스 도보 경로는 Kakao Developers REST API의 `GET https://dapi.kakao.com/v2/routing/walk`를 사용한다. 주변 카페·식당 검색은 같은 호스트의 Kakao Local API를 별도 설정인 `KAKAO_LOCAL_API_BASE_URL=https://dapi.kakao.com`으로 사용한다. 두 기능 모두 `KAKAO_API_KEY`의 REST 키로 인증한다.

API base path는 `/api/v1`이다. 상세 계약은 [문서 세트](MiriGangNeung_BackEnd_Codex_MD_Set/docs/CODEX_START_HERE.md)를 기준으로 한다. clone 직후에는 [루트 시작 문서](docs/CODEX_START_HERE.md)와 [AGENTS.md](AGENTS.md)를 먼저 읽는다.

처음 실행할 때는 `.env.example`을 `.env`로 복사한다. 실제 인증키는 `.env`에만 입력하며, `.env`는 Git에 커밋하지 않는다.

## Docker 실행

Docker Desktop을 실행한 뒤 MySQL, Redis와 애플리케이션을 함께 기동한다.

```powershell
docker compose up --build
```

MySQL은 호스트의 기본 포트 3307로 공개한다. 다른 호스트 포트를 사용하려면 `MYSQL_PORT`를 바꾼다. 애플리케이션 내부 연결 포트는 항상 Docker 서비스 포트 3306을 사용한다.

```powershell
$env:MYSQL_PORT="3307"
docker compose up --build
```

관광공사/Kakao 연동이 필요하면 실행 전에 환경변수를 설정한다.

```powershell
$env:TOUR_API_KEY="실제_관광공사_인증키"
$env:KAKAO_API_KEY="실제_Kakao_REST_키"
docker compose up --build
```

AI 이미지 합성은 별도 `MiriGangNeung_Agent` 서버를 먼저 `AI_PROVIDER=mock` 또는 운영 Provider로
실행한 뒤 백엔드 `.env`에 연결 주소를 설정한다. 실제 Provider 선택과 모델 키는 Agent 저장소에서
관리한다.

```properties
AI_BASE_URL=http://localhost:8100
AI_API_KEY=
AI_CONNECT_TIMEOUT=5s
AI_READ_TIMEOUT=30s
AI_POLL_DELAY=2s
```

백엔드를 Docker 컨테이너로 실행하고 Agent를 호스트에서 실행한다면 `AI_BASE_URL`은 일반적으로
`http://host.docker.internal:8100`을 사용한다. 두 서비스에서 `AI_API_KEY`를 사용한다면 값이 서로
같아야 하며 실제 값은 Git에 커밋하지 않는다.

컨테이너가 정상 기동되면 `http://localhost:8080/actuator/health`와
`http://localhost:8080/api/v1/places?page=0&size=10`으로 확인한다.

사진 합성 API의 multipart 필드와 polling 순서는 [API 계약](docs/API_CONTRACT.md)을 참고한다.
