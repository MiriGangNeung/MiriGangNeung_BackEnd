# MiriGangNeung API Contract Guide

이 문서는 프론트엔드·백엔드·Postman 사용자가 현재 백엔드 API를 같은 방식으로 이해하기 위한 사람용 안내서다.

기계가 읽는 공식 계약은 [`openapi.yaml`](./openapi.yaml)이며, 전체 설계 배경은 `MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md`를 참고한다.

## 1. 실행 전제

```text
Backend: http://localhost:8080
Base path: /api/v1
```

Docker를 사용하는 경우:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

백엔드 health 확인:

```http
GET http://localhost:8080/actuator/health
```

## 2. 권장 호출 순서

```text
1. GET /api/v1/places
2. 응답 content에서 실제 place id를 선택
3. POST /api/v1/courses에 선택한 id 전송
4. 필요하면 GET /api/v1/courses/{courseId}
5. 필요하면 POST /api/v1/courses/{courseId}/share
6. 지도 경로는 POST /api/v1/routes/walking
```

프론트의 mock id(`jumunjin`, `anmok` 등)는 백엔드 Course API에 사용할 수 없다. 반드시 백엔드 Place API에서 받은 실제 id를 사용한다.

## 3. 장소 API

### 목록 조회

```http
GET /api/v1/places?page=0&size=20
GET /api/v1/places?keyword=경포&page=0&size=20
```

목록 응답은 배열이 아니라 `content` 안에 들어 있다.

