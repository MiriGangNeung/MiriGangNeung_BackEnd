# 장소 이미지 CDN 호환 저장 구조 설계

**작성일:** 2026-08-24
**상태:** 승인됨
**범위:** `MiriGangNeung_BackEnd`, `MiriGangNeung_FrontEnd`

## 목표

장소 카드가 화면을 열 때마다 한국관광공사 이미지 서버에서 큰 원본 파일을 직접 내려받지 않도록 한다. 장소 동기화 시 이미지를 한 번 저장하고, 화면에는 캐시 가능한 썸네일 URL을 반환하며, 원픽·AI 합성 단계에는 같은 이미지의 고화질 URL을 제공한다.

## 현재 문제

- `PlaceImage.imageUrl`에 관광공사 원본 URL만 저장되어 있다.
- `/api/v1/places`가 반환한 URL을 프론트의 `<img>`가 관광공사 서버에서 직접 요청한다.
- Redis는 장소 JSON만 캐시하며 이미지 바이트는 저장하지 않는다.
- 브라우저 카드에는 원본에 가까운 이미지가 여러 장 동시에 로드될 수 있다.
- 실제 CDN 계정·도메인은 아직 없으므로 로컬에서 글로벌 CDN의 성능을 직접 측정할 수 없다.

## 결정 사항

### 1. 원본 URL과 저장 파일을 분리한다

`PlaceImage`의 기존 `imageUrl`은 관광공사 원본 URL로 유지한다. 다음 메타데이터를 추가한다.

```text
sourceImageUrl       기존 imageUrl과 같은 관광공사 원본 URL
originalStorageKey   저장된 원본 파일 키
thumbnailStorageKey  카드용 썸네일 파일 키
contentType          원본 MIME type
originalByteSize     원본 바이트 수
thumbnailByteSize    썸네일 바이트 수
```

기존 DB 데이터와 테스트를 깨지 않도록 기존 생성자는 유지하고, 새 저장 키가 없는 행은 원본 URL을 fallback으로 사용한다.

### 2. 로컬 파일 저장소를 CDN 호환 origin으로 사용한다

현재 구현은 `IMAGE_STORAGE_DIR`에 파일을 저장하는 `LocalPlaceImageStorage`를 사용한다. Docker에서는 named volume으로 보존한다. 외부 공개 주소는 `IMAGE_PUBLIC_BASE_URL`로 분리한다.

```text
동기화
  관광공사 source URL
    -> ImageAssetCacheService
    -> LocalPlaceImageStorage
    -> original + thumbnail
    -> PlaceImage storage keys

화면
  /api/v1/places
    -> thumbnail URL: IMAGE_PUBLIC_BASE_URL/{thumbnailStorageKey}
    -> original URL:  IMAGE_PUBLIC_BASE_URL/{originalStorageKey}
  GET /media/images/{storageKey}
    -> 파일 스트리밍 + Cache-Control
```

저장소 인터페이스를 별도로 두어 이후 S3/R2/CloudFront를 연결할 때 장소 동기화와 API 계약을 바꾸지 않는다. 이번 작업은 실제 외부 CDN 상품을 등록하거나 인증키를 추가하지 않는다.

### 3. 동기화 시에만 원본을 복사한다

- 장소 목록·상세·Redis cache miss는 관광공사 이미지 API를 호출하지 않는다.
- `ImageAssetCacheService`는 source URL의 결정적 fingerprint를 파일 키로 사용한다.
- 같은 source URL의 파일이 이미 있으면 네트워크 요청 없이 재사용한다.
- 새 이미지가 HTTP 2xx이고 `Content-Type: image/*`이며 최대 다운로드 크기 이내일 때만 저장한다.
- 원본은 바이트를 보존하고, 썸네일은 Java `ImageIO`로 가로 최대 640px JPEG를 생성한다.
- 다운로드 실패 이미지는 목록에 노출하지 않는다. 기존에 같은 source URL로 유효한 캐시 파일이 있으면 그 파일을 유지한다.

### 4. API 응답은 썸네일과 원본을 함께 구분한다

기존 `imageUrls`와 `thumbnailUrl`은 카드용 썸네일 URL을 반환한다. 병렬 배열인 `originalImageUrls`를 추가하여 원픽 이미지의 순서를 보존한다. 상세 이미지 항목에는 다음 URL을 포함한다.

```json
{
  "imageUrl": "http://localhost:8080/media/images/place-...-thumb.jpg",
  "originalImageUrl": "http://localhost:8080/media/images/place-....jpg",
  "sourceImageUrl": "https://tong.visitkorea.or.kr/...",
  "title": "경포해변",
  "source": "KTO",
  "sortOrder": 0,
  "copyrightCode": "Type1"
}
```

캐시되지 않은 기존 행은 세 URL 모두 기존 원본 URL로 fallback한다. Type1 필터와 PhotoGalleryService1 보충 정책은 그대로 유지한다.

### 5. 프론트는 카드에서 썸네일을 사용한다

- 카드와 목록 이미지는 cached thumbnail URL을 사용한다.
- 카드 이미지는 `loading="lazy"`, `decoding="async"`로 설정한다.
- 왼쪽 hero와 원픽 확인처럼 첫 화면에 즉시 필요한 이미지는 eager 로딩을 유지한다.
- `placeImageIndexes`와 `imageUrls`/`originalImageUrls`의 같은 index를 사용하여 1/5 중 선택한 사진이 원픽·합성 단계에서도 같은 순서를 유지한다.

## 성능 측정

같은 백엔드와 같은 장소 샘플을 기준으로 적용 전후를 비교한다.

### 기록할 지표

- 장소 목록 JSON 응답 TTFB와 바이트 수
- 샘플 이미지 수와 성공률
- 이미지별 TTFB p50/p95
- 이미지별 전체 응답시간 p50/p95
- 카드 이미지 전체 다운로드 바이트
- 카드 이미지 평균 바이트

### 비교 단계

1. 기존 코드가 반환하는 관광공사 URL을 대상으로 baseline 측정
2. 이미지 캐시 동기화 후 같은 장소 순서로 내부 origin URL을 대상으로 측정
3. 내부 origin의 첫 요청과 반복 요청을 각각 기록
4. 이미지 바이트 감소율과 TTFB 변화율을 계산

실제 Cloudflare/S3/CloudFront edge가 없는 로컬에서는 글로벌 CDN 개선율을 주장하지 않는다. 보고서에는 `외부 관광공사 원본 → 로컬 캐시 origin`의 측정값과, 실제 CDN 배포 시 동일한 캐시 헤더를 적용할 수 있다는 점을 분리해 기록한다.

## 제외 범위

- Base64를 JSON에 넣지 않는다.
- Redis에 이미지 binary를 넣지 않는다.
- AI Provider를 새로 선택하거나 구현하지 않는다.
- 관광공사 저작권 정책을 임의로 확대 해석하지 않는다. Type1과 기존 attribution metadata를 유지하고, 실제 공개 CDN 배포 전에는 사용 조건을 재확인한다.
