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
4. 응답의 `courseId`로 실제 코스 결과를 표시
5. 필요하면 주변 카페·음식점 조회 후 코스에 추가
6. 필요하면 장소 삭제·순서 변경·공유
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
      "thumbnailUrl": "http://localhost:8080/media/images/place-...-thumbnail.jpg",
      "latitude": 37.8046,
      "longitude": 128.9072,
      "imageUrls": ["http://localhost:8080/media/images/place-...-thumbnail.jpg"],
      "originalImageUrls": ["http://localhost:8080/media/images/place-...-original.jpg"]
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

## 4. 장소 이미지 보충 정책

장소 목록은 KorService2에서 수집한 배경 합성용 장소 카드만 반환한다. 장소 하나에 대해 KorService2 이미지와 PhotoGalleryService1에서 장소명으로 매칭된 이미지를 합쳐 Type1 이미지 최대 5장까지 제공한다.

```http
# 장소 카드와 저장된 이미지 조회
GET /api/v1/places?page=0&size=20
```

PhotoGalleryService1에서 장소명이 기존 KorService2 장소와 매칭되지 않는 사진은 카드로 만들지 않고 버린다. 위치·상세 정보가 부족한 사진을 별도 장소로 노출하지 않기 위한 정책이다.

PhotoGalleryService1 원문 API는 공개 컨트롤러로 노출하지 않는다. 동기화 시에만 내부적으로 호출하고, 결과 이미지 URL을 `place_images`에 저장한다. 따라서 화면 요청이나 Redis 만료 때문에 관광공사 API를 다시 호출하지 않는다.

동기화에서 이미지 캐시가 켜져 있으면 원본 URL을 한 번 다운로드해 로컬 저장소(운영에서는 object storage/CDN origin으로 교체 가능한 구조)에 원본과 카드용 썸네일로 저장한다. `imageUrls`는 썸네일, `originalImageUrls`는 같은 순서의 원본 URL이다. 기존 캐시 파일이 없는 레거시 행은 두 필드 모두 관광공사 원본 URL로 fallback한다.

저장된 파일은 다음 endpoint에서 장기 immutable cache header와 함께 제공한다.

```http
GET http://localhost:8080/media/images/{storageKey}
```

Redis에는 장소 JSON만 저장하며 이미지 binary는 저장하지 않는다. `IMAGE_PUBLIC_BASE_URL`을 실제 CDN 도메인으로 바꾸면 DB의 storage key를 바꾸지 않고 공개 URL만 전환할 수 있다.

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

`onePickId`는 반드시 `placeIds` 안에 포함되어야 한다. 추천 엔진은 원픽을 첫 장소로 고정한 뒤, 선택된 후보에 대해 `types` 여행 유형과 `companion` 동행자 조건의 설명 가능한 점수를 계산하고 거리순을 fallback으로 사용한다. 현재 장소 데이터에 동행자 전용 태그가 없으므로 동행자 조건은 카테고리·장소명·설명 기반의 보정 점수로 적용된다. 조건에 맞는 후보가 없어도 후보를 하드 필터링하지 않고 거리순으로 추천한다.

현재 P0의 기간별 정거장 수 제한은 `day` 기준 원픽 외 최대 3개, `night1` 기준 원픽 외 최대 4개다. 다일 코스의 날짜별 분리·숙박·중복 방지는 별도 확장 범위다.

응답의 핵심 필드는 다음과 같다.

```json
{
  "courseId": "course-uuid",
  "title": "나만의 강릉 코스",
  "duration": "day",
  "stops": [
    {
      "stopId": "stop-uuid",
      "sequence": 1,
      "placeId": "place-id-1",
      "externalPlaceId": null,
      "name": "경포해변",
      "arrivalTime": "09:00",
      "stayMinutes": 60,
      "crowdLevel": "LOW",
      "isOnePick": true,
      "note": "원픽 장소",
      "latitude": 37.8046,
      "longitude": 128.9072,
      "external": false,
      "category": "nature",
      "address": "강릉시",
      "placeUrl": null
    }
  ],
  "totalDistanceMeters": 1200,
  "totalTravelMinutes": 15,
  "routeStatus": "READY",
  "routeSegments": []
}
```

`routeStatus`는 Kakao 도보 API 키가 없거나 외부 경로를 계산하지 못하면 `UNAVAILABLE`이 된다. 이 경우 코스 CRUD와 장소 표시는 계속 사용할 수 있고 거리·시간은 0으로 반환된다. `routeSegments`의 `polyline` 좌표는 `[longitude, latitude]` 순서다.

`arrivalTime`은 저장된 고정값을 그대로 반복하지 않는다. 현재 stop 순서의 첫 장소는 09:00이며, 다음 장소부터 직전 장소의 `stayMinutes`와 확인된 직전 `routeSegments.durationSeconds`를 누적해 계산한다. 도보 경로가 unavailable이면 이동시간을 0으로 두고 체류시간만 누적한 표시용 일정으로 반환한다.

### 코스 주변 카페·음식점 조회

