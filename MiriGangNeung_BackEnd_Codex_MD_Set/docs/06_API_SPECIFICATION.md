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
onePickId: required string
aspectRatio: optional string
```

Response:

```json
{
  "jobId": "string",
  "status": "QUEUED"
}
```

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
  "error": null
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
  "place": {
    "id": "string",
    "name": "string",
    "region": "string",
    "description": "string"
  }
}
```

### GET /compositions/{jobId}/download

생성 이미지를 다운로드한다.

- 인증 없음
- job ownership 대신 충분히 추측하기 어려운 job ID를 사용
- TTL 만료 시 404/410
- Content-Disposition: attachment

### POST /compositions/{jobId}/retry

재시도 가능 상태에서만 허용.

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
