# 관광사진 소스 탭 설계

## 목적

1번 배경 선택 화면에서 한국관광공사 사진을 두 소스로 비교한다.

- 관광공모전(사진) 수상작: 전국 수상작을 활용한다.
- 관광사진 갤러리: `PhotoGalleryService1`의 일반 관광사진을 활용한다.

현재 API 확인 결과 공모전 수상작은 전국 약 95건, 관광사진 갤러리는 약 6,118건이다. API 데이터는 갱신될 수 있으므로 이 수치는 구현 시점의 참고값이며 계약에 하드코딩하지 않는다.

## 범위와 비범위

포함 범위:

- 공모전 수상작의 기존 강원도 제한 제거
- `PhotoGalleryService1/galleryList1` 연동
- 두 소스의 읽기 전용 백엔드 API
- 배경 선택 화면의 출처 탭
- 원본 이미지 우선 및 썸네일 fallback
- 두 소스의 단위 테스트와 실제 API·이미지 smoke test

비범위:

- 사진 데이터를 DB에 저장하거나 `places`/`place_images`에 동기화하지 않는다.
- 사진을 실제 관광지 장소 데이터로 변환하지 않는다.
- 공모전·갤러리 사진에 대한 지역명 추론이나 좌표 지오코딩을 하지 않는다.
- 이미지 품질 점수화, 중복 제거 알고리즘, 무한 스크롤은 이번 작업에서 다루지 않는다.

## 백엔드 설계

### 공모전 수상작 API

기존 `GET /api/v1/award-photos` 계약을 유지하되 기본 조회에서 `lDongRegnCd=51`을 제거한다.

실제 KTO 요청:

```text
GET https://apis.data.go.kr/B551011/PhokoAwrdService/phokoAwrdSyncList
  ?serviceKey=...
  &numOfRows=100
  &pageNo=1
  &MobileOS=ETC
  &MobileApp=MiriGangNeung
  &arrange=C
  &showflag=1
  &_type=json
```

향후 지역별 조회가 필요할 수 있으므로 내부 클라이언트와 서비스는 선택적 `region` 값을 받을 수 있게 유지한다. 프론트의 전국 조회 요청에는 `region`을 보내지 않는다.

기존 응답 필드는 유지하고 `source: "KTO_AWARD"`를 계속 반환한다.

### 관광사진 갤러리 API

새 읽기 전용 API를 추가한다.

```text
GET /api/v1/tourism-photos?page=0&size=100
```

KTO 요청:

```text
GET https://apis.data.go.kr/B551011/PhotoGalleryService1/galleryList1
  ?serviceKey=...
  &numOfRows=100
  &pageNo=1
  &MobileOS=ETC
  &MobileApp=MiriGangNeung
  &arrange=C
  &_type=json
```

`galleryList1`은 제목 기준으로 그룹화된 목록을 제공하므로 배경 선택용 첫 페이지에는 이 목록을 사용한다. 응답 매핑은 다음과 같다.

| KTO 필드 | 백엔드 필드 |
| --- | --- |
| `galContentId` | `id` |
| `galTitle` | `title` |
| `galPhotographyLocation` | `location` |
| `galPhotographyMonth` | `photographyMonth` |
| `galPhotographer` | `photographer` |
| `galSearchKeyword` | `keywords` |
| `galWebImageUrl` | `originalImageUrl` |
| `galWebImageUrl` | `thumbnailUrl` |

응답의 `source`는 `KTO_PHOTO_GALLERY`로 고정한다. 갤러리 API가 별도 썸네일 필드를 제공하지 않으므로 웹용 이미지 URL을 두 이미지 필드에 넣고 프론트에서는 동일한 URL을 사용한다.

### 공통 외부 API 처리

- 공모전과 갤러리는 각각 독립적인 API client, properties, service, controller를 갖는다.
- 공통 `TourApiResponseParser`로 JSON/XML envelope를 해석한다.
- 서비스 키는 환경변수에서 한 번 URL decode한 후 `URLEncoder`로 query parameter를 다시 인코딩하고 `build(true)`로 URI를 만든다. `+`, `/`, `=`가 포함된 관광공사 키가 공백이나 query 구분자로 오해되지 않도록 한다.
- 두 이미지 URL이 모두 비어 있는 항목은 응답에서 제외한다.
- 갤러리 이미지가 `http://tong.visitkorea.or.kr`로 반환되면 브라우저 mixed content를 피하기 위해 동일 호스트의 `https` URL로 정규화한다.
- 원격 오류는 기존 `TOUR_API_ERROR`/HTTP 502 계약으로 변환한다.
- 각 서비스 키는 분리 설정할 수 있고, 별도 키가 없으면 기존 `TOUR_API_KEY`를 fallback으로 사용한다.

