# Work Log

## 2026-09-08

### 00:29 ~ 01:19 — Backend-Agent 이미지 합성 Job 실제 연동

**Agent:** Codex
**작업 유형:** Implementation / Integration / Verification

### 작업 내용

- Agent 공식 계약에 맞춰 `AiGenerationClient`를 multipart 생성, 상태 조회, 결과 다운로드, 취소 인터페이스로 정리하고 `HttpAiGenerationClient`를 구현했다.
- `CompositionService.create()`에서 사용자 사진과 Type1 관광지 원본을 Agent에 전달하고 `providerJobId` 및 provider metadata를 저장하도록 연결했다.
- `CompositionPollingJob`을 추가해 Agent 상태를 MySQL Job에 반영하고 DONE 결과를 `TemporaryImageStorage`에 저장하도록 했다.
- Agent 오류의 code/message/retryable과 safety 경고를 백엔드 DTO로 정규화하고, retry API가 실제로 새 Agent generation을 호출하도록 구현했다.
- `PlaceImage.originalStorageKey`는 `PlaceImageStorage.open()`으로 읽고 누락 파일은 기존 `ImageAssetCacheService`로 복구한다. Type1이 아닌 이미지는 합성에 사용하지 않는다.
- 현재 프론트 흐름의 `Place.id` UUID만 onePickId로 허용하고 `kto-award:*`, `kto-gallery:*` ID는 명시적으로 거부한다.
- 기존 multipart 계약을 유지하면서 사용자가 고른 Type1 이미지를 정확히 전달할 수 있도록 선택적인 `backgroundImageUrl`을 추가했다.
- 만료 정리 작업이 입력뿐 아니라 결과 이미지도 삭제하도록 보완했다.
- API 계약, OpenAPI, AI·데이터 모델·이미지 저장 문서와 실행 환경변수를 현재 구현에 맞게 갱신했다.

### 주요 변경 파일

