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

주요 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `TOUR_API_BASE_URL`, `TOUR_API_KEY`, `KAKAO_API_BASE_URL`, `KAKAO_API_KEY`, `AI_BASE_URL`, `AI_API_KEY`, `IMAGE_TEMP_DIR`, `IMAGE_TTL_SECONDS`.

API base path는 `/api/v1`이다. 상세 계약은 [문서 세트](MiriGangNeung_BackEnd_Codex_MD_Set/docs/CODEX_START_HERE.md)를 기준으로 한다. clone 직후에는 [루트 시작 문서](docs/CODEX_START_HERE.md)와 [AGENTS.md](AGENTS.md)를 먼저 읽는다.

처음 실행할 때는 `.env.example`을 `.env`로 복사한다. 실제 인증키는 `.env`에만 입력하며, `.env`는 Git에 커밋하지 않는다.

## Docker 실행

Docker Desktop을 실행한 뒤 MySQL, Redis와 애플리케이션을 함께 기동한다.

```powershell
docker compose up --build
```

호스트의 3306 포트가 이미 사용 중이면 다음처럼 MySQL 포트를 바꿀 수 있다. 애플리케이션 내부 연결 포트는 항상 Docker 서비스 포트 3306을 사용한다.

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

컨테이너가 정상 기동되면 `http://localhost:8080/actuator/health`와
`http://localhost:8080/api/v1/places?page=0&size=10`으로 확인한다.