설정 이름:

```yaml
tour:
  award:
    key: ${TOUR_AWARD_API_KEY:${TOUR_API_KEY:}}
  photo-gallery:
    base-url: ${TOUR_PHOTO_GALLERY_API_BASE_URL:https://apis.data.go.kr/B551011/PhotoGalleryService1}
    key: ${TOUR_PHOTO_GALLERY_API_KEY:${TOUR_API_KEY:}}
    timeout: ${TOUR_PHOTO_GALLERY_API_TIMEOUT:5s}
```

Docker Compose에도 `TOUR_PHOTO_GALLERY_API_KEY`를 전달하고, 값이 없으면 `TOUR_API_KEY`를 사용한다.

## 프론트엔드 설계

### 데이터 모델

기존 `Place` 모델에 선택적 사진 출처를 추가한다.

```ts
type BackgroundPhotoSource = 'award' | 'gallery';
```

각 adapter는 기존 화면이 사용하는 `Place`로 변환한다.

- 공모전 ID: `kto-award:<contentId>`
- 갤러리 ID: `kto-gallery:<galContentId>`
- 이름: 제목
- 지역: 촬영 장소
- 카테고리: `nature`
- 좌표: `0, 0` (좌표를 제공하지 않는 사진 데이터의 표시용 값)
- 이미지: 원본 우선, fallback 이미지
- 출처: `award` 또는 `gallery`

두 endpoint를 병렬 조회해 하나의 캐시된 목록으로 합친다. 따라서 갤러리 사진을 선택한 뒤 다음 화면에서 같은 ID를 다시 찾을 수 있다. 두 요청이 모두 실패한 경우에만 기존 전체 오류 상태를 표시하고, 한 소스만 실패한 경우 성공한 소스는 계속 표시한다.

### 출처 탭과 기존 필터

1번 화면 상단에 다음 출처 탭을 추가한다.

- `공모전 수상작`
- `관광사진 갤러리`

출처 탭이 먼저 사진 목록을 제한하고, 기존 `all`/카테고리 필터가 그 결과에 적용된다. 두 데이터 소스 모두 자연 사진으로 분류하므로 기존 자연 필터에서도 표시된다.

기본 출처는 `award`로 둔다. 기존 선택 순서, 최대 선택 수, 이미지 카드, hero 이미지는 유지한다.

## 테스트와 검증

백엔드:

- 공모전 client 테스트에서 지역 파라미터 없이 전국 요청이 생성되는지 검증한다.
- 갤러리 client 테스트에서 `galleryList1` 경로, `arrange=C`, 필드 매핑, 이미지 필터, 키 인코딩을 검증한다.
- 각 service/controller 테스트에서 응답 source와 페이지 계약을 검증한다.
- 두 API가 모두 비어 있거나 하나만 실패하는 경우의 결합 동작을 검증한다.

프론트엔드:

- 갤러리 응답 adapter의 필드·ID·source·이미지 fallback을 검증한다.
- 두 endpoint 요청 URL과 결과 병합을 검증한다.
- 출처 탭이 선택된 source만 카드에 표시하는지 검증한다.

런타임:

- `/api/v1/award-photos?page=0&size=100`이 전국 결과를 반환하는지 확인한다.
- `/api/v1/tourism-photos?page=0&size=100`이 갤러리 결과를 반환하는지 확인한다.
- 두 응답에서 선택한 이미지 URL이 `200 image/*`인지 확인한다.

## 알려진 제한

두 API의 사진 ID는 배경 선택 화면의 표시용 식별자다. 실제 관광지 `Place` ID가 아니므로 코스 생성·상세 관광지 API의 장소 식별자로 직접 사용할 수 없다. 이 POC에서는 사진 품질 확인을 우선하며, 장소 연결은 별도 기능으로 남긴다.