- `src/main/java/com/mirigangneung/infrastructure/ai/*`
- `src/main/java/com/mirigangneung/composition/*`
- `src/test/java/com/mirigangneung/infrastructure/ai/HttpAiGenerationClientTest.java`
- `src/test/java/com/mirigangneung/composition/*`
- `src/main/resources/application.yml`
- `.env.example`, `docker-compose.yml`, `README.md`
- `docs/API_CONTRACT.md`, `docs/openapi.yaml`, `docs/PROJECT_STATUS.md`
- `docs/adr/2026-09-08-ai-composition-agent-integration.md`
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/07_DATA_MODEL.md`
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/09_AI_INTEGRATION.md`
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/10_IMAGE_STORAGE.md`

### 테스트 결과

- 변경 영역 단위 테스트: `BUILD SUCCESSFUL`
- 백엔드 일반 전체 테스트: 93개 중 opt-in E2E 1개 skip, 실패 0, 오류 0, `BUILD SUCCESSFUL`
- `RUN_AI_MOCK_E2E=true` 전체 테스트: 93개 실행, 실패 0, 오류 0, `BUILD SUCCESSFUL`
- `AI_PROVIDER=mock` 실제 Agent와 Spring Boot HTTP E2E: 생성 → polling → DONE → 결과 다운로드 성공(약 2.1초)
- `git diff --check`: 통과

### 발생한 문제와 해결 방법

- multipart helper가 요청 타입을 넓게 반환해 body 메서드를 사용할 수 없던 컴파일 오류를 body request 전용 overload로 해결했다.
- Agent 응답에 백엔드 내부 DTO에 없는 metadata 필드가 있어 Jackson 역직렬화가 실패했다. 외부 응답 DTO에 unknown-field 허용을 적용해 계약의 확장 가능성을 유지했다.
- Docker Desktop Linux engine이 현재 환경에서 기동되지 않아 Docker E2E는 실행하지 못했다. Agent 의존성을 `%TEMP%`의 격리 Python 3.10 가상환경에 설치하고 mock Agent를 8100에 실행해 실제 HTTP 왕복을 검증했다. Agent repository 파일은 수정하지 않았다.

### 관련 commit

- 없음 (현재 작업 트리 변경)

## 2026-08-26

### 시간 미기록 ~ 20:30 — 코스 경로·일정 표시 문제 수정 및 Docker 재검증

**Agent:** Codex
**작업 유형:** Bug Fix / Verification

### 작업 내용

- 기존 Kakao 도보 Client의 404 원인이었던 `/v2/routing/walk`와 `start_x/start_y/end_x/end_y` 요청을 공식 Affiliate Walking endpoint와 `origin/destination/priority/summary` 계약으로 변경했다.
- `CourseScheduleCalculator`를 추가해 코스 응답의 stop 도착시간을 09:00부터 체류시간과 확인된 도보시간 기준으로 계산했다. 경로가 unavailable이어도 모든 장소가 09:00으로 반복되지 않는다.
- 프론트 코스 결과 카드 사이에 각 인접 장소의 도보시간·거리를 표시하고, 주변 장소 패널의 관광지 개수를 실제 코스 개수로 표시했다.
- 카페·음식점은 기존 계약대로 자동 추천·자동 삽입하지 않고 Kakao Local 조회 후 사용자가 선택해 추가하는 흐름을 유지했다.
- Windows 전체 테스트에서 파일 스트림이 닫히지 않아 발생한 이미지 저장소 테스트 잠금을 수정했다.

### 주요 변경 파일

- `src/main/java/com/mirigangneung/infrastructure/kakao/HttpKakaoRouteClient.java`
- `src/main/java/com/mirigangneung/course/service/CourseScheduleCalculator.java`
- `src/main/java/com/mirigangneung/course/dto/CourseResponse.java`
- `src/main/java/com/mirigangneung/course/service/CourseService.java`
- `src/main/java/com/mirigangneung/course/service/CoursePlaceService.java`
- `src/test/java/com/mirigangneung/infrastructure/kakao/HttpKakaoRouteClientTest.java`
- `src/test/java/com/mirigangneung/course/service/CourseScheduleCalculatorTest.java`
- `src/test/java/com/mirigangneung/infrastructure/image/LocalPlaceImageStorageTest.java`
- `..\\MiriGangNeung_FrontEnd\\src\\components\\organisms\\CourseResult.tsx`
- `docs/API_CONTRACT.md`, `docs/PROJECT_STATUS.md`, `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`

### 테스트 결과

- 백엔드 `./gradlew.bat --project-cache-dir C:\\Users\\chin0\\AppData\\Local\\Temp\\mirigangneung-gradle-cache test`: 83개 통과
- 프론트 `npm test -- --run`: 18개 파일 / 46개 테스트 통과
- 프론트 `npm run build`: 성공
- Docker app 이미지 재빌드 및 기동: 성공
- 실제 Docker 호출: health `UP`, 장소 68개, 코스 도착시간 `09:00`·`10:00`·`11:00` 확인
- 실제 Kakao Affiliate Walking 호출: HTTP 403. 도보 제휴/권한 승인 전까지 `routeStatus=UNAVAILABLE`로 유지됨

### 발생한 문제와 해결 방법

- 기존 도보 endpoint는 실제 호출에서 404였으므로 공식 Affiliate Walking 경로와 파라미터로 교체했다.
- 전체 테스트의 이미지 저장소 재저장 단계는 Windows 파일 잠금으로 실패했다. 테스트가 `StoredAsset.input()`을 닫도록 수정해 재실행에서 해결했다.

### 관련 commit

- `63109cf` — `fix: repair course walking route and schedule display`

## 2026-08-25 — 코스 결과 주변 장소 관리 및 실제 코스 API 연동

**Agent:** Codex  
**작업 유형:** Backend-centered Feature Implementation

### 결정·구현

- Kakao Local REST adapter를 추가하고 `FD6` 음식점·`CE7` 카페를 백엔드에서만 조회하도록 했다. 선택한 코스 관광지 정거장 전체를 기준으로 2km 내 결과를 수집하고 Kakao 외부 ID로 중복 제거·거리 정렬한다.
- `CourseExternalPlace` snapshot entity를 추가했다. 사용자가 코스에 추가한 외부 장소의 이름·주소·전화번호·URL·좌표를 MySQL에 보존하므로 새로고침·공유·외부 데이터 변경에도 코스가 유지된다.
- `nearby-places`, `stops/external`, `stops/{stopId}`, `stops/order` API를 추가했다. 원픽 삭제는 차단하고, 삭제 후 sequence를 compact하며, 추가·삭제·순서 변경 뒤 Kakao 도보 경로 합계와 segment를 재계산한다.
- CourseResponse에 `stopId`, 외부 장소 필드, `routeStatus`, `routeSegments`를 추가했다. 경로 API가 unavailable이어도 코스 장소 CRUD는 유지된다.
- 프론트는 mock course와 브라우저 Kakao REST 검색을 제거하고, 코스 생성 응답의 `courseId`를 sessionStorage에 저장한다. 코스 결과는 백엔드 조회를 사용하고 카페/음식점 탭, 거리·가까운 관광지 표시, 추가·삭제·native drag reorder를 제공한다. 지도 경로는 서버 응답을 우선 사용한다.

### 검증

- 백엔드: `bash ./gradlew test` — `BUILD SUCCESSFUL`
- 프론트: `npm test -- --run` — `15 files / 37 tests passed`
- 프론트: `npm run lint` — errors 0, 기존 `PhotoUpload.tsx` unused eslint-disable warnings 4개
- 프론트: `npm run build` — production build successful
- Gradle Wrapper cache와 프론트 local HTTP server는 sandbox 권한 제약이 있어 각각 승인된 실행으로 검증했다.

### 주요 커밋

- `fc03e27` — `feat: add Kakao local category client`
- `3e76497` — `feat: persist external course places`
- `5ee3428` — `feat: add course nearby place management APIs`
- 프론트 변경은 `feat/course-place-management` worktree에서 검증 후 별도 commit 예정

## 2026-08-24

### 20:00 ~ 진행 중 — PhotoGalleryService1을 이미지 보충 전용으로 변경

**Agent:** Codex
**작업 유형:** Behavior Change/Verification

**작업 내용:**

- PhotoGalleryService1에서 기존 KorService2 장소명과 일치하는 사진만 기존 장소의 `place_images`에 보충하도록 유지했다.
- 장소명·좌표·상세 정보가 부족한 미매칭 사진으로 별도 `gallery` 장소 카드를 생성하지 않도록 제거했다.
- 기존에 남아 있던 `KTO_PHOTO_GALLERY` 카드와 이미지는 전체 동기화 시 정리하도록 변경했다.
- 장소 목록 API의 `source=PHOTO_GALLERY` 필터, 응답 `sourceType`, 프론트의 `사진 검수` 탭과 별도 API 호출을 제거했다.
- 화면 요청은 KorService2 장소 카드와 DB에 저장된 최대 5장의 이미지만 사용한다.

**검증 결과:**

- 백엔드 핵심 테스트 통과: `PlaceCatalogSyncTransactionTest`, `TourismPhotoMatcherTest`, `PlaceRepositoryTest`, `PlaceServiceTest`
- 프론트 관련 테스트 4개 통과 및 production build 통과
- 실제 Docker 동기화에서 KorService2 1,004개를 조회했고, 배경 카테고리 237개를 저장·갱신했다. 카테고리 제외 767개, 기존 `KTO_PHOTO_GALLERY` 카드 삭제 47개, 이미지 URL 검증 제외 9개였다. 기본 목록은 69개이며 `gallery` 카드는 0개다.

### 19:00 ~ 19:27 — PhotoGalleryService1 미매칭 검수용 카드 분리 (이후 제거됨)

**Agent:** Codex
**작업 유형:** Feature Implementation/Verification

**작업 내용:**

- KorService2 장소 카드는 기존 기본 목록으로 유지하고, PhotoGalleryService1에서만 발견된 장소명 그룹을 `category=gallery`, `source=KTO_PHOTO_GALLERY` 카드로 별도 저장했다.
- 장소 목록 API에 `source=PHOTO_GALLERY` 필터와 응답 `sourceType`을 추가했다. 기본 목록에는 `nature`, `culture`, `active` KorService2 카드만 노출한다.
- PhotoGalleryService1 원문 공개 API와 사용하지 않는 공모전 사진 API, 전용 DTO·서비스·컨트롤러·테스트·설정을 제거했다. PhotoGalleryService1 내부 클라이언트와 동기화 매칭은 유지했다.
- 음식·카페명으로 명확히 판단되는 미매칭 사진은 검수용 카드로 생성하지 않는다. 관광사진 API가 정상 수집되면 기존 검수용 카드를 교체해 stale 카드가 남지 않도록 했다.
- 동기화 완료 시 장소 목록·상세 Redis 캐시를 prefix 기준으로 무효화해 DB와 화면 응답이 어긋나지 않게 했다. 관광사진 API 호출 실패 시에는 기존 검수용 카드를 보존한다.

**검증 결과:**

- `bash gradlew --no-daemon test`: `BUILD SUCCESSFUL`
- Docker 실제 동기화: KorService2 1,004개 조회, 배경 카테고리 237개 저장, 카테고리 제외 767개, 깨진 이미지 10개 제외, 기본 화면 카드 69개
- PhotoGalleryService1 미매칭 검수용 카드: 이전 47개 삭제 후 현재 47개 생성
- `GET /api/v1/places?page=0&size=100`: 69개, 모두 `sourceType=KOR_SERVICE2`, 이미지 보유
- `GET /api/v1/places?source=PHOTO_GALLERY&page=0&size=100`: 47개, 모두 `sourceType=PHOTO_GALLERY`, `category=gallery`, 이미지 보유
- `TOUR_API_SYNC_ON_STARTUP=false`로 앱을 재기동했고 `/actuator/health`가 `UP`임을 확인했다.

## 2026-08-09

### 시간 미기록 ~ 22:16 — 프론트·백엔드 연동용 API 계약 문서 정리

**Agent:** Codex
**작업 유형:** Documentation/API Contract

**작업 내용:**

- 현재 백엔드 Controller와 DTO를 기준으로 OpenAPI 3.0 YAML을 작성했다.
- 사람이 읽기 쉬운 API 호출 가이드와 실행 순서, JSON 예시, 오류 응답, 프론트 필드 변환표를 작성했다.
- 프론트의 static/mock API, 자체 walking-route API와 백엔드 실제 계약의 차이를 문서에 명시했다.
- 루트 시작 문서와 AGENTS.md에서 새 API 문서를 참조하도록 연결했다.

**주요 변경 파일:**

- `docs/openapi.yaml`
- `docs/API_CONTRACT.md`
- `docs/CODEX_START_HERE.md`
- `AGENTS.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- `git diff --check` 통과
- Controller, DTO, 프론트 API 타입·query·route 구현 대조 완료
- 코드 로직 변경 없음

