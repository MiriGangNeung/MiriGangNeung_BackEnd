# 02 Frontend Contract

## Source of truth

현재 프론트 repository의 README와 App.jsx를 기준으로 한다.

프론트는 6개 화면을 사용한다.

1. BackgroundPicker
2. OnePickConfirm
3. PhotoUpload
4. CompositeResult
5. CourseOptions
6. CourseResult

## 현재 프론트 상태

```text
screen: 1..6
tab: filter id
picks: string[] <= 3
liked: Record<id, boolean>
onePick: place id
agreeA: boolean
agreeB: boolean
phase: ready | running | done
stageIndex: number
elapsed: number
types: string[] 1..2
companion: string
duration: day | night1 | custom
startDate/endDate: ISO date, currently meaningful for custom UI
activeStop: number
```

초기값은 Mock이며 백엔드 계약의 기본값으로 취급하지 않는다.

## Screen 1 — 장소 선택

필요 데이터:

```json
{
  "id": "string",
  "name": "string",
  "region": "string",
  "category": "string",
  "tags": ["string"],
  "thumbnailUrl": "string|null",
  "latitude": 0.0,
  "longitude": 0.0
}
```

필터/페이지네이션을 고려한 API:

```http
GET /api/v1/places
GET /api/v1/places/{placeId}
```

권장 query:

```text
category
keyword
page
size
```

기본 지역은 강릉시다.

## Screen 2 — 원픽

별도 저장 API가 필수는 아니다.

프론트가 `picks` 안에서 하나를 선택한다.

백엔드에서는 이후 composition/course 요청의 `onePickId`로 전달받는다.

## Screen 3 — 사진 업로드/합성

현재 프론트의 timer/stage advance는 Mock이다. 실제 구현에서는 Job API로 대체한다.

필수:

```http
POST /api/v1/compositions
GET /api/v1/compositions/{jobId}
POST /api/v1/compositions/{jobId}/retry
```

multipart request:

- `photo`
- `onePickId`
- 선택적 `aspectRatio`
- 선택적 generation option

Consent 여부는 실제 서비스 정책에 맞춰 별도 metadata로 전달하거나 프론트에서 필수 검증한다.

## Screen 4 — 합성 결과

필요:

- result image download URL
- generation status
- generatedAt
- selected place
- place description/region
- AI generated disclaimer

백엔드 response는 외부 AI URL을 그대로 노출하지 않는다. 필요한 경우 백엔드 다운로드 endpoint 또는 짧은 수명의 download token을 제공한다.

## Screen 5 — 코스 조건

프론트에서 현재 사용:

```json
{
  "placeIds": ["..."],
  "onePickId": "...",
  "types": ["active"],
  "companion": "couple",
  "duration": "day"
}
```

custom인 경우 날짜 값이 추가된다.

`duration`은 여행 길이 선택으로 해석한다.

- `day`: 당일치기
- `night1`: 1박 2일
- `custom`: 사용자 지정 기간

날짜는 custom에서만 현재 UI상 의미가 있다. 서버가 모든 코스 요청에 실제 달력 날짜를 강제하지 않는다.

## Screen 6 — 코스 결과

필요 stop 데이터:

```json
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
```

추가 metadata:

```text
courseId
title
duration
totalDistanceMeters
totalTravelMinutes
stops
```

## 지도

현재 프론트 prototype README는 Leaflet/OSM을 사용하지만 팀 결정은 **Kakao만 사용**하는 것이다.

목표:

- Kakao Maps JS SDK: 프론트 지도 렌더링
- Spring Boot → Kakao REST API: 경로 계산

## 중요: Mock 제거

다음은 실제 API 연동 시 제거/교체 대상이다.

- Mock places
- Mock course stops
- Fake compose timer
- Mock generated image
- 하드코딩된 total distance/time
- 하드코딩된 날짜/장소 데이터
