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
5. 필요하면 주변 장소 조회 후 코스에 추가
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
  "types": ["food", "rest", "culture", "nature"],
  "detailTypes": ["food:chinese", "food:japanese", "rest:coffee"],
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
  "types": ["food", "rest", "culture", "nature"],
  "detailTypes": ["food:chinese", "food:japanese", "rest:coffee"],
  "companion": "couple",
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

KTO 원본 장소의 `placeUrl`은 버전 관리되는 `src/main/resources/data/kakao-place-mappings.csv`의 `tourContentId` 매핑으로 우선 채워진다. 현재 기본 카드 69개 중 68개는 Kakao 상세 URL을 가지며, `강릉 명주동 거리`는 의도적으로 빈 매핑이라 `null`로 유지된다. 이 URL은 리뷰 원문을 백엔드가 저장하는 값이 아니라 프론트가 기존 Kakao 장소 iframe으로 여는 링크다. 매핑되지 않은 장소는 `null`이며 리뷰 버튼을 표시하지 않는다.

### 코스 장소 검색

장소 추가 패널은 카페·음식점·문화시설·관광명소 중 카테고리를 먼저 고른 뒤, 해당 카테고리 안에서 주변 추천·강릉 전체 검색·강릉 대표를 선택하는 2단 구조다. 백엔드가 조회하는 카테고리 코드는 카페(`CE7`), 음식점(`FD6`), 문화시설(`CT1`), 관광명소(`AT4`)다. 강릉 대표는 현재 준비 중이며 외부 API를 호출하지 않는다. 카카오 REST 키는 백엔드의 `KAKAO_API_KEY`로만 설정한다.

```http
# 선택한 관광지 최대 3곳 주변을 자동 확장해 추천(기본 2km)
GET /api/v1/courses/{courseId}/nearby-places?scope=nearby&category=cafe
GET /api/v1/courses/{courseId}/nearby-places?scope=nearby&category=cafe&stopId={stopId}&sort=distance

# 강릉 전체 영역에서 사용자가 제출한 장소명 키워드 검색
GET /api/v1/courses/{courseId}/nearby-places?scope=all&category=restaurant&keyword=테라로사&page=0&size=15
```

`nearby`에서 `stopId`를 생략하면 코스의 모든 관광지 주변을 합쳐 조회하고, 지정하면 해당 관광지 주변만 조회한다. 검색 반경은 사용자에게 선택받지 않고 `2km → 5km → 10km → 15km` 순서로 자동 확장한다. 세부 취향이 있는 코스에서 현재 반경 안의 정확한 취향 일치 후보가 3개 미만이면 다음 반경을 조회하며, 최대 15km까지 확장한다. 모든 확장 결과는 장소 ID를 기준으로 중복 제거해 합치며, 확장으로 새로 들어온 장소의 `recommendationReasons`에는 검색 확장 사실이 포함된다. 응답 최상위 `searchRadiusMeters`에는 마지막으로 시도한 반경을 담고, 2km를 초과한 경우 프론트는 자동 확장 안내만 표시한다. `sort`는 `recommended`(기본값) 또는 `distance`이며, 추천순은 코스의 여행 타입·세부 취향·동행 유형·거리·정보 완성도를 사용한 0~100점 내림차순이다. `nearby`의 `size`는 Kakao 페이지 요청 크기(1~15)이고, `places`는 여러 관광지·페이지 결과를 합친 목록이므로 15개를 초과할 수 있다. 후보가 다른 관광지에서 더 가까운 결과로 교체되어도 최초 확장 단계 안내는 유지한다. `nearby` 응답은 기존처럼 거리와 가장 가까운 관광지 정보를 포함한다.

`all`은 사용자가 검색어를 제출한 경우에만 Kakao Local API의 `rect` 키워드 검색을 사용해 Gangneung 사각 영역을 조회한다. 검색어가 비어 있으면 외부 API를 호출하지 않고 빈 결과를 반환한다. rect 검색은 기준 좌표가 없으므로 Kakao에는 `accuracy` 정렬을 사용한다. `page`는 0부터 시작하는 0~44 범위이고 `size`는 1~15이며, 응답의 `isEnd`가 false이면 다음 페이지를 요청할 수 있다. `all` 응답의 `distanceMeters`, `nearestStopId`, `nearestStopName`, `recommendationScore`, `recommendationReasons`는 항상 null/빈 배열이다. 현재 코스에 이미 있는 Kakao ID(카탈로그 장소에 연결된 ID와 외부 장소 snapshot ID 모두) 또는 이름과 일치하는 후보는 제외한다.

`all` 검색 영역은 `KAKAO_LOCAL_ALL_SEARCH_RECT` 환경변수로 설정하며 형식은 Kakao 문서 기준 `leftX,leftY,rightX,rightY`다. 기본값은 `128.70,37.95,129.05,37.65`다.