**발생한 문제와 해결 방법:**

- 프론트는 static 장소/코스와 `/api/walking-route`를 사용하고, 백엔드는 `/api/v1` 실제 API를 제공하는 차이가 확인됐다. 두 계약을 혼동하지 않도록 사람용 가이드와 OpenAPI에 각각 명시했다.

**관련 commit:**

- `7416e76` — `docs: publish unified API contract`

## 2026-08-09

### 시간 미기록 ~ 15:58 — 팀원 Agent/Frontend 폴더 내용 확인 및 용어 규칙 반영

**Agent:** Codex
**작업 유형:** Documentation/Repository Inspection

**작업 내용:**

- 상위 sibling repository의 `MiriGangNeung_Agent`와 `MiriGangNeung_FrontEnd`를 확인했다.
- Agent repository의 README와 Frontend repository의 README, API 초안, 데이터 요구사항, 상태 흐름, 지도/경로/코스 장소 추가 설계·계획 문서 및 package/API 구조를 읽었다.
- `Agent 폴더`/`AI 담당 폴더`와 `Frontend 폴더`/`프론트엔드 폴더`를 각각의 repository를 가리키는 용어로 `AGENTS.md`에 추가했다.
- 두 sibling repository는 계속 읽기 전용 참고 대상으로 유지한다.

**주요 변경 파일:**

- `AGENTS.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 두 sibling repository의 실제 경로와 주요 문서/구조 확인
- 백엔드 코드 변경 없음
- sibling repository에는 수정·commit을 수행하지 않음

**발생한 문제와 해결 방법:**

- 두 폴더는 백엔드 repository 내부가 아니라 상위 경로의 별도 Git repository로 확인했다. 따라서 내용을 참고만 하고 백엔드 문서에 용어와 범위만 기록했다.
- sibling repository는 소유권 경고로 Git status를 확인하지 않았으며, 파일 내용 확인에는 영향을 주지 않았다.

**관련 commit:**

- `0d20f74` — `docs: define agent and frontend folder terminology`

## 2026-08-09

### 시간 미기록 ~ 15:52 — 팀원 참고 폴더의 백엔드 작업 제외 규칙 추가

**Agent:** Codex
**작업 유형:** Documentation

**작업 내용:**

- `MiriGangNeung_Agent`와 `MiriGangNeung_FrontEnd`를 읽기 전용 참고 폴더로 정의했다.
- 두 폴더를 수정·삭제·이동하거나 백엔드 commit에 포함하지 않는 기본 규칙을 `AGENTS.md`에 추가했다.
- 현재 checkout에 두 폴더가 존재하지 않는 사실을 `PROJECT_STATUS.md`에 기록했다.

**주요 변경 파일:**

- `AGENTS.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- repository root 실제 목록 확인
- 코드 변경 없음

**발생한 문제와 해결 방법:**

- 현재 checkout에는 요청한 두 폴더가 없어, 존재 여부를 추측하지 않고 해당 폴더가 추가될 경우에도 적용되는 규칙만 기록했다.

**관련 commit:**

- `3c00533` — `docs: exclude team reference folders from backend work`

## 2026-08-08

### 시간 미기록 ~ 17:41 — clone 후 AI 에이전트 인수인계 문서 보완

**Agent:** Codex
**작업 유형:** Documentation

**작업 내용:**

- 루트 `docs/CODEX_START_HERE.md` 진입 문서를 추가했다.
- 안전한 환경변수 템플릿 `.env.example`을 추가했다.
- README와 AGENTS.md에 clone 후 시작 순서, 환경변수, Docker 포트 충돌 대응을 명시했다.
- 현재 상태 문서에 최신 상태 문서 commit 정보를 추가했다.

**주요 변경 파일:**