코스에 포함된 관광지 좌표를 모두 기준으로 Kakao Local 카테고리 API를 조회한다. 같은 Kakao 장소가 여러 관광지 주변에서 발견되면 가장 가까운 거리만 남겨 거리순으로 정렬한다. 카카오 REST 키는 백엔드의 `KAKAO_API_KEY`로만 설정한다.

```http
GET /api/v1/courses/{courseId}/nearby-places?category=cafe
GET /api/v1/courses/{courseId}/nearby-places?category=restaurant
```

`cafe`는 Kakao `CE7`, `restaurant`는 Kakao `FD6`으로 변환되며 기본 반경은 2km다. 응답은 가장 가까운 관광지와의 거리·이름을 포함한다.

### 주변 장소 추가·삭제·순서 변경

```http
POST /api/v1/courses/{courseId}/stops/external
Content-Type: application/json
```

```json
{
  "externalPlaceId": "kakao-place-id",
  "name": "카페 예시",
  "category": "cafe",
  "categoryName": "음식점 > 카페",
  "address": "강릉시 안목동",
  "roadAddress": "강릉시 창해로",
  "phone": "033-000-0000",
  "placeUrl": "https://place.map.kakao.com/kakao-place-id",
  "longitude": 128.948,
  "latitude": 37.772
}
```

추가 시 Kakao 응답을 코스 전용 snapshot으로 DB에 저장하고 `CourseStop`을 마지막 순서에 붙인다. 전역 관광지 카탈로그에는 추가하지 않는다.

```http
DELETE /api/v1/courses/{courseId}/stops/{stopId}
PUT /api/v1/courses/{courseId}/stops/order
Content-Type: application/json
```

```json
{ "stopIds": ["stop-uuid-2", "stop-uuid-1"] }
```

원픽 장소는 삭제할 수 없으며, 순서 변경 요청은 현재 코스의 모든 `stopId`를 중복 없이 정확히 한 번씩 포함해야 한다. 추가·삭제·순서 변경 후에는 도보 거리·시간과 `routeSegments`를 다시 계산한다.

