# 11 External APIs

## 1. 한국관광공사

프로젝트의 핵심 공공데이터 소스.

원본 매뉴얼은 `api_manual_guide/original/`에 보존하고, Markdown 변환본은 `api_manual_guide/markdown/`에 있다.

### Core

- 국문 관광정보
- 관광사진
- 관광지별 연관 관광지
- 위치 기반/지역 기반 관광정보

### Recommendation signals

- 지역별 방문자수
- 관광지 집중률/방문자 추이 예측
- 관광다양성/수요 관련 API

### 특화 확장

- 무장애여행
- 반려동물동반여행
- 고캠핑
- 두루누비
- 웰니스 등 제공되는 특화 데이터

**실제 endpoint/parameter는 반드시 원본 매뉴얼을 확인한다. 기억으로 endpoint를 만들지 않는다.**

## 2. Kakao

팀 결정: 지도/경로는 Kakao로 통일한다.

- Frontend: Kakao Maps JavaScript SDK
- Backend: Kakao REST API for route information

Kakao API 원문 응답은 backend DTO로 normalize한다.

## 3. AI

백엔드는 별도 FastAPI Agent를 `AiGenerationClient`/`HttpAiGenerationClient`를 통해서만 호출한다.
실제 Provider와 모델은 Agent의 `AI_PROVIDER` 설정에서 선택하며 백엔드가 하드코딩하지 않는다.

## API Key

환경변수:

```text
TOUR_API_KEY
KAKAO_API_KEY
AI_API_KEY
AI_BASE_URL
```

`AI_API_KEY`는 백엔드와 Agent 사이의 선택적 공유 인증값이며 두 서비스에 동일하게 설정한다.
모든 실제 값은 `.env`, secrets manager, deployment secret 등으로 관리한다.

절대 Git commit 금지.

## 장애

외부 API는 항상:

- timeout
- retry 가능 여부
- rate limit
- invalid response
- upstream 5xx
- partial data

를 고려한다.

관광공사 캐시가 있는 경우 최근 정상 데이터를 fallback으로 사용할 수 있다.