- `docs/CODEX_START_HERE.md`
- `.env.example`
- `README.md`
- `AGENTS.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 문서 경로와 Git 추적 대상 확인
- `.env`는 계속 `.gitignore` 대상이며 `.env.example`에는 실제 secret이 없음
- 코드 로직 변경 없음; 기존 테스트 결과는 `BUILD SUCCESSFUL`, 2 tests passed

**발생한 문제와 해결 방법:**

- 상세 시작 문서가 루트 `docs/`가 아닌 문서 세트 하위에 있어 루트 진입 문서를 추가했다.
- clone 환경에는 실제 `.env`가 없으므로 복사 가능한 비밀값 없는 템플릿을 추가했다.

**관련 commit:**

- `0b6090f` — `docs: improve AI agent onboarding`

## 2026-08-08 — Initial backend implementation baseline

**시작 시간:** 시간 미기록
**완료 시간:** 시간 미기록
**Agent:** Codex
**작업 유형:** Implementation

- 루트 Gradle/Spring Boot 프로젝트와 Gradle Wrapper를 구성했다.
- Place, Composition Job, Course/CourseStop, RuleBased recommendation, Kakao route, temporary image storage의 기본 계층을 추가했다.
- API key는 `application.yml` 환경변수 placeholder로만 관리한다.
- `RuleBasedCourseRecommendationEngineTest`, `LocalTemporaryImageStorageTest`를 추가했다.
- 검증: `./gradlew.bat test` — BUILD SUCCESSFUL, 2 tests passed.
- 현재 작업은 아직 별도 구현 commit으로 기록되지 않았다.
- 남은 제한은 `docs/PROJECT_STATUS.md`에 기록한다.

## 2026-08-08

### 시간 미기록 ~ 17:32 — Docker/Postman 검증 결과 최종 기록 및 Git 업로드 준비

**Agent:** Codex
**작업 유형:** Verification/Documentation

**작업 내용:**

- Docker Compose 기반 app, MySQL, Redis 실행 상태와 health endpoint를 최종 확인했다.
- 관광공사 API 연동으로 Gangneung 장소 목록 응답을 확인했다.
- 올바른 JSON 요청으로 `POST /api/v1/courses` 코스 생성 응답을 확인했다.
- 잘못된 JSON은 `400 INVALID_REQUEST`로 반환되도록 공통 예외 처리를 확인했다.
- 실제 API key가 들어 있는 `.env`는 `.gitignore`로 Git 대상에서 제외되어 있음을 확인했다.

**주요 변경 파일:**

- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`
- `README.md`
- `Dockerfile`, `docker-compose.yml`, `.dockerignore`
- 관광 API, Course 요청 검증 및 공통 예외 처리 관련 Java 파일

**테스트 결과:**

- `./gradlew.bat test`: `BUILD SUCCESSFUL`, 2 tests passed
- Docker app health: `UP`
- `GET /api/v1/places?page=0&size=2`: 실제 강릉 관광지 응답 확인
- `POST /api/v1/courses`: 정상 JSON 요청 성공
- malformed JSON: `400 INVALID_REQUEST`

**발생한 문제와 해결 방법:**

- 초기 관광 API 요청은 잘못된 지역 파라미터와 URL 조합으로 실패했으며, 법정동 코드와 전체 base URL 조합으로 수정했다.
- Postman 요청의 JSON 형식 오류는 `Content-Type: application/json` 및 raw JSON 사용으로 해결했고, 서버의 parsing error 응답도 400으로 정리했다.
- Git 안전 디렉터리 경고는 저장소 경로를 `safe.directory`로 등록해 해결했다.

**관련 commit:**

- `46fbdd7` — `feat: add Docker deployment and verify P0 API flow`

### 16:13:29 ~ 16:13:55 — AI agent 작업 기록 시간 규칙 반영

**Agent:** Codex
**작업 유형:** Documentation

**작업 내용:**

- AGENTS.md에 KST 기준 작업 시간 기록 규칙을 추가했다.
- PROJECT_STATUS.md 상단에 최신 갱신 시각과 작업 agent를 추가했다.
- 기존 초기 구현 기록은 정확한 작업 시간이 확인되지 않아 `시간 미기록`으로 표시했다.

**주요 변경 파일:**

- `AGENTS.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 문서-only 변경이므로 테스트를 재실행하지 않았다.
- 직전 코드 검증 결과: `./gradlew.bat test` — `BUILD SUCCESSFUL`, 2 tests passed.

**발생한 문제와 해결 방법:**

- 기존 작업의 정확한 시작/완료 시각은 확인할 수 없어 임의로 기록하지 않고 `시간 미기록`으로 남겼다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-26

### 시간 미기록 ~ 18:05 — 코스 순서 변경 CORS 오류 수정

**Agent:** Codex
**작업 유형:** Bugfix / Verification

**작업 내용:**

- 프론트의 `PUT /api/v1/courses/{courseId}/stops/order` 요청이 브라우저 preflight에서 차단되는 원인을 확인했다.
- 백엔드 CORS 허용 메서드에 `PUT`을 추가했다.
- 기존 순서 변경 endpoint와 프론트 요청의 `stopIds` 계약은 유지했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/common/config/WebConfig.java`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- Gradle 전체 테스트 — `BUILD SUCCESSFUL`
- Docker 재빌드 후 health — `UP`
- 브라우저 CORS preflight `PUT` — HTTP `200`
- 실제 `PUT /api/v1/courses/{courseId}/stops/order` 및 재조회 — 순서 저장 성공

**발생한 문제와 해결 방법:**

- 브라우저 preflight 응답이 `403`이었고 CORS `allowedMethods`에 `PUT`이 누락되어 있었다. `PUT`을 허용 메서드에 추가했다.

**관련 commit:** `c966319` — `fix: allow course reorder CORS requests`

### 시간 미기록 ~ 18:00 — Docker 및 코스 추천 실제 API 검증

**Agent:** Codex
**작업 유형:** Verification / Documentation

**작업 내용:**

- 현재 추천 코드로 Docker app 이미지를 재빌드하고 MySQL·Redis와 함께 기동했다.
- `/actuator/health`가 `UP`인지 확인했다.
- `/api/v1/places`를 실제 호출해 관광지 목록과 실제 Place ID를 확인했다.
- `POST /api/v1/courses`로 `active`·`solo`·`day` 조건 및 `nature`·`couple`·`night1` 조건을 실제 검증했다.
- 생성된 코스를 `GET /api/v1/courses/{courseId}`로 재조회해 저장·복원 상태를 확인했다.
- Kakao 도보 API 직접 호출 결과 `404`를 확인했으며, 현재 코스 응답은 `routeStatus=UNAVAILABLE`이다.

**주요 변경 파일:**

- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- `./gradlew.bat --project-cache-dir C:\Users\chin0\AppData\Local\Temp\mirigangneung-gradle-cache test` — `BUILD SUCCESSFUL`
- Docker health — `UP`
- 관광지 조회 — 정상 응답
- 코스 생성·조회 — 정상 응답
- Kakao 도보 경로 — `UNAVAILABLE` (외부 endpoint `404`)

**발생한 문제와 해결 방법:**

- 기존 Docker 이미지가 현재 추천 코드를 포함하지 않아 `docker compose up -d --build app`로 재빌드했다.
- Kakao 키는 컨테이너에 전달되었지만 현재 코드가 호출하는 도보 endpoint가 `404`를 반환하는 문제를 확인했다. 관련 코드 수정은 별도 작업으로 남겼다.

**관련 commit:** 없음 (현재 작업 트리 변경)

### 시간 미기록 ~ 17:16 — 코스 조건 기반 RuleBased 추천 고도화

**Agent:** Codex
**작업 유형:** Implementation / Test / Documentation

**작업 내용:**

- `types` 여행 유형과 `companion` 동행자 조건을 추천 점수에 반영하는 `CoursePreferenceScorer`를 추가했다.
- 조건에 맞는 후보를 하드 필터링하지 않고 점수 내림차순, 거리순 fallback으로 정렬하도록 `RuleBasedCourseRecommendationEngine`을 수정했다.
- 기존 원픽 우선 규칙과 `day`/`night1` 정거장 수 제한은 유지했다.
- `CourseService`가 요청 조건을 추천 엔진으로 전달하는 테스트와 조건별 추천·fallback 테스트를 추가했다.
- API 계약, 프로젝트 상태 문서에 현재 추천 정책과 P0 범위를 기록했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/course/recommendation/CoursePreferenceScorer.java`
- `src/main/java/com/mirigangneung/course/recommendation/RuleBasedCourseRecommendationEngine.java`
- `src/test/java/com/mirigangneung/course/recommendation/RuleBasedCourseRecommendationEngineTest.java`
- `src/test/java/com/mirigangneung/course/service/CourseServiceTest.java`
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`
- `docs/API_CONTRACT.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 추천 엔진 테스트: `BUILD SUCCESSFUL`
- Course 패키지 테스트: `BUILD SUCCESSFUL`
- 전체 테스트: `BUILD SUCCESSFUL`, 81 tests completed, 0 failed

**발생한 문제와 해결 방법:**

- 신규 `CourseServiceTest`에서 테스트용 Entity ID가 null이라 원픽 비교 시 NPE가 발생했다. 테스트 fixture에 UUID를 주입해 해결했다.
- 기존 repository Gradle cache lock 권한 문제를 피하기 위해 별도 프로젝트 cache 경로를 사용했다.

**관련 commit:** 없음 (현재 작업 트리 변경)

### 18:10 ~ 18:20 — 강릉 전체 장소 카탈로그 동기화

**Agent:** Codex
**작업 유형:** Implementation / Verification

**작업 내용:**

- KorService2 `areaBasedList2`를 페이지 끝까지 조회해 첫 100건 제한 없이 강릉 장소 전체를 DB에 저장하는 동기화 서비스를 추가했다.
- 대량 동기화 중 장소마다 `detailImage2`를 호출하지 않도록 요약 조회 경로를 분리했다.
- 기존 Type1 이미지, KorService2 대표 이미지, 관광사진 정보 GW 매칭 이미지를 합쳐 장소당 최대 5장까지 DB에 저장한다.
- 평소 서버 시작에는 동기화를 수행하지 않고 `TOUR_API_SYNC_ON_STARTUP=true`일 때만 1회 실행하도록 구성했다.

**테스트 결과:**

- 전체 테스트: `bash gradlew test` — `BUILD SUCCESSFUL`
- 실제 Docker 전체 동기화: `fetched=1004`, `saved=1004`, `withImages=97`
- 장소 목록 API: `totalElements=97`, 첫 페이지 20개 모두 이미지 포함

**발생한 문제와 해결 방법:**

- 기존 이미지 삭제가 트랜잭션 없이 실행되어 서버가 종료됐다. `deleteByPlace`에 트랜잭션 경계를 추가하고 실제 JPA 회귀 테스트를 추가했다.
- 전체 동기화 요약 조회에 `contentTypeId=12`가 남아 149개만 저장됐다. 전체 동기화에서는 콘텐츠 유형을 보내지 않도록 분리해 1,004개 저장을 확인했다.
- 사진 없는 장소가 목록 첫 페이지를 차지하던 문제를 Type1 이미지가 존재하는 장소만 조회하는 DB 쿼리로 해결했다.

**관련 commit:**

- 현재 작업 커밋에 기록

### 18:20 ~ 18:30 — 장소 화면 조회를 Redis/DB 캐시 어사이드로 분리

**Agent:** Codex
**작업 유형:** Refactor / Verification

**작업 내용:**

- 장소 목록과 상세 조회에서 KorService2 실시간 호출을 제거했다.
- Redis hit이면 캐시 응답을 바로 반환하고, miss 또는 만료이면 DB를 조회한 뒤 목록/상세 TTL에 맞춰 Redis에 저장한다.
- 관광사진 정보 GW의 런타임 매칭도 화면 요청에서 제거해 외부 데이터 갱신은 전체 동기화 실행 시점에만 수행되도록 분리했다.
- 이전 캐시와 응답 의미가 섞이지 않도록 목록 key를 `v5`, 상세 key를 `v3`으로 변경했다.

**테스트 결과:**

- PlaceService 관련 테스트: `BUILD SUCCESSFUL`

**관련 commit:**

- 현재 작업 커밋에 기록

### 18:44 ~ 18:51 — 배경 합성 장소 필터와 깨진 이미지 정리

**Agent:** Codex
**작업 유형:** Bugfix / Data cleanup / Verification

**작업 내용:**

- 동기화 시 이미지 URL의 HTTP 상태와 Content-Type을 검사해 실제 이미지 응답만 저장하도록 변경했다.
- 배경 합성에 적합한 관광지, 문화시설, 레포츠만 목록에 노출하고 맛집·숙박·쇼핑·행사 등은 제외했다.
- 음식점 장소는 관련 코스 참조와 이미지를 먼저 삭제한 뒤 장소 데이터도 DB에서 삭제하도록 트랜잭션 정리 서비스를 추가했다.
- 목록 조건 변경에 맞춰 Redis cache key를 `v7`로 변경했다.

**실제 Docker 검증 결과:**

- 관광공사 원본: 1,004개
- 카테고리 제외: 767개
- 음식점 DB 삭제: 482개
- 배경 후보 저장·갱신: 237개
- 깨진 이미지 URL 제외: 9개
- 최종 화면 카드: 69개 (`nature` 51, `culture` 11, `active` 7)
- 음식점 DB 잔여: 0개
- 강릉 올림픽파크: 유효 이미지가 없어 목록에서 제외
- 전체 테스트: `BUILD SUCCESSFUL`

## 2026-08-08

### 16:24:11 ~ 16:27:56 — Docker 실행 구성 및 관광공사 조회 경로 보완

**Agent:** Codex
**작업 유형:** Implementation

**작업 내용:**

- MySQL, Redis, Spring Boot app을 포함한 Docker Compose 구성을 추가했다.
- Java 17 기반 multi-stage Dockerfile과 `.dockerignore`를 추가했다.
- 관광공사 주변 조회를 `locationBasedList2`로 분리했다.
- 장소 상세 조회에서 DB miss 시 관광공사 `detailCommon2`를 호출하도록 보완했다.
- README에 Docker 실행 및 환경변수 예시를 추가했다.

**주요 변경 파일:**

- `Dockerfile`
- `docker-compose.yml`
- `.dockerignore`
- `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiClient.java`
- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- `README.md`
- `docs/PROJECT_STATUS.md`

**테스트 결과:**

- `docker compose config`: 성공
- 로컬 Gradle `test`: `BUILD SUCCESSFUL`, 2 tests passed
- Docker image build/up: Docker Desktop Linux engine 미실행으로 미실행
- 로컬 HTTP smoke test: Gradle project cache 권한 문제로 미실행

**발생한 문제와 해결 방법:**

- `gradlew`가 Gradle 배포본을 다운로드하려다 네트워크 권한 오류가 발생했다. 캐시된 로컬 Gradle 실행 파일로 테스트를 실행해 컴파일과 테스트를 검증했다.
- Docker API pipe가 없어 이미지 build가 시작되지 않았다. Docker Desktop 실행 후 재시도해야 한다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-08

### 16:32:03 ~ 16:32:30 — 외부 API Key 등록 상태 문서화

**Agent:** Codex
**작업 유형:** Documentation/Security

**작업 내용:**

- 필요한 외부 API Key와 용도를 `docs/PROJECT_STATUS.md`에 추가한다.
- 실제 인증키 값은 저장소, 코드, 문서, 환경설정 파일에 기록하지 않는다.
- 현재 PowerShell 프로세스의 환경변수 등록 여부를 확인한다.

**주요 변경 파일:**

- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 문서 및 환경변수 상태 확인 작업으로 코드 테스트는 실행하지 않았다.

**발생한 문제와 해결 방법:**

- `TOUR_API_KEY`를 포함한 외부 Key 환경변수는 현재 프로세스에 등록되어 있지 않았다. 사용자가 제공한 값은 파일이나 persistent environment에 저장하지 않는다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-08

### 16:35:53 ~ 16:38:59 — 관광공사 인증키 환경설정 및 연동 검증

**Agent:** Codex
**작업 유형:** Configuration/Verification

**작업 내용:**

- 프로젝트 루트 `.env`에 `TOUR_API_KEY` 환경변수를 등록했다.
- Spring Boot가 로컬 실행에서도 루트 `.env`를 optional config로 읽도록 설정했다.
- `.env`가 `.gitignore`에 의해 Git에서 제외되는지 확인했다.
- URL encoded service key를 Client에서 한 번만 인코딩하도록 처리했다.
- 관광공사 `areaBasedList2` read-only 호출을 시도했다.

**주요 변경 파일:**

- `.env` (Git ignored)
- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `src/main/resources/application.yml`
- `README.md`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- Gradle test: `BUILD SUCCESSFUL`, 2 tests passed
- 관광공사 API 호출: HTTP 400, 성공 응답 확인 실패
- 인증키 값은 출력하지 않음

**발생한 문제와 해결 방법:**

- API 요청이 HTTP 400을 반환했다. 키 또는 공공데이터포털 요청 인코딩/계정 상태를 추가 확인해야 한다.
- `.env`는 Git ignore 상태임을 확인했다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-08

### 시간 미기록 ~ 16:49:04 — 관광공사 400 오류 원인 수정 및 실응답 검증

**Agent:** Codex
**작업 유형:** Bugfix/Verification

**작업 내용:**

- 한국관광공사 공식 가이드의 지역 필터 파라미터를 재확인했다.
- 기존 `areaCode=32`를 제거하고 강릉의 `lDongRegnCd=51`, `lDongSignguCd=150`을 사용하도록 수정했다.
- 동일 인증키로 공식 `areaBasedList2` 호출을 재검증했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 관광공사 API: `resultCode=0000`, `resultMsg=OK`, 강릉 관광지 2건 확인
- Gradle test: `BUILD SUCCESSFUL`, 2 tests passed

**발생한 문제와 해결 방법:**

- 기존 요청에 공식 가이드에 정의되지 않은 `areaCode=32`가 포함되어 HTTP 400이 발생했다.
- 공식 가이드의 법정동 시도/시군구 코드로 변경해 정상 응답을 확인했다.
- 정확한 작업 시작 시각은 확인하지 못해 `시간 미기록`으로 기록했다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-08

### 시간 미기록 ~ 17:12:26 — Docker Desktop 기동 및 컨테이너 smoke test

**Agent:** Codex
**작업 유형:** Deployment/Verification

**작업 내용:**

- Docker Desktop을 실행했다.
- MySQL, Redis, Spring Boot app 컨테이너를 Compose로 기동했다.
- 호스트 3306 포트 충돌로 MySQL 외부 포트를 3307로 사용했다.
- RedisTemplate Bean 중복으로 앱이 종료되는 문제를 `@Primary`로 해결했다.
- 관광공사 RestClient의 base URL 결합 문제를 수정했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/common/config/RedisConfig.java`
- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- Docker image build: 성공
- MySQL: healthy
- Redis: healthy
- App: running, `localhost:8080`
- `/actuator/health`: `{"status":"UP"}`
- `/api/v1/places?page=0&size=2`: 강릉 관광지 2건 응답 확인
- Gradle test: `BUILD SUCCESSFUL`, 2 tests passed

