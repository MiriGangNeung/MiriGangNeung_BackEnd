# 20 Deployment

## Local

```text
Frontend localhost
Backend localhost
MySQL localhost/container
Redis localhost/container
```

Frontend는 Backend base URL 환경변수로 접근한다.

## Environment variables

예시:

```text
SPRING_PROFILES_ACTIVE=local

DB_URL=
DB_USERNAME=
DB_PASSWORD=

REDIS_HOST=
REDIS_PORT=

TOUR_API_BASE_URL=
TOUR_API_KEY=

KAKAO_API_BASE_URL=
KAKAO_API_KEY=

AI_BASE_URL=
AI_API_KEY=

IMAGE_TEMP_DIR=
IMAGE_TTL_SECONDS=
```

실제 AI 변수는 Provider 결정 후 확정한다.

## Production

권장 구성:

```text
Frontend
   ↓
Reverse Proxy
   ↓
Spring Boot
   ├─ MySQL
   ├─ Redis
   ├─ Tourism API
   ├─ Kakao
   └─ AI Server
```

MVP에서 단일 Spring Boot instance라면 local temporary image storage를 사용할 수 있다.

다중 instance로 늘리면 object storage로 교체한다.

## Secrets

Secrets는:

- environment
- CI/CD secret
- secret manager

중 하나로 관리한다.

Git에 커밋하지 않는다.

## Health

필수:

```http
GET /actuator/health
```

Actuator를 사용할 경우 production에서 민감 endpoint를 외부에 무제한 공개하지 않는다.
