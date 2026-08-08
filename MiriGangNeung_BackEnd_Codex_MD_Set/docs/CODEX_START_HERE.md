# CODEX START HERE — MiriGangNeung Backend

## 0. 당신의 역할

당신은 `MiriGangNeung_BackEnd` 저장소의 백엔드 개발자다.

이 문서 세트는 프론트엔드 저장소, 현재 UI 프로토타입, 공모전 요구사항, 팀의 결정사항, 한국관광공사 OpenAPI 원본 매뉴얼을 기준으로 작성되었다.

**가장 중요한 원칙**

1. 이 문서와 실제 코드가 충돌하면 실제 코드/최신 API 계약을 우선하되, 계약을 바꾸기 전에 영향 범위를 확인한다.
2. 이 문서에서 `확정`으로 표시된 설계는 임의로 변경하지 않는다.
3. `미정/추상화` 영역(AI Provider 등)은 특정 업체/서비스를 임의로 선택하지 않는다.
4. 현재 구현 범위는 `P0`이다. `P1/P2`는 인터페이스와 확장 지점만 준비하고 임의로 전부 구현하지 않는다.
5. 회원가입/JWT/User 기능은 현재 구현하지 않는다.
6. 코스 생성은 현재 **LLM을 사용하지 않는 규칙 기반/알고리즘 기반 추천**으로 구현한다.
7. 관광공사 데이터는 외부 API 원문 DTO를 도메인에 직접 노출하지 않는다.
8. 프론트엔드와 백엔드는 별도 Git repository이며 REST API로 통신한다.
9. 외부 API Key/Secret은 코드/커밋에 절대 넣지 않는다.
10. 사용자 원본 사진과 AI 생성 이미지는 장기 보관하지 않는다.

## 1. 문서 읽기 순서

### 반드시 읽기

1. `01_PROJECT_OVERVIEW.md`
2. `02_FRONTEND_CONTRACT.md`
3. `03_USER_FLOW.md`
4. `04_REQUIREMENTS_AND_SCOPE.md`
5. `05_BACKEND_ARCHITECTURE.md`
6. `06_API_SPECIFICATION.md`
7. `07_DATA_MODEL.md`
8. `08_RECOMMENDATION_ALGORITHM.md`
9. `09_AI_INTEGRATION.md`
10. `10_IMAGE_STORAGE.md`
11. `11_EXTERNAL_APIS.md`
12. `12_REDIS_STRATEGY.md`
13. `13_ERROR_SECURITY_OBSERVABILITY.md`
14. `14_TEST_STRATEGY.md`
15. `15_IMPLEMENTATION_PLAN.md`
16. `16_CODING_CONVENTION.md`
17. `17_DECISION_LOG.md`

### 참고

- `18_FUTURE_FEATURES.md`
- `19_API_MANUAL_INDEX.md`
- `20_DEPLOYMENT.md`
- `21_GLOSSARY.md`
- `api_manual_guide/markdown/*`

## 2. 현재 확정 기술 스택

- Java
- Spring Boot
- Gradle
- Spring Data JPA
- MySQL
- Redis
- Kakao Maps / Kakao REST API
- 한국관광공사 OpenAPI
- AI 서버: Provider 미정, Adapter/Client 인터페이스로 추상화
- 이미지 저장: MVP 로컬 임시 저장 우선, object storage 교체 가능 구조

Spring Boot의 정확한 minor/patch 버전은 저장소 초기화 시점의 팀 환경에 맞추며, 이미 존재하는 `build.gradle`/`gradle.properties`가 있다면 그것을 우선한다.

## 3. 현재 서비스 흐름

`장소 선택(최대 3) → 원픽 선택 → 사용자 사진 업로드/동의 → AI 합성 → 합성 결과 → 여행 조건 → 알고리즘 기반 코스 생성 → 지도/경로 → 저장/공유/스토리 카드`

## 4. 현재 P0

- 강릉시 관광지 조회
- 관광지/사진 데이터 제공
- 최대 3개 장소 선택을 지원하는 데이터
- 원픽 장소 정보
- 사용자 사진 업로드
- AI 생성 Job 생성/조회/결과
- 생성 결과 다운로드
- AI 생성 실패/재시도
- 여행 조건 기반 코스 생성
- 원픽을 코스 핵심 장소로 포함
- 관광공사 데이터 기반 후보 필터링/점수화
- 거리/이동시간 고려
- Kakao 경로 API 연동
- 코스 결과
- 코스 저장/공유의 익명 share token
- 코스 삭제/공유 만료
- 임시 이미지 자동 삭제
- Redis 캐시/Job 상태
- 기본 관측/로그/에러 처리

## 5. P1/P2

P1/P2는 문서에 설계만 남긴다. 구현을 요청받기 전까지 임의로 구현하지 않는다.

예:
- 숙박/교통 예약 연계
- 숏폼 생성
- 방문 인증
- 캠페인/쿠폰
- 고도화된 관리자 기능
- 특화 관광 데이터 전체 확장
- LLM 추천 Provider 추가

## 6. 구현 종료 기준

P0의 API가 명세와 일치하고 다음이 모두 만족되어야 한다.

- `./gradlew test` 성공
- 핵심 서비스 단위 테스트 존재
- Controller 통합 테스트 존재
- 외부 API 호출이 Adapter/Client 계층으로 격리됨
- API Key가 소스에 없음
- Entity를 Controller response로 직접 반환하지 않음
- 외부 API 원문 DTO를 프론트에 직접 반환하지 않음
- 이미지 TTL 정리 동작 검증
- Redis 장애 시 가능한 기능은 graceful degradation
- 프론트가 필요한 response shape와 일치
- README에 로컬 실행법과 환경변수가 정리됨