**발생한 문제와 해결 방법:**

- 호스트 3306이 사용 중이어서 Compose 실행 시 3307로 매핑했다.
- Spring Boot 기본 `stringRedisTemplate`과 사용자 정의 Bean이 중복되어 `@Primary`를 추가했다.
- RestClient가 base URL의 `/B551011/KorService2` 경로를 안정적으로 결합하지 않아 전체 URL을 직접 구성하도록 변경했다.
- 정확한 작업 시작 시각은 확인하지 못해 `시간 미기록`으로 기록했다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-08

### 시간 미기록 ~ 17:24:14 — Postman Course 요청 오류 확인 및 정상 요청 검증

**Agent:** Codex
**작업 유형:** Bugfix/Verification

**작업 내용:**

- Course 요청 400의 원인을 확인하기 위해 running Docker app에서 요청을 재현했다.
- JSON 속성명 따옴표가 누락된 malformed JSON임을 로그로 확인했다.
- `HttpMessageNotReadableException`을 공통 400 응답으로 처리하도록 수정했다.
- 올바른 JSON 직렬화 요청으로 Course 생성과 원픽 포함 응답을 확인했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/common/error/GlobalExceptionHandler.java`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- Gradle test: `BUILD SUCCESSFUL`, 2 tests passed
- Docker app health: `UP`
- 올바른 `POST /api/v1/courses`: Course 생성 성공
- malformed JSON: 400 `INVALID_REQUEST`

**발생한 문제와 해결 방법:**

- 요청 JSON이 `{duration:day}`처럼 속성명 따옴표 없이 전송되어 Jackson parsing error가 발생했다.
- Postman에서 `Body → raw → JSON`과 `Content-Type: application/json`을 사용하도록 안내했고, 서버도 malformed JSON을 400으로 반환하도록 보완했다.
- 정확한 작업 시작 시각은 확인하지 못해 `시간 미기록`으로 기록했다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)
## 2026-08-16

### 시간 미기록 ~ 23:04 — 관광 사진 PR 리뷰 반영

**Agent:** Codex
**작업 유형:** Implementation / Documentation

**작업 내용:**

- PR 브랜치 `develop`의 리뷰 항목을 확인하고 기존 API 계약 문서를 복구했다.
- 기존 `KoreanTourApiClient`의 서비스 키와 query parameter 인코딩을 신규 관광 사진 Client와 같은 URI-safe 방식으로 통일했다.
- `+`가 포함된 서비스 키 회귀 테스트를 추가했다.
- Award/PhotoGallery API의 현재 페이지 기준 근사 페이지네이션 정책과 단일 이미지 URL 동작을 API 문서에 명시했다.
- `PROJECT_STATUS.md`에 신규 API와 관련 환경변수를 반영했다.

**주요 변경 파일:**

- `docs/API_CONTRACT.md`
- `docs/openapi.yaml`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`
- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClientTest.java`

**테스트 결과:**

- Gradle Wrapper 실행은 Gradle 배포판 다운로드 권한 오류로 미실행.
- 로컬 Gradle로 재시도했으나 현재 환경에서 Spring Boot Gradle Plugin 의존성 다운로드가 차단되어 미실행.
- 코드 및 문서 변경 후 전체 테스트는 커밋 전에 재실행해야 한다.

**발생한 문제와 해결 방법:**

- 기존 작업 트리에서 PR 브랜치 전환 중 Windows 파일 교체 오류가 발생했다. 원격 `origin/develop` 기준 별도 worktree `.pr-develop`를 사용해 작업 파일 혼합을 피했다.
- 원격 네트워크/의존성 접근 제한으로 테스트가 완료되지 않았다.

**관련 commit:** `c445d0b` — `fix: address tourism photo API review comments`

## 2026-08-24

### 시간 미기록 ~ 13:44 — 장소별 다중 이미지와 Type1 저작권 필터 적용

**Agent:** Codex
**작업 유형:** Implementation / Bugfix / Verification

**작업 내용:**

- KorService2 `detailImage2` 응답을 대표 이미지와 합쳐 장소별 최대 5장까지 저장·응답하도록 확장했다.
- `cpyrhtDivCd=Type1` 이미지만 API 매핑, DB 저장, 목록, 상세 응답에서 허용하도록 방어 필터를 적용했다.
- 기존 Type3 이미지가 캐시에서 다시 노출되지 않도록 Place 목록/상세 cache key version을 변경했다.
- 신규 Place 생성 시 관광공사 수정일을 생성자에서 보존해 최신 `develop`의 신규 엔티티 처리 방식과 기존 계약을 함께 유지했다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- `src/main/java/com/mirigangneung/place/domain/Place.java`
- `src/main/java/com/mirigangneung/place/domain/PlaceImage.java`
- `src/main/java/com/mirigangneung/place/dto/PlaceResponse.java`
- `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- `src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClientTest.java`
- `src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java`