추천 점수는 Kakao 응답에 실제로 존재하는 장소명·카테고리명·주소·좌표·상세 URL만 사용한다. 정확한 세부 선호 일치는 55점, 관련 세부 유형은 42점, 주소에서만 확인된 세부 유형은 44점, 세부 선호를 확인할 수 없거나 다른 음식 분류인 후보는 20점, 넓은 여행 유형은 32점을 기준으로 합산한다. 거리 20점, 동행 유형 10점, 정보 완성도 10점을 추가하며, 주변 카페 추천에서 대형 프랜차이즈 토큰은 8점 감점한다. 세부 취향이 달라도 후보에서 제외하지 않고 중립 점수로 유지한다. 리뷰·별점·인기도·사진을 추정하지 않으며, `recommendationScore`가 점수이고 `recommendationReasons`가 최대 3개의 설명이다. 선호값이 없는 기존 코스는 두 필드를 각각 `null`·빈 배열로 반환하고 거리순과 같은 결과로 fallback한다.

주변 장소 항목의 추가 필드는 다음과 같다.

```json
{
  "scope": "nearby",
  "category": "cafe",
  "page": 0,
  "size": 15,
  "isEnd": true,
  "searchRadiusMeters": 2000,
  "places": [
    {
      "externalPlaceId": "kakao-place-id",
      "name": "안목 바다 카페",
      "category": "cafe",
      "distanceMeters": 150,
      "nearestStopName": "경포해변",
      "recommendationScore": 93,
      "recommendationReasons": [
        "휴식 취향에 맞는 장소예요",
        "커플과 잘 어울리는 장소예요",
        "관광지에서 가까워요"
      ]
    }
  ]
}
```

실제 응답은 `scope`, `category`, `page`, `size`, `isEnd`, `searchRadiusMeters`, `places`를 최상위에 두고 위 항목을 `places` 배열에 담는다. `searchRadiusMeters`는 `nearby`에서 마지막 자동 검색 반경을 나타내며 `scope=all`에서는 null이다. `scope=all` 응답은 거리·추천 메타데이터도 null/빈 배열이다.

허용되지 않은 `sort` 값은 HTTP 400(`INVALID_SORT`)으로 반환한다.

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

추가 시 Kakao 응답을 코스 전용 snapshot으로 DB에 저장하고 `CourseStop`을 마지막 순서에 붙인다. 전역 관광지 카탈로그에는 추가하지 않는다. 기존 KTO 관광지와 이름이 정규화되어 일치하는 관광명소·문화시설은 추가 요청에서도 거절한다.

```http
DELETE /api/v1/courses/{courseId}/stops/{stopId}
PUT /api/v1/courses/{courseId}/stops/order
Content-Type: application/json
```

```json
{ "stopIds": ["stop-uuid-2", "stop-uuid-1"] }
```

원픽 장소는 삭제할 수 없으며, 순서 변경 요청은 현재 코스의 모든 `stopId`를 중복 없이 정확히 한 번씩 포함해야 한다. 추가·삭제·순서 변경 후에는 도보 거리·시간과 `routeSegments`를 다시 계산한다.

외부 연동 기준은 [Kakao Local 카테고리 검색 가이드](https://developers.kakao.com/docs/ko/local/dev-guide)와 [Kakao 지도 REST API 가이드](https://developers.kakao.com/docs/ko/kakaomap/rest-api)다.

### KTO 장소 Kakao URL 보강

KTO 장소의 사진·설명은 계속 KTO에서 제공한다. 기본 카드의 Kakao URL은 동기화 직후 고정 CSV를 `tourContentId`로 조회해 `places`에 저장하며, 이 단계에서는 Kakao API를 호출하지 않는다. 새로 들어온 장소나 CSV에 없는 장소를 자동으로 찾고 싶을 때만 Kakao Local 키워드 검색을 장소명·좌표와 함께 호출한다. 양쪽 이름을 동일한 정규화 규칙으로 비교해 정확히 일치하고 좌표가 검색 반경 안에 있는 결과만 `kakaoPlaceId`와 `kakaoPlaceUrl`로 저장한다. 화면 요청마다 Kakao를 호출하지 않는다.

전체 KTO 동기화를 실행하려면 `.env`에서 `TOUR_API_SYNC_ON_STARTUP=true`로 실행한다. 실행 순서는 KTO 동기화 → 선택적 Kakao API 보강(`KAKAO_PLACE_ENRICHMENT_ON_STARTUP=true`) → 고정 CSV 매핑 적용이다. 따라서 CSV에 있는 수동 매핑과 명시적 `null` 정책이 최종값이 된다. Kakao 키가 없거나 매칭되지 않는 장소는 동기화를 실패시키지 않고 URL 없이 남는다.

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

코스 생성·장소 추가·삭제·순서 변경 응답은 인접한 코스 장소 간 Kakao 도보 경로를 합산한 `totalDistanceMeters`, `totalTravelMinutes`, `routeSegments`를 포함한다. 외부 API를 사용할 수 없을 때도 장소 목록은 반환하고 `routeStatus=UNAVAILABLE`로 표시한다.

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