```json
{
  "content": [
    {
      "id": "place-id",
      "name": "경포해변",
      "region": "강릉",
      "category": "12",
      "tags": [],
      "thumbnailUrl": "https://example.com/image.jpg",
      "latitude": 37.8046,
      "longitude": 128.9072
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 상세·주변·연관 조회

```http
GET /api/v1/places/{placeId}
GET /api/v1/places/{placeId}/nearby
GET /api/v1/places/{placeId}/related
```

## 4. KTO 사진 소스 API

사진 소스 API는 한국관광공사 원문을 내부 DTO로 정규화해 반환하며 데이터를 백엔드 DB에 영속화하지 않는다.

```http
GET /api/v1/award-photos?region=51&page=0&size=100
GET /api/v1/tourism-photos?page=0&size=100
```

`award-photos`는 기본적으로 강원 권역 코드 `51`을 요청하고, 서비스 계층에서 위치에 `강릉`이 포함된 사진만 반환한다. `tourism-photos`는 `강릉` 키워드로 검색한 뒤 동일한 위치 필터를 적용한다. 두 API의 `size`는 1~100이다.

응답의 공통 형태는 다음과 같다.

```json
{
  "content": [],
  "page": 0,
  "size": 100,
  "totalElements": 0,
  "totalPages": 0
}
```

현재 KTO 사진 원문 응답과 강릉·이미지 필터가 적용된 뒤의 전체 건수를 별도로 제공하지 않으므로 `totalElements`는 현재 응답 content 개수, `totalPages`는 content가 있을 때 1, 없을 때 0이다. 따라서 정식 페이지네이션 UI의 전체 페이지 수로 사용하지 않는다. 정확한 페이지네이션으로 변경하려면 KTO totalCount와 필터링 전략을 함께 확정해야 한다.

Award photo 주요 필드:

```text
id, title, location, award, keywords, originalImageUrl, thumbnailUrl,
photographer, copyrightCode, source=KTO_AWARD
```

Tourism photo 주요 필드:

```text
id, title, location, photographyMonth, keywords, originalImageUrl,
thumbnailUrl, photographer, source=KTO_PHOTO_GALLERY
```

PhotoGalleryService1은 단일 이미지 URL만 제공하므로 `originalImageUrl`과 `thumbnailUrl`이 동일할 수 있다.

## 5. 코스 API

### 생성 요청

```http
POST /api/v1/courses
Content-Type: application/json
```

```json
{
  "placeIds": ["place-id-1", "place-id-2", "place-id-3"],
  "onePickId": "place-id-1",
  "types": ["nature"],
  "companion": "couple",
  "duration": "day"
}
```

`onePickId`는 반드시 `placeIds` 안에 포함되어야 한다. 현재 추천 엔진은 원픽을 첫 장소로 고정하고, 선택된 장소 중 좌표상 가까운 장소를 선택한다.

응답의 핵심 필드는 다음과 같다.

```json
{
  "courseId": "course-uuid",
  "title": "나만의 강릉 코스",
  "duration": "day",
  "stops": [
    {
      "sequence": 1,
      "placeId": "place-id-1",
      "name": "경포해변",
      "arrivalTime": "09:00",
      "stayMinutes": 60,
      "crowdLevel": "LOW",
      "isOnePick": true,
      "note": "원픽 장소",
      "latitude": 37.8046,
      "longitude": 128.9072
    }
  ],
  "totalDistanceMeters": 0,
  "totalTravelMinutes": 0
}
```

현재 `totalDistanceMeters`와 `totalTravelMinutes`는 Course 응답에 아직 경로 계산 결과가 통합되지 않아 0일 수 있다.

### 나머지 Course API

```http
GET /api/v1/courses/{courseId}
DELETE /api/v1/courses/{courseId}
POST /api/v1/courses/{courseId}/share
DELETE /api/v1/courses/{courseId}/share
GET /api/v1/share/courses/{shareToken}
```

## 6. 사진 합성 Job API

```http
POST /api/v1/compositions
Content-Type: multipart/form-data
```

multipart 필드:

```text
photo: 이미지 파일
onePickId: 장소 id
aspectRatio: 선택값
```

그 후 polling한다.

```http
GET /api/v1/compositions/{jobId}
POST /api/v1/compositions/{jobId}/retry
GET /api/v1/compositions/{jobId}/download
```

주의: 현재 AI Provider는 실제로 선택·연결되지 않았고 `AiGenerationClient` 인터페이스만 존재한다. 따라서 Job 생성 API가 있어도 실제 DONE 이미지가 항상 생성되는 상태는 아니다.

## 7. 도보 경로 API

현재 백엔드 계약은 두 지점 사이의 POST 요청이다.

```http
POST /api/v1/routes/walking
Content-Type: application/json
```

```json
{
  "origin": {
    "latitude": 37.8046,
    "longitude": 128.9072
  },
  "destination": {
    "latitude": 37.7722,
    "longitude": 128.8961
  }
}
```

```json
{
  "distanceMeters": 1200,
  "durationSeconds": 900,
  "polyline": []
}
```

프론트 repository의 자체 `/api/walking-route?stops=...` API는 현재 백엔드 API와 다른 별도 구현이다. 프론트가 백엔드를 사용하려면 URL, HTTP method, request/response shape을 이 문서 기준으로 맞춰야 한다.

## 8. 공통 오류 응답

```json
{
  "timestamp": "2026-08-08T08:19:05Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/courses"
}
```

대표 상태 코드:

| 상태 | 의미 |
|---:|---|
| 200 | 정상 처리 |
| 400 | 요청 JSON·파라미터·validation 오류 |
| 404 | 장소·코스·Job을 찾을 수 없음 |
| 502 | 관광공사·Kakao 등 외부 API 오류 |

## 9. 프론트 연동 시 필드 변환

현재 프론트 mock 타입과 백엔드 응답 필드가 다르다.

| 프론트 mock | 백엔드 API |
|---|---|
| `places` | `content` |
| `cat` | `category` |
| `lat` | `latitude` |
| `lng` | `longitude` |
| `n` | `sequence` |
| `id` | `placeId` |
| `time` | `arrivalTime` |
| `stay` | `stayMinutes` |
| `crowd` | `crowdLevel` |

프론트의 `usePlacesQuery`, `useCourseStopsQuery`, `useComposeRun`은 현재 static/mock 구현이다. 실제 연동 시 해당 지점부터 백엔드 API 호출로 교체해야 한다.

## 10. 관련 문서

- [OpenAPI 정의](./openapi.yaml)
- [프로젝트 상태](./PROJECT_STATUS.md)
- [상세 API 명세](../MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md)
- [백엔드 시작 문서](./CODEX_START_HERE.md)
