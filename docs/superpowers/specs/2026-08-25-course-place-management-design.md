# 코스 주변 음식점·카페 및 정거장 관리 설계

## 배경과 목표

현재 코스 결과 화면은 선택한 관광지를 프론트 mock으로 조합하고, 장소 추가는 브라우저의 Kakao Maps JavaScript SDK를 직접 호출해 세션에만 임시로 붙인다. 백엔드에는 관광지 코스 생성 API가 있지만 프론트와 연결되지 않았고, 음식점·카페 검색·외부 장소 저장·정거장 순서 저장 API가 없다.

이번 작업은 백엔드를 코스의 단일 데이터 소스로 만들고 다음 흐름을 완성한다.

```text
관광지 최대 3곳 선택
→ 백엔드 POST /courses
→ 코스 결과 표시
→ 음식점/카페 탭 선택
→ 선택 관광지 전체 주변 2km 검색 결과 통합·중복 제거·거리순 정렬
→ 외부 장소를 코스에 추가
→ 정거장 삭제 또는 드래그 앤 드롭 순서 변경
→ 백엔드 저장
→ Kakao 도보 경로·구간 거리·총 이동시간 재계산
```

## 확정된 제품 결정

- 음식점과 카페는 장소 추가 패널의 별도 탭으로 제공한다. Kakao category code는 각각 `FD6`, `CE7`이다.
- 주변 검색은 원픽 1곳이 아니라 코스에 포함된 관광지 최대 3곳 전체를 기준으로 한다.
- 각 관광지에서 반경 `2,000m` 이내 결과를 가져온다.
- 여러 관광지 검색에 동시에 걸린 외부 장소는 Kakao 장소 ID로 한 번만 표시한다.
- 통합 결과는 가장 가까운 관광지까지의 거리 오름차순으로 정렬하고, 카드에 가장 가까운 기준 관광지명과 거리를 표시한다.
- 사용자 브라우저 GPS는 사용하지 않는다. 선택한 관광지의 저장 좌표만 사용한다.
- 외부 장소는 코스에 추가하는 순간 스냅샷으로 DB에 저장한다. 새로고침·코스 조회·공유에서도 유지한다.
- 추가 장소는 기본적으로 코스 마지막 정거장에 붙인다.
- 관광지·외부 장소 모두 드래그 앤 드롭으로 순서를 바꿀 수 있다.
- 정거장 삭제는 원픽을 제외하고 허용한다. 삭제 후 sequence를 다시 매긴다.
- 순서·추가·삭제 요청은 낙관적 UI를 사용하되 서버 실패 시 직전 응답 상태로 복구한다.
- Kakao REST API 키는 백엔드 환경변수로만 사용한다. 프론트에는 지도 JavaScript 키만 남긴다.
- 외부 검색의 정상 빈 결과와 Kakao 권한·쿼터·timeout·5xx 오류를 서로 다른 상태로 표시한다.

## 시스템 구조

### 백엔드

외부 연동은 기존 `Controller → Service → Repository` 구조와 별도의 Client adapter 경계를 따른다.

- `KakaoLocalClient`: 여러 관광지 좌표에 대해 `category.json`을 호출하고 Kakao 원문을 내부 `NearbyPlace`로 정규화한다.
- `KakaoRouteClient`: 기존 자동차 directions 호출을 Kakao 도보 경로 API로 교체하고 거리·시간·polyline을 정규화한다.
- `CourseService`: 코스 생성, 주변 장소 검색, 외부 장소 스냅샷 추가, 삭제, 순서 변경을 하나의 코스 도메인 규칙으로 관리한다.
- `CourseExternalPlace`: Kakao 장소를 코스 시점에 고정하는 스냅샷 엔티티다. 관광공사 `Place`에 섞지 않는다.
- `CourseStop`: 기존 관광공사 장소 또는 외부 스냅샷 중 하나를 가리키며 sequence를 가진다.
- `CourseRouteCalculator`: 현재 정거장 순서의 인접 구간을 Kakao 도보 API로 계산해 총거리·총시간·polyline을 만든다.

### 프론트엔드

- `CourseOptionsPage`가 실제 코스 생성 요청을 보내고 `courseId`를 세션 상태에 저장한다.
- `useCourseQuery`가 `GET /courses/{courseId}`를 읽고 CourseResult에 전달한다.
- `courseApi.ts`가 코스 생성·주변 검색·외부 장소 추가·삭제·순서 저장 요청을 담당한다.
- `CourseResult`는 음식점/카페 탭, 로컬 검색어 필터, 외부 장소 추가/삭제, drag-and-drop을 렌더링한다.
- `CourseResultPage`가 서버 응답을 기준으로 낙관적 상태와 실패 복구를 관리한다.
- `CourseMap`은 코스 응답의 route points를 표시하고, 정거장 순서와 마커 번호를 동일한 배열에서 렌더링한다.

## API 계약

### 코스 생성

기존 계약을 유지한다.

```http
POST /api/v1/courses
```

```json
{
  "placeIds": ["place-id-1", "place-id-2", "place-id-3"],
  "onePickId": "place-id-1",
  "types": ["nature"],
  "companion": "couple",
  "duration": "day",
  "startDate": "2026-08-25",
  "endDate": "2026-08-25"
}
```

### 주변 음식점·카페 조회

```http
GET /api/v1/courses/{courseId}/nearby-places?category=restaurant
GET /api/v1/courses/{courseId}/nearby-places?category=cafe
```

응답은 프론트가 Kakao 원문을 알 필요가 없도록 정규화한다.