**테스트 결과:**

- 관련 테스트: `BUILD SUCCESSFUL`
- 전체 테스트: `bash gradlew test` — `BUILD SUCCESSFUL`

**발생한 문제와 해결 방법:**

- 로컬 `develop`이 원격보다 5개 commit 뒤여서 최신 `origin/develop`에서 기능 브랜치를 생성했다.
- `PlaceService` 충돌은 원격의 신규 엔티티 처리 구조를 유지하면서 Type1 필터와 원본 수정일 초기화를 함께 적용해 해결했다.

**관련 commit:**

- 현재 PR commit에 기록

## 2026-08-24

### 14:12 ~ 14:30 — 관광사진 장소명 정규화 및 장소 카드 이미지 보강

**Agent:** Codex
**작업 유형:** Implementation / Verification

**작업 내용:**

- 관광사진 정보 GW의 강릉 검색 결과를 장소명별로 묶고 기존 Place 이름과 안전하게 연결하는 정규화기를 추가했다.
- 공백, 괄호 부가명, 행정구역 접두어, 해변/해수욕장 표기를 통일하고 확인된 별칭만 적용했다.
- 관광사진 전체 목록을 프로세스 내에서 1시간 캐시하고, 일치한 장소의 기존 이미지 뒤에 최대 5장까지 보충했다.
- 관광사진 API 실패 시 기존 저장 이미지로 계속 응답하도록 fallback을 유지했다.
- Place 목록 응답 변경에 맞춰 Redis cache key를 `v4`로 올렸다.

