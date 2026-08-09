# Work Log

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
