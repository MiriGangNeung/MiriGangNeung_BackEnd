# MiriGangNeung_BackEnd
미리강릉 백엔드 개발 레포지토리

## 실행

Java 17 이상과 Gradle을 사용한다.

```powershell
./gradlew bootRun
./gradlew test
```

기본값은 H2 메모리 DB와 localhost Redis이며, MySQL/Redis/외부 API는 환경변수로 주입한다.

프로젝트 루트의 `.env`는 로컬 Spring Boot 실행 시 optional config로 읽으며, Docker Compose도 동일한 파일을 환경변수 입력으로 사용한다. `.env`에는 실제 secret을 넣을 수 있지만 Git에는 커밋하지 않는다.

주요 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `TOUR_API_BASE_URL`, `TOUR_API_KEY`, `TOUR_API_SYNC_ON_STARTUP`, `KAKAO_API_BASE_URL`, `KAKAO_API_KEY`, `KAKAO_PLACE_ENRICHMENT_ON_STARTUP`, `AI_BASE_URL`, `AI_API_KEY`, `IMAGE_TEMP_DIR`, `IMAGE_TTL_SECONDS`.

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

기존 DB의 KTO 장소에 Kakao 리뷰 링크를 API로 보강하려면 `KAKAO_API_KEY`를 입력하고 `.env`에 `KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`를 설정한 뒤 백엔드를 한 번 실행한다. 이 옵션은 선택 사항이며, `TOUR_API_SYNC_ON_STARTUP=true`인 전체 동기화에서는 KTO 동기화와 고정 CSV 매핑이 먼저 적용된 뒤 별도 보강 단계가 실행된다. 69개 기본 카드의 리뷰 URL은 `src/main/resources/data/kakao-place-mappings.csv`에서 읽으므로 이 매핑에는 Kakao API 호출이 필요하지 않다.

컨테이너가 정상 기동되면 `http://localhost:8080/actuator/health`와
`http://localhost:8080/api/v1/places?page=0&size=10`으로 확인한다.