**주요 변경 파일:**

- `src/main/java/com/mirigangneung/place/service/PlaceNameNormalizer.java`
- `src/main/java/com/mirigangneung/place/service/TourismPhotoMatcher.java`
- `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- `src/test/java/com/mirigangneung/place/service/PlaceNameNormalizerTest.java`
- `src/test/java/com/mirigangneung/place/service/TourismPhotoMatcherTest.java`
- `src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java`
- `docs/PROJECT_STATUS.md`
- `docs/WORK_LOG.md`

**테스트 결과:**

- 정규화/매칭/PlaceService 관련 테스트: `BUILD SUCCESSFUL`
- Spring Application Context 테스트: `BUILD SUCCESSFUL`
- 실제 API와 MySQL/Redis 연결 smoke test: HTTP 200
- 현재 Place 100개 기준 화면 노출 카드: 18개 → 34개, 16개 증가
- 신규 카드 이미지 수: 장소별 1~5장

**발생한 문제와 해결 방법:**

- `TourismPhotoMatcher`의 운영/테스트 생성자 중 Spring 주입 대상을 명시하지 않아 Application Context가 실패했다. 운영 생성자에 `@Autowired`를 지정해 해결했다.
- Docker 이미지 재빌드가 외부 base image metadata 조회 중 제한 시간에 걸렸다. 동일 코드로 8081 임시 서버를 실행해 실제 MySQL/Redis/API 통합 동작을 검증했다.
- 관광사진을 100건씩 17회 조회하면 최초 요청이 느려졌다. 실제 API가 2,000건 요청을 정상 처리하는 것을 확인하고 한 번의 요청으로 현재 1,623건을 가져오도록 조정했다.

**관련 commit:**

- 없음 (현재 작업 트리 변경)

## 2026-08-24

### 21:09 ~ 21:34 — 관광 이미지 CDN 호환 origin 저장 및 lazy delivery

**Agent:** Codex
**작업 유형:** Performance / Implementation / Verification

**작업 내용:**

- 이미지 원본 URL의 SHA-256 기반 storage key를 만들고, 동기화 시 원본과 카드용 JPEG 썸네일을 로컬 저장소에 한 번 저장하도록 구현했다.
- `GET /media/images/{storageKey}` 스트리밍 endpoint에 `Content-Type`, `Content-Length`, `public, max-age=31536000, immutable`을 적용했다.
- `PlaceImage`에 원본·썸네일 storage key와 byte metadata를 저장하고, 목록·상세 응답에 썸네일 `imageUrls`, 병렬 원본 `originalImageUrls`, source URL metadata를 추가했다.
- 레거시 이미지 행은 기존 관광공사 URL로 fallback하며, 이미지 binary는 Redis에 넣지 않는다.
- 프론트 카드 이미지는 lazy/async로 바꾸고, 첫 화면 hero는 eager로 유지했다. 원픽 후보의 썸네일·원본·선택 순번은 동일 인덱스로 유지한다.
- Docker에 `image_data` volume과 이미지 캐시 설정 환경변수를 추가했다.

**검증 결과:**

- 백엔드 `bash gradlew --no-daemon test` — `BUILD SUCCESSFUL`
- 프론트 `npm test -- --run` — 12 files / 28 tests passed
- 프론트 `npm run lint` — errors 0, 기존 warning 4개
- 프론트 `npm run build` — Vite build success
- 8081 임시 서버에서 기존 MySQL/Redis 연결 및 전체 동기화 성공: fetched 1,004 / category excluded 767 / saved 237 / withImages 69 / broken excluded 9
- 반환 이미지 221/221개가 로컬 storage URL이고 media endpoint 헤더·바이트 응답을 확인했다.

**성능 측정:**

- 동일 harness 조건(`samplePlaces=10`, 장소당 최대 5장, 2 passes)에서 이미지 평균 크기 515,816 bytes → 46,912 bytes로 90.91% 감소했다.
- 이미지 TTFB p50/p95는 67.497/125.438ms → 0.683/0.927ms, 전체 응답 p50/p95는 143.182/338.940ms → 0.736/1.253ms로 감소했다.
- 목록 JSON은 원본 URL 배열 추가로 36,866 → 65,003 bytes가 됐고, warmed list TTFB는 26.901 → 33.805ms가 됐다.
- 위 결과는 글로벌 CDN edge가 아닌 로컬 디스크 origin과 외부 관광공사 원본을 비교한 수치다.

**주요 파일:**

- `src/main/java/com/mirigangneung/infrastructure/image/*`
- `src/main/java/com/mirigangneung/place/service/PlaceCatalogSyncService.java`
- `src/main/java/com/mirigangneung/place/dto/PlaceResponse.java`
- `src/main/resources/application.yml`
- `docker-compose.yml`
- 프론트 `src/lib/placesApi.ts`, `src/lib/placeImages.ts`, `src/components/atoms/ImageSlot.tsx`

**관련 commit:**

- 구현 완료 후 backend/frontend 각각 별도 commit으로 기록 예정

### 22:05 ~ 22:07 — Docker Compose MySQL 호스트 포트 기본값 보완

**Agent:** Codex
**작업 유형:** Bugfix / Configuration / Verification

**작업 내용:**

- CDN worktree에는 백엔드 본체의 로컬 `.env`가 자동으로 복사되지 않아 `MYSQL_PORT`가 비어 있었고, Compose fallback인 호스트 3306으로 기동을 시도하는 문제가 있었다.
- MySQL 포트 매핑을 `${MYSQL_PORT:-3307}:3306`으로 변경했다. 환경변수가 있으면 지정한 호스트 포트를 사용하고, 없으면 로컬 기본값 3307을 사용한다.
- 컨테이너 간 JDBC 연결은 `mysql:3306`을 유지했다. 호스트 공개 포트와 Docker 네트워크 내부 포트를 혼동하지 않도록 README와 `.env.example`을 갱신했다.

**검증 결과:**

- `docker compose config --format json` — 환경변수 미설정 시 published port `3307`
- `MYSQL_PORT=3310 docker compose config --format json` — published port `3310`
- `bash gradlew --no-daemon test` — `BUILD SUCCESSFUL`

**관련 commit:**

- 없음 (현재 작업 트리 변경)
