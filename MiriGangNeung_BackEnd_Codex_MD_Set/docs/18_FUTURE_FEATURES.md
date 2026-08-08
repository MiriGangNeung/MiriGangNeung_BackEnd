# 18 Future Features

현재 P0 구현을 방해하지 않도록 확장 지점만 남긴다.

## P1

- 숙박/교통 예약 딥링크
- 코스 편집
- 대체 장소 추천 UI 강화
- 방문자/혼잡 데이터 고도화
- 코스 복제
- 스토리 카드 생성

## P2

- 숏폼
- 방문 인증
- 캠페인/쿠폰
- 관리자
- 운영 대시보드
- 추천 가중치 UI
- LLM 자연어 설명
- 개인화
- 특화 관광 profile

## Architecture requirement

Future feature가 P0 entity를 무리하게 깨지 않도록:

- interfaces
- DTO versioning
- optional fields
- strategy pattern
- external client adapter

를 활용한다.

## 절대 하지 말 것

미래 기능을 위한 테이블/endpoint를 지금 전부 만들면서 핵심 흐름을 복잡하게 만들지 않는다.
