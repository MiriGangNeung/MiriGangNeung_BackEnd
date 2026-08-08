# MiriGangNeung_BackEnd
미리강릉 백엔드 개발 레포지토리

## 실행

Java 17 이상과 Gradle을 사용한다.

```powershell
./gradlew bootRun
./gradlew test
```

기본값은 H2 메모리 DB와 localhost Redis이며, MySQL/Redis/외부 API는 환경변수로 주입한다.

주요 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `TOUR_API_BASE_URL`, `TOUR_API_KEY`, `KAKAO_API_BASE_URL`, `KAKAO_API_KEY`, `AI_BASE_URL`, `AI_API_KEY`, `IMAGE_TEMP_DIR`, `IMAGE_TTL_SECONDS`.

API base path는 `/api/v1`이다. 상세 계약은 [문서 세트](MiriGangNeung_BackEnd_Codex_MD_Set/docs/CODEX_START_HERE.md)를 기준으로 한다.
