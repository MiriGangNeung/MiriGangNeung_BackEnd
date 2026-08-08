# 17 Decision Log

## D-001 — Backend repository

**결정:** 프론트와 별도 Git repository.

```text
MiriGangNeung_FrontEnd
MiriGangNeung_BackEnd
```

## D-002 — Backend stack

**결정:** Java + Spring Boot + Gradle + Spring Data JPA + MySQL.

## D-003 — Redis

**결정:** 사용.

목적은 cache, Job 상태, TTL 데이터다. MySQL 대체가 아니다.

## D-004 — 회원

**결정:** 회원가입 없음.

User/JWT/RefreshToken을 P0에 만들지 않는다.

## D-005 — 지역

**결정:** 강릉시만.

강릉시 외 관광지는 P0 후보에서 제외한다.

## D-006 — 지도

**결정:** Kakao로 통일.

Frontend Kakao Maps + Backend Kakao REST route.

## D-007 — 코스 추천

**결정:** 현재 LLM 사용 안 함.

규칙 기반/점수 기반/거리 기반 알고리즘으로 구현한다.

향후 LLM을 Adapter 뒤에 추가할 수 있게 한다.

## D-008 — AI Provider

**상태:** 미정.

AI 담당자가 Provider/모델을 결정한다.

백엔드는 `AiGenerationClient`로 추상화한다.

## D-009 — 이미지 저장

**결정:** 장기 저장하지 않는다.

MVP는 local temporary storage를 허용한다.

다중 인스턴스/worker 확장 시 S3-compatible storage로 교체 가능하게 추상화한다.

## D-010 — 코스 공유

**결정:** 익명 share token.

회원 없이도 링크 공유가 가능하도록 한다.

## D-011 — 코스 저장/스토리 카드

**결정:** 최종 서비스 기능으로 문서화한다.

P0와 P1/P2를 분리하여 현재 구현 범위를 통제한다.

## D-012 — duration

현재 프론트 UI의 `day / night1 / custom`을 여행 길이로 해석한다.

- day = 당일
- night1 = 1박 2일
- custom = 사용자 지정 날짜/기간

실제 달력 날짜는 custom에서만 필수로 본다.

## D-013 — 관광공사 API

핵심 관광정보/사진/연관/방문자·집중률 등과 특화 API 확장 구조를 유지한다.

정확한 endpoint/parameter는 제공된 원본 매뉴얼을 기준으로 한다.

## D-014 — Source preservation

원본 DOCX API 매뉴얼은 `api_manual_guide/original`에 보존한다.

Markdown 변환본은 Codex 검색 편의를 위한 참고본이며, 복잡한 표/이미지는 원본을 우선한다.