외부 연동 기준은 [Kakao Local 카테고리 검색 가이드](https://developers.kakao.com/docs/ko/local/dev-guide)와 [Kakao 지도 REST API 가이드](https://developers.kakao.com/docs/ko/kakaomap/rest-api)다. 카페·음식점은 현재 자동으로 코스에 삽입하지 않고 이 API로 조회한 뒤 사용자가 선택해 추가한다.

### 나머지 Course API

```http
GET /api/v1/courses/{courseId}
DELETE /api/v1/courses/{courseId}
POST /api/v1/courses/{courseId}/share
DELETE /api/v1/courses/{courseId}/share
GET /api/v1/share/courses/{shareToken}
```

## 6. 사진 합성 Job API

### 기본 AI 모델 목록

```http
GET /api/v1/composition-models
```

응답에는 백엔드가 실제 합성 입력으로 사용할 수 있는 preset만 포함된다. 현재는 `default-female-01` 1개를 제공한다.
`imageUrl`은 모델 미리보기용 이미지 API다.

```http
GET /api/v1/composition-models/{modelPresetId}/image
```

```http
POST /api/v1/compositions
Content-Type: multipart/form-data
```

multipart 필드:

```text
photo: 이미지 파일 (photo 방식에서 필수, JPEG/PNG/WEBP, 최대 10MB)
modelPresetId: 기본 AI 모델 ID (preset 방식에서 필수, photo와 동시에 사용 불가)
onePickId: GET /api/v1/places에서 받은 Place.id UUID (필수)
aspectRatio: 1:1 | 4:5 | 9:16 (선택, 기본 4:5)
backgroundImageUrl: 사용자가 선택한 해당 Place의 Type1 이미지 URL (선택, originalImageUrls 권장)
```

`onePickId`는 `kto-award:*`, `kto-gallery:*` 표시용 ID가 아니라 백엔드 `Place.id` UUID여야 한다.
`photo` 또는 `modelPresetId` 중 정확히 하나만 전송해야 한다. `modelPresetId` 방식은 백엔드 preset asset을
기존 Agent 입력 사진으로 사용하므로 나머지 Job/polling/result 계약은 동일하다.
백엔드는 해당 Place의 `copyrightCode=Type1` 이미지 원본을 `PlaceImageStorage`에서 읽어 Agent에
`background` 파일로 전달한다. `backgroundImageUrl`을 생략하면 정렬 순서가 가장 앞선 Type1 이미지를
사용한다. 지정한 URL이 해당 Place의 Type1 이미지가 아니거나 원본을 준비할 수 없으면 Job을 만들지
않고 오류를 반환한다.

생성 성공 예시:

```json
{
  "jobId": "f7a1d2de-8215-4b0e-98bd-27d8e4cbf671",
  "status": "QUEUED",
  "progress": 0,
  "stage": "요청 접수",
  "resultAvailable": false,
  "downloadUrl": null,
  "place": null,
  "error": null,
  "safety": {
    "status": "UNKNOWN",
    "reasonCode": null,
    "warnings": []
  }
}
```

생성 후 다음 API를 1~2초 간격으로 polling한다.

```http
GET /api/v1/compositions/{jobId}
POST /api/v1/compositions/{jobId}/retry
GET /api/v1/compositions/{jobId}/download
```

상태는 `QUEUED`, `ANALYZING`, `COMPOSITING`, `QUALITY_CHECK`, `DONE`, `FAILED`다.
`DONE && resultAvailable == true`일 때만 `downloadUrl`을 사용한다. `FAILED` 응답의 `error`는
`code`, `message`, `retryable`을 포함하며, `retryable=true`인 경우에만 retry API를 호출한다.
`safety.warnings`는 결과를 차단하지 않는 품질 경고이며 경고가 있어도 상태는 `DONE`일 수 있다.

진행 중 응답:

```json
{
  "jobId": "f7a1d2de-8215-4b0e-98bd-27d8e4cbf671",
  "status": "COMPOSITING",
  "progress": 60,
  "stage": "이미지 합성 중",
  "resultAvailable": false,
  "downloadUrl": null,
  "place": null,
  "error": null,
  "safety": {
    "status": "UNKNOWN",
    "reasonCode": null,
    "warnings": []
  }
}
```

완료 응답:

```json
{
  "jobId": "f7a1d2de-8215-4b0e-98bd-27d8e4cbf671",
  "status": "DONE",
  "progress": 100,
  "stage": "COMPLETED",
  "resultAvailable": true,
  "downloadUrl": "/api/v1/compositions/f7a1d2de-8215-4b0e-98bd-27d8e4cbf671/download",
  "place": null,
  "error": null,
  "safety": {
    "status": "PASSED",
    "reasonCode": null,
    "warnings": [
      {
        "code": "FACE_NOT_PRESERVED",
        "message": "얼굴이 실제 모습과 조금 다르게 표현됐을 수 있습니다."
      }
    ]
  }
}
```

실패 응답:

```json
{
  "jobId": "f7a1d2de-8215-4b0e-98bd-27d8e4cbf671",
  "status": "FAILED",
  "progress": 60,
  "stage": "FAILED",
  "resultAvailable": false,
  "downloadUrl": null,
  "place": null,
  "error": {
    "code": "PROVIDER_TIMEOUT",
    "message": "AI 이미지 생성 시간이 초과되었습니다.",
    "retryable": true
  },
  "safety": null
}
```

retry는 request body가 없으며 성공 시 같은 `jobId`로 `QUEUED` 상태가 반환된다. 다운로드 응답은
실제 이미지 Content-Type과 `Content-Disposition: attachment`를 사용한다.

Backend-Agent 연결에는 `AI_BASE_URL`과 선택적인 공유 인증값 `AI_API_KEY`가 필요하다. 실제 모델과
Provider 선택은 Agent 측 `AI_PROVIDER` 설정의 책임이며 백엔드는 특정 모델을 선택하지 않는다.

## 7. 도보 경로 API

코스 생성·장소 추가·삭제·순서 변경 응답은 인접한 코스 장소 간 Kakao 도보 경로를 합산한 `totalDistanceMeters`, `totalTravelMinutes`, `routeSegments`를 포함한다. 외부 API를 사용할 수 없을 때도 장소 목록은 반환하고 `routeStatus=UNAVAILABLE`로 표시한다. 현재 Client는 Kakao Mobility Affiliate Walking의 `GET /affiliate/walking/v1/directions`에 `origin=longitude,latitude`, `destination=longitude,latitude`, `priority=DISTANCE`, `summary=false`를 전달한다. 이 API는 별도 제휴·승인이 필요하며 권한이 없으면 403이므로 코드에서는 코스 CRUD를 유지하면서 unavailable로 처리한다.

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

프론트 API adapter는 아래 필드를 화면 타입으로 변환한다.

| 프론트 mock | 백엔드 API |
|---|---|
| `places` | `content` |
| `cat` | `category` |
| `lat` | `latitude` |
| `lng` | `longitude` |
| `n` | `sequence` |
| `id` | `stopId` |
| `time` | `arrivalTime` |
| `stay` | `stayMinutes` |
| `crowd` | `crowdLevel` |

프론트 코스 결과는 `POST /api/v1/courses` 응답의 `courseId`를 sessionStorage에 보관하고, 새로고침 시 `GET /api/v1/courses/{courseId}`로 복원한다. 주변 장소 탭은 백엔드 `nearby-places` API만 호출하며 브라우저에서 Kakao REST API를 직접 호출하지 않는다.

## 10. 관련 문서

- [OpenAPI 정의](./openapi.yaml)
- [프로젝트 상태](./PROJECT_STATUS.md)
- [상세 API 명세](../MiriGangNeung_BackEnd_Codex_MD_Set/docs/06_API_SPECIFICATION.md)
- [백엔드 시작 문서](./CODEX_START_HERE.md)
