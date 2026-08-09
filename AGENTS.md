# AGENTS.md — MiriGangNeung Backend

clone 직후에는 이 파일과 함께 루트 `docs/CODEX_START_HERE.md`를 읽는다. 상세 명세의 실제 위치는 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/`이며, 루트 시작 문서는 해당 문서 세트로 연결되는 진입점이다.

이 문서는 Codex, Orca, Antigravity 등 서로 다른 코딩 에이전트가 이 저장소에서 작업할 때 따라야 하는 공통 지침이다. 상세 명세를 복사하지 않고, 현재 상태 문서와 영역별 원문 문서를 참조한다.

## 1. 프로젝트 개요

- 프로젝트 이름: `MiriGangNeung_BackEnd` / 미리강릉 백엔드
- 목적: 강릉 관광지 데이터, 사용자 사진 합성 Job, 규칙 기반 여행 코스, 지도 경로와 익명 코스 공유를 제공하는 백엔드
- 백엔드 역할: 별도 프론트엔드와 REST API로 통신하며, 관광공사·Kakao 등 외부 API를 내부 Domain/DTO로 정규화하고 MySQL/Redis와 임시 이미지 저장소를 연결한다.

위 내용은 루트 `README.md`와 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/01_PROJECT_OVERVIEW.md`, `04_REQUIREMENTS_AND_SCOPE.md`, `05_BACKEND_ARCHITECTURE.md`에서 확인한다.

## 2. 확정된 기술 스택

현재 `build.gradle`, `application.yml`, 기존 설계 문서에서 확인되는 기술만 사용한다.

- Java 17 toolchain
- Spring Boot 4.0.7
- Gradle
- Spring Web / Validation
- Spring Data JPA
- MySQL driver 및 MySQL 영속 저장소
- Redis 및 Spring Data Redis
- H2: 현재 로컬 기본 datasource와 테스트용 runtime fallback
- 한국관광공사 OpenAPI
- Kakao REST API adapter
- 로컬 임시 이미지 저장소 추상화

AI Provider, 모델, object storage 제품 및 기타 외부 서비스는 선택하지 않는다. AI 연동은 `AiGenerationClient` 인터페이스 범위만 유지한다.

## 3. 작업 시작 전 필수 절차

새 작업은 다음 순서로 시작한다.

1. 현재 repository 구조와 `git status`를 확인한다.
2. `docs/PROJECT_STATUS.md`를 읽어 현재 구현 상태를 확인한다.
3. 요청받은 작업과 직접 관련된 문서만 읽는다.
4. 관련 ADR이 있는지 `docs/adr/`에서 확인한다.
5. 기존 코드를 먼저 확인한 뒤 구현한다.
6. 기존 구현과 문서가 서로 다른 경우 차이를 확인한다.

