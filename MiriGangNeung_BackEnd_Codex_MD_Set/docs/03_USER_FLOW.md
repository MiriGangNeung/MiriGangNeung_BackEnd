# 03 User Flow

## 전체

```text
장소 선택 최대 3
  ↓
원픽 선택
  ↓
사진 업로드 + 동의
  ↓
AI 생성 Job
  ↓
생성 결과
  ↓
여행 조건
  ↓
추천 후보 생성
  ↓
추천 점수 계산
  ↓
동선/시간 검증
  ↓
코스 결과
  ↓
Kakao 지도
  ↓
저장 / 공유 / 스토리 카드
```

## AI 생성 상세

```text
POST composition
  ↓
QUEUED
  ↓
AI provider 요청
  ↓
ANALYZING
  ↓
COMPOSITING
  ↓
QUALITY_CHECK
  ↓
DONE
  ↓
다운로드
  ↓
TTL 삭제
```

실패:

```text
FAILED
  ├─ INVALID_INPUT
  ├─ PROVIDER_ERROR
  ├─ TIMEOUT
  ├─ SAFETY_REJECTED
  └─ INTERNAL_ERROR
```

일시적 외부 오류는 제한된 횟수의 자동 retry를 고려한다.

## 코스 생성 상세

```text
선택 장소
+ 원픽
+ 여행 유형
+ 동행
+ 기간
      ↓
강릉시 후보 수집
      ↓
운영 가능성 필터
      ↓
카테고리/취향 필터
      ↓
원픽 연관 후보 확장
      ↓
추천 점수
      ↓
코스 구성
      ↓
시간/거리 검증
      ↓
Kakao 경로 조회
      ↓
최종 CourseResponse
```

## 익명 공유

```text
Course 생성
  ↓
shareToken 생성
  ↓
공유 URL
  ↓
누구나 GET
  ↓
삭제/만료 시 공유 불가
```
