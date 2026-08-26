# MiriGangNeung_BackEnd
미리강릉 백엔드 개발 레포지토리

## 로컬 실행

가장 간단한 방법은 Docker Compose다. Docker Desktop을 먼저 실행하고, 아래 명령은 이 저장소 루트에서 실행한다.

### 1. 환경변수 준비

```bash
cp .env.example .env
```

`.env`에 사용할 키를 입력한다.

```dotenv
TOUR_API_KEY=실제_관광공사_키
KAKAO_API_KEY=실제_Kakao_REST_키
```

Mac에서 MySQL 호스트 포트 충돌이 자주 발생하면 `.env`에 호스트 포트를 지정한다. 컨테이너 내부 MySQL 연결 포트는 항상 `3306`이다.

```dotenv
MYSQL_PORT=3307
APP_PORT=8080
REDIS_PORT=6379
```

### 2. 백엔드 시작

```bash
docker compose up -d --build
docker compose logs -f app
```

정상 실행 확인:

```bash
curl http://localhost:8080/actuator/health
curl 'http://localhost:8080/api/v1/places?page=0&size=10'
```

처음 한 번 전체 강릉 장소를 DB에 채우려면 `.env`의 `TOUR_API_SYNC_ON_STARTUP=true`로 바꾼 뒤 시작한다. 동기화가 끝나면 다시 `false`로 돌려 일반 재시작 때 관광공사 API를 호출하지 않게 한다. 기존 KTO 장소의 Kakao 리뷰 링크를 자동 보강하려면 `KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`도 설정한다. 69개 기본 카드의 고정 리뷰 링크는 `src/main/resources/data/kakao-place-mappings.csv`에서 읽으므로 이 매핑에는 Kakao API 호출이 필요하지 않다.

### 3. 백엔드 종료 및 포트 충돌

```bash
docker compose down       # 컨테이너만 정리하고 DB 볼륨은 보존
docker compose ps
lsof -nP -iTCP:3307 -sTCP:LISTEN
```

포트가 이미 사용 중이면 `.env`의 `MYSQL_PORT` 또는 `APP_PORT`를 다른 값으로 바꾼다. `docker compose down -v`는 DB와 Redis 볼륨까지 삭제하므로 데이터 초기화가 필요할 때만 사용한다.

### Gradle로 앱만 실행할 때

Docker의 MySQL/Redis가 이미 실행 중이거나 `.env`에서 H2와 로컬 Redis를 설정한 경우 사용할 수 있다.

```bash
./gradlew bootRun
./gradlew test
```

API base path는 `/api/v1`이다. 상세 계약은 [API 계약](docs/API_CONTRACT.md)과 [문서 시작점](docs/CODEX_START_HERE.md)을 참고한다. 실제 인증키가 들어 있는 `.env`는 Git에 커밋하지 않는다.