```json
{
  "category": "cafe",
  "places": [
    {
      "externalPlaceId": "12345",
      "name": "카페 예시",
      "categoryName": "음식점 > 카페",
      "address": "강원특별자치도 강릉시 ...",
      "roadAddress": "강원특별자치도 강릉시 ...",
      "phone": "033-000-0000",
      "placeUrl": "https://place.map.kakao.com/12345",
      "latitude": 37.77,
      "longitude": 128.94,
      "distanceMeters": 450,
      "nearestStopId": "course-stop-uuid",
      "nearestStopName": "안목해변"
    }
  ]
}
```

서버는 코스의 관광공사 장소 정거장만 검색 기준으로 사용하고, 외부 장소 정거장은 다시 주변 기준에 포함하지 않는다. 각 기준점의 Kakao 결과를 합친 뒤 `externalPlaceId`로 중복 제거한다.

### 외부 장소 추가

```http
POST /api/v1/courses/{courseId}/external-stops
```

```json
{
  "externalPlaceId": "12345",
  "category": "cafe",
  "name": "카페 예시",
  "categoryName": "음식점 > 카페",
  "address": "강원특별자치도 강릉시 ...",
  "roadAddress": "강원특별자치도 강릉시 ...",
  "phone": "033-000-0000",
  "placeUrl": "https://place.map.kakao.com/12345",
  "latitude": 37.77,
  "longitude": 128.94
}
```

서버는 허용 category, 필수 문자열, 유효한 WGS84 좌표, 코스 중복을 검증한 후 snapshot과 새 `CourseStop`을 같은 트랜잭션에서 저장한다. 식별자는 `source + externalPlaceId` 의미를 가지며 현재 source는 `KAKAO`다.

### 정거장 삭제

```http
DELETE /api/v1/courses/{courseId}/stops/{stopId}
```

원픽 삭제는 `COURSE_ONE_PICK_REQUIRED` 오류로 거부한다. 나머지 정거장을 삭제한 뒤 sequence를 1부터 다시 매기고 route summary를 재계산한다.

### 정거장 순서 저장

```http
PATCH /api/v1/courses/{courseId}/stops/order
```

```json
{ "stopIds": ["stop-3", "stop-1", "stop-2"] }
```

서버는 코스에 속한 모든 정거장이 정확히 한 번씩 포함되었는지, 중복·누락·타 코스 정거장이 없는지 확인한 뒤 하나의 트랜잭션에서 sequence를 갱신한다.

### CourseResponse 확장

기존 필드는 유지하고 다음 필드를 추가한다.

- `stopId`: 순서 저장·삭제에 사용하는 `CourseStop` ID
- `placeId`: 관광공사 장소 ID. 외부 정거장은 `null`
- `externalPlaceId`: Kakao 장소 ID. 관광공사 정거장은 `null`
- `kind`: `TOURISM`, `RESTAURANT`, `CAFE`
- `address`, `placeUrl`
- `routeStatus`: `READY`, `UNAVAILABLE`
- `routeSegments`: 인접 정거장별 거리·시간·polyline

기존 `name`, `thumbnailUrl`, `arrivalTime`, `latitude`, `longitude` 등은 계속 제공한다. 외부 장소에는 공식 이미지가 보장되지 않으므로 지도 링크와 주소를 우선 표시하고 썸네일은 선택값으로 둔다.

## 실패 처리

| 상황 | 응답/화면 |
| --- | --- |
| 선택 관광지에 좌표 없음 | 코스 생성 시 좌표 없는 후보 제외, 원픽 좌표 없으면 422 |
| 주변 장소 없음 | 200 + 빈 `places`, “주변에 장소가 없어요” |
| Kakao key 없음 | 503 + `KAKAO_API_NOT_CONFIGURED`, 설정 안내 |
| Kakao 401/403/429/5xx/timeout | 502 + `KAKAO_API_ERROR`, 빈 결과로 위장하지 않음 |
| 외부 장소 중복 추가 | 409 + `COURSE_STOP_ALREADY_EXISTS` |
| 원픽 삭제 | 409 + `COURSE_ONE_PICK_REQUIRED` |
| 순서 배열 중복·누락·타 코스 ID | 400 + `INVALID_STOP_ORDER` |
| 저장 실패 | 프론트가 서버의 직전 CourseResponse로 복구하고 오류 toast |

## 테스트 전략

### 백엔드

- Kakao Local client: category code, x/y/radius/sort, 응답 매핑, HTTP 오류
- 주변 장소 service: 3개 관광지 호출, Kakao ID 중복 제거, 최소 거리 기준 정렬·기준 관광지 부여
- 외부 snapshot 추가: 허용 category·좌표 검증, 중복 방지, sequence append
- 순서 변경: 전체 ID 검증, sequence 갱신, route 재계산
- 삭제: 원픽 보호, sequence 재정렬, route 재계산
- CourseResponse: 관광공사/외부 정거장 필드 매핑
- 기존 전체 Gradle 테스트 유지

### 프론트

- API request/response mapping과 오류 변환
- courseId를 포함한 코스 생성·조회 query
- 카테고리 탭·검색어 필터·중복 외부 장소 표시
- drag-and-drop 순서 계산과 실패 복구
- 추가·삭제 후 카드/지도 배열 동기화
- 기존 전체 Vitest, lint, production build

## 범위 밖

- 사용자 인증·코스 소유권 검증은 현재 저장소에 인증이 없으므로 이번 범위에 추가하지 않는다.
- Kakao 외부 장소의 운영시간·메뉴 상세 조회는 추가하지 않는다.
- 자동 최적화가 사용자가 고른 순서를 덮어쓰는 기능은 추가하지 않는다.
- 외부 장소의 전역 Place 카탈로그화·장기 갱신 job은 하지 않는다.
