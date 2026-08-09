# CODEX START HERE

이 파일은 clone 직후 작업을 시작하는 AI 코딩 에이전트를 위한 루트 진입점이다.

## 시작 순서

1. `AGENTS.md`를 읽는다.
2. `docs/PROJECT_STATUS.md`에서 현재 구현 상태와 검증 결과를 확인한다.
3. 요청과 직접 관련된 상세 명세만 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/`에서 읽는다.
4. 관련 ADR은 `docs/adr/`에서 확인한다.
5. 실제 코드와 테스트를 먼저 확인한 뒤 수정한다.

## 상세 문서 위치

- 현재 API 계약(사람용): [`API_CONTRACT.md`](./API_CONTRACT.md)
- 현재 API 계약(기계용 OpenAPI): [`openapi.yaml`](./openapi.yaml)
- 전체 시작 문서: [`MiriGangNeung_BackEnd_Codex_MD_Set/docs/CODEX_START_HERE.md`](../MiriGangNeung_BackEnd_Codex_MD_Set/docs/CODEX_START_HERE.md)
- API 계약: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`
- DB 모델: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/07_DATA_MODEL.md`
- 백엔드 구조: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/05_BACKEND_ARCHITECTURE.md`
- 외부 API: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/11_EXTERNAL_APIS.md`
- 테스트 전략: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/14_TEST_STRATEGY.md`
- 배포: `MiriGangNeung_BackEnd_Codex_MD_Set/docs/20_DEPLOYMENT.md`
- 한국관광공사 공식 매뉴얼: `MiriGangNeung_BackEnd_Codex_MD_Set/api_manual_guide/`

## 실행 전 필수 사항

1. `.env.example`을 `.env`로 복사한다.
2. 필요한 외부 API Key를 `.env`에 입력한다.
3. `.env`는 절대 commit하지 않는다.
4. 로컬 테스트는 `./gradlew.bat test`를 사용한다.
5. Docker 실행은 `docker compose up --build`를 사용한다.

실제 API Key가 없으면 관광공사·Kakao·AI 통합 테스트는 실행할 수 없다. 가짜 Key를 만들지 않는다.