모든 문서를 매번 처음부터 끝까지 읽지 않는다. 영역별 상세 명세는 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/`에서 작업과 관련된 파일만 선택한다. 한국관광공사 endpoint와 parameter는 `MiriGangNeung_BackEnd_Codex_MD_Set/api_manual_guide/`의 공식 원본/변환본을 확인한다.

## 4. 문서의 역할

- `docs/PROJECT_STATUS.md`: 현재 repository 코드와 검증 결과를 기준으로 한 최신 구현 상태. 과거 상태를 누적하지 않는다.
- `docs/WORK_LOG.md`: 의미 있는 개발 작업의 누적 기록. 기존 기록은 삭제하거나 임의로 수정하지 않는다.
- `docs/adr/`: 중요한 아키텍처·기술 의사결정의 이유와 결과. 새 결정이 생길 때만 ADR을 추가한다.
- `docs/API_CONTRACT.md`: 프론트·백엔드·Postman 사용자를 위한 현재 API 호출 순서, 예시와 필드 매핑.
- `docs/openapi.yaml`: 현재 백엔드 API의 기계 판독용 OpenAPI 계약. API 계약 변경 시 함께 갱신한다.
- `MiriGangNeung_BackEnd_Codex_MD_Set/docs/`: API, DB, 알고리즘, AI, 이미지 저장, 외부 API, Redis, 오류 처리, 테스트와 배포의 상세 설계 문서.
- `MiriGangNeung_BackEnd_Codex_MD_Set/api_manual_guide/`: 한국관광공사 API 공식 활용 가이드 원본과 Markdown 변환본.

## 5. 현재 상태와 과거 기록의 구분

`PROJECT_STATUS.md`는 현재 코드가 실제로 하는 일을 기록한다. 완료되지 않은 구조나 계획을 완료된 기능처럼 쓰지 않는다.

`WORK_LOG.md`는 과거 작업을 기록한다. 기존 기록을 고치지 않고 새 항목을 추가한다.

`docs/adr/`는 단순 작업 이력이 아니라 중요한 결정과 그 근거를 기록하는 곳이다.

## 6. 모호한 요구사항 처리

다음 사항처럼 API 계약, 외부 API 사용 방식, 데이터 모델, 대규모 구조 변경 또는 미정 기술 선택에 영향을 주는 요구는 임의로 결정하지 않는다.

- 문제와 영향 범위를 먼저 명시한다.
- 사용자 또는 프로젝트 담당자의 결정을 요청한다.
- 결정을 기다리고 구현해야 하는 경우 작업 결과와 `PROJECT_STATUS.md`/`WORK_LOG.md`에 명확히 기록한다.

단순한 구현 세부사항은 합리적으로 처리하고, 중요한 설계 결정에 영향을 주는 경우에만 질문한다. AI Provider를 임의로 선택하거나 LLM API를 추가하지 않는다.

## 7. 구현 원칙

- 요청받은 범위에 집중하고 불필요한 리팩토링을 하지 않는다.
- 기존 기능을 임의로 삭제하거나 변경하지 않는다.
- Controller → Service → Repository 구조와 외부 Client/Adapter 경계를 유지한다.
- Request/Response DTO를 사용하고 Entity를 API 응답으로 직접 노출하지 않는다.
- 외부 API 원문 DTO를 프론트에 직접 반환하지 않고 내부 Domain/DTO로 정규화한다.
- 문서에 정의된 `/api/v1` API Contract를 준수한다.
- 새 dependency는 필요성을 확인한 뒤 추가한다.
- secret, API key, password를 코드나 커밋에 하드코딩하지 않는다.
- Redis는 캐시·Job 상태·임시 데이터 용도로만 사용하고 영속 데이터는 MySQL/JPA에 저장한다.
- P1/P2를 P0 구현에 섞지 않는다.

### 참고용 팀원 폴더의 작업 범위

- `MiriGangNeung_Agent`와 `MiriGangNeung_FrontEnd`는 팀원이 관리하는 Git 내용을 내려받아 참고하는 폴더로 취급한다.
- 용어상 `Agent 폴더` 또는 `AI 담당 폴더`는 `MiriGangNeung_Agent`를, `Frontend 폴더` 또는 `프론트엔드 폴더`는 `MiriGangNeung_FrontEnd`를 의미한다.
- 이 백엔드 작업에서는 두 폴더를 읽기 전용 참고 대상으로만 사용한다.
- 해당 폴더의 파일을 수정, 삭제, 이동, 이름 변경, 포맷팅하거나 백엔드 commit에 포함하지 않는다.
- 두 폴더의 코드가 필요해 보여도 먼저 백엔드 작업과의 관련성을 확인하고, 실제 변경은 이 repository의 백엔드 파일에만 수행한다.
- 현재 checkout에 해당 폴더가 없더라도 이 규칙은 이후 폴더가 추가될 때 동일하게 적용한다.

## 8. 테스트 및 검증

작업 종료 전 가능한 범위에서 다음을 실행한다.

- 컴파일 확인
- 변경 영역 관련 테스트
- 필요한 경우 전체 테스트: `./gradlew test` 또는 Windows PowerShell의 `./gradlew.bat test`
- repository에 정적 검사/lint가 추가되면 해당 검사도 실행

실패한 테스트를 성공으로 보고하지 않는다. 환경 문제로 실행하지 못한 경우 실패인지 미실행인지와 원인을 최종 보고에 구분한다.

## 9. 작업 완료 후 문서 업데이트

의미 있는 작업이 끝나면 다음을 수행한다.

1. `docs/PROJECT_STATUS.md`를 실제 코드와 테스트 결과에 맞게 갱신한다.
2. `docs/WORK_LOG.md`에 작업을 추가한다.
3. 새롭고 중요한 아키텍처 결정이 있으면 `docs/adr/`에 ADR을 추가한다.
4. 테스트 결과, 남은 문제와 다음 작업을 기록한다.

사소한 오타 수정은 WORK_LOG에 남기지 않는다.

작업 기록 시간 규칙:

- `PROJECT_STATUS.md` 상단에는 `Last Updated: YYYY-MM-DD HH:mm KST`와 `Last Updated By: <agent>`를 유지한다.
- `WORK_LOG.md`의 의미 있는 작업 단위에는 날짜, 시작 시간, 완료 시간, 작업 agent, 작업 내용, 주요 변경 파일, 테스트 결과, 문제와 해결 방법, 관련 commit을 기록한다.
- 시간은 KST(UTC+9)로 기록한다.
- 과거 작업의 정확한 시간을 확인할 수 없으면 임의로 만들지 않고 `시간 미기록`으로 표시한다.
- 작업 중인 항목은 완료 시간을 `In Progress`로 표시한다.
- Git commit timestamp와 실제 작업 시간은 구분한다.

## 10. Git

Git commit과 diff는 실제 코드 변경의 근거로 사용한다. 가능하면 WORK_LOG에 관련 commit hash와 message를 기록한다. 작업 시작 전 기존 변경사항을 보존하고, 사용자의 요청 없이 reset·대규모 삭제·기존 변경 덮어쓰기를 하지 않는다.

## 11. 문서와 코드가 충돌할 경우

문서와 코드가 다르면 조용히 문서나 코드를 바꾸지 않는다.

1. 어떤 부분이 다른지 확인한다.
2. 현재 코드가 실제로 어떻게 동작하는지 확인한다.
3. 문서가 목표 상태인지 확인한다.
4. 필요한 변경과 영향 범위를 판단한다.
5. 중요한 충돌이면 사용자에게 알리고 작업 결과에 기록한다.

실제 코드와 테스트 결과를 현재 동작의 근거로 삼되, API Contract 변경은 별도 확인 없이 하지 않는다.

## 12. 과거 작업 기록 사용 방법

`WORK_LOG.md` 전체를 매번 읽지 않는다. 현재 작업과 관련된 항목만 검색한다. 중요한 결정의 이유가 필요하면 WORK_LOG보다 먼저 `docs/adr/`를 확인한다.

전체 API 명세, DB 스키마, 관광공사 매뉴얼, 추천 알고리즘의 상세 내용과 긴 개발 이력은 이 파일에 복사하지 않는다. 해당 원문 문서를 참조한다.
