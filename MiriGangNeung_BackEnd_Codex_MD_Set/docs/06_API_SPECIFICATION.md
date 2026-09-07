# 06 API Specification

Base URL:

```text
/api/v1
```

## 1. Places

### GET /places

강릉시 관광지 목록.

Query:

```text
category?: string
keyword?: string
page?: int
size?: int
```

Response:

```json
{
  "content": [
    {
      "id": "string",
      "name": "string",
      "region": "강릉시 ...",
      "category": "string",
      "tags": ["string"],
      "thumbnailUrl": "string|null",
      "latitude": 37.0,
      "longitude": 128.0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### GET /places/{placeId}

상세 관광지.

### GET /places/{placeId}/nearby

주변 후보.

### GET /places/{placeId}/related

연관 관광지.

## 2. Composition

### POST /compositions

`multipart/form-data`

Fields:

```text
photo: required file
onePickId: required Place.id UUID
aspectRatio: optional 1:1 | 4:5 | 9:16 (default 4:5)
backgroundImageUrl: optional selected Type1 PlaceImage URL (originalImageUrls recommended)
```

Response:

```json
{
  "jobId": "string",
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

`onePickId`는 `GET /api/v1/places`가 반환한 `Place.id` UUID다. 별도 사진 소스의 표시용 ID를
UUID로 변환하지 않는다. 백엔드는 Type1 PlaceImage만 배경으로 선택하고, 저장된
`originalStorageKey`를 `PlaceImageStorage.open()`으로 읽는다. 저장 파일이 사라졌다면 기존
`ImageAssetCacheService`로 같은 원본 URL을 한 번 복구한다.

### GET /compositions/{jobId}

Response:

```json
{
  "jobId": "string",
  "status": "QUEUED|ANALYZING|COMPOSITING|QUALITY_CHECK|DONE|FAILED",
  "progress": 0,
  "stage": "string",
  "resultAvailable": false,
  "downloadUrl": null,
  "place": null,
  "error": null,
  "safety": null
}
```

DONE:

```json
{
  "jobId": "string",
  "status": "DONE",
  "progress": 100,
  "stage": "COMPLETED",
  "resultAvailable": true,
  "downloadUrl": "/api/v1/compositions/{jobId}/download",
  "place": null,
  "error": null,
  "safety": {
    "status": "PASSED",
    "reasonCode": null,
    "warnings": []
  }
}
```

현재 `place` 필드는 호환성을 위해 유지하는 예약 필드이며 실제 응답은 `null`이다. DONE 판별은
`status == "DONE" && resultAvailable == true && downloadUrl != null` 세 조건으로 한다.
polling 권장 간격은 1~2초이며 Agent의 얼굴 보존 재생성 때문에 전체 처리 시간에 고정된 짧은
타임아웃을 두지 않는다.

### GET /compositions/{jobId}/download

생성 이미지를 다운로드한다.

- 인증 없음
- job ownership 대신 충분히 추측하기 어려운 job ID를 사용
- TTL 만료 시 404/410
- Content-Disposition: attachment

### POST /compositions/{jobId}/retry

재시도 가능 상태에서만 허용.

- `status=FAILED`이고 `error.retryable=true`일 때 호출한다.
- 같은 백엔드 Job ID를 유지하면서 Agent에 새 generation을 생성하고 새 `providerJobId`를 저장한다.
- 사용자 원본 파일이 TTL 만료됐거나 비재시도 오류면 409/410을 반환한다.

## 3. Courses

### POST /courses

Request:

```json
{
  "placeIds": ["string"],
  "onePickId": "string",
  "types": ["active"],
  "companion": "couple",
  "duration": "day"
}
```

custom:

```json
{
  "duration": "custom",
  "startDate": "2026-08-08",
  "endDate": "2026-08-09"
}
```

추천 정책:

- `onePickId` 장소는 항상 첫 번째 정거장으로 유지한다.
- `types`는 최대 2개까지 사용할 수 있으며 장소의 `category`, 이름, 설명에 기반한 적합도 점수를 가산한다.
- `companion`은 현재 장소 데이터의 카테고리·이름·설명 기반 보정 점수로 반영한다.
- 조건에 맞지 않는 후보를 하드 필터링하지 않고 거리순 fallback을 사용한다.
- `day`는 원픽 외 최대 3개, `night1`은 원픽 외 최대 4개의 정거장을 추천한다.
- 날짜별 분리·숙박·다일 코스 중복 방지는 현재 계약 범위에 포함하지 않는다.

Response:

```json
{
  "courseId": "string",
  "title": "나만의 강릉 코스",
  "duration": "day",
  "stops": [
    {
      "sequence": 1,
      "placeId": "string",
      "name": "string",
      "thumbnailUrl": "string|null",
      "arrivalTime": "09:30",
      "stayMinutes": 60,
      "crowdLevel": "LOW",
      "isOnePick": true,
      "note": "string",
      "latitude": 37.0,
      "longitude": 128.0
    }
  ],
  "totalDistanceMeters": 0,
  "totalTravelMinutes": 0
}
```

### GET /courses/{courseId}

저장/공유에 사용할 코스 조회.

### DELETE /courses/{courseId}

익명 저장 코스 삭제.

## 4. Share

### POST /courses/{courseId}/share

Response:

```json
{
  "shareToken": "opaque-random-token",
  "shareUrl": "/share/courses/{shareToken}",
  "expiresAt": "..."
}
```

### GET /share/courses/{shareToken}

공유용 public response.

### DELETE /courses/{courseId}/share

공유 token 철회.

## 5. Route

### POST /routes/walking

Request:

```json
{
  "origin": {"latitude": 37.0, "longitude": 128.0},
  "destination": {"latitude": 37.1, "longitude": 128.1}
}
```

Response는 프론트가 지도 polyline을 그릴 수 있는 최소한의 normalized route shape으로 만든다.

Kakao 원문 response를 프론트에 그대로 노출하지 않는다.

코스 응답의 `arrivalTime`은 첫 장소 09:00부터 각 장소의 `stayMinutes`와 앞 구간의 도보 `durationSeconds`를 누적해 계산한다. 도보 API가 unavailable이면 구간 이동시간을 0으로 두고 체류시간만 반영한다. 카페·음식점은 코스 생성 시 자동 삽입하지 않고 `nearby-places` 조회 후 사용자가 추가한다.

백엔드 Kakao 도보 adapter는 Kakao Mobility Affiliate Walking의 `GET /affiliate/walking/v1/directions`를 사용한다. 이 endpoint는 `origin`, `destination`, `priority`, `summary` 파라미터와 `Authorization: KakaoAK <REST_API_KEY>` 헤더가 필요하며, 계정에 제휴 권한이 없으면 403이 반환될 수 있다.

## API 공통

성공:

- 2xx

클라이언트 오류:

- 400 validation
- 404 not found
- 409 invalid state/conflict
- 410 expired resource
- 413 file too large
- 415 unsupported media
- 429 rate limit

서버/외부 API:

- 500 internal
- 502 upstream failure
- 503 temporarily unavailable
- 504 upstream timeout
