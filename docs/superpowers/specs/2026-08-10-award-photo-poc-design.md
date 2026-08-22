# 공모전 수상작 배경 이미지 POC 설계

## 1. 목표

첫 번째 배경 선택 화면에서 기존 국문 관광정보(`KorService2`)의 대표 이미지 대신 한국관광공사 관광공모전 사진 수상작을 보여주고, 실제 이미지 품질을 빠르게 검증한다.

이번 작업은 사진 품질 확인을 위한 POC다. 수상작을 실제 관광지의 정식 대표 이미지나 코스 생성 데이터로 확정하지 않는다.

## 2. 범위

### 포함

- 한국관광공사 `PhokoAwrdService/phokoAwrdSyncList` 호출
- 표출 가능한 수상작만 조회한다.
- 강원 지역 코드 `lDongRegnCd=51`을 기본 필터로 사용한다.
- 수상작 원본 이미지(`orgImage`)와 썸네일(`thumbImage`)을 응답한다.
- 백엔드에 별도 `award-photos` API를 만들고 프론트 첫 화면이 이 API를 사용한다.
- 수상작 제목, 촬영 장소, 수상 등급, 키워드를 카드에 표시한다.

### 제외

- 기존 `KorService2` 코드 삭제 또는 데이터베이스 마이그레이션
- 수상작을 `places` 테이블에 저장
- 수상작 ID를 실제 관광지 ID로 간주
- AI 합성 모델에 참조 이미지를 전달하는 기능
- 수상작과 관광지의 정확한 좌표 매칭

## 3. API 및 데이터 흐름

```text
Frontend BackgroundPicker
        |
        v
GET /api/v1/award-photos?page=0&size=100&region=51
        |
        v
Backend AwardPhotoController
        |
        v
PhokoAwrdService/phokoAwrdSyncList
        |
        v
AwardPhotoResponse -> frontend display adapter -> cards
```

KTO 요청은 다음 기본 파라미터를 사용한다.

- `numOfRows`: 요청 페이지 크기
- `pageNo`: 1부터 시작하는 KTO 페이지 번호
- `MobileOS=ETC`
- `MobileApp=MiriGangNeung`
- `arrange=C` (수정일순)
- `showflag=1` (표출 콘텐츠만)
- `lDongRegnCd=51` (강원)
- `_type=json`

## 4. 백엔드 계약

`GET /api/v1/award-photos` 응답은 기존 장소 API와 구분되는 아래 형태로 제공한다.

```json
{
  "content": [
    {
      "id": "award-content-id",
      "title": "수상작 제목",
      "location": "촬영 장소",
      "award": "디지털카메라 부문 [금상]",
      "keywords": ["바다", "일출"],
      "originalImageUrl": "https://.../image2_1.jpg",
      "thumbnailUrl": "https://.../image3_1.jpg",
      "photographer": "촬영자",
      "copyrightCode": "Type1",
      "source": "KTO_AWARD"
    }
  ],
  "page": 0,
  "size": 100,
  "totalElements": 95,
  "totalPages": 1
}
```

KTO 응답 필드 매핑:

| KTO 필드 | API 필드 | 용도 |
| --- | --- | --- |
| `contentId` | `id` | 수상작 원본 식별자 |
| `koTitle` | `title` | 카드 제목 |
| `koFilmst` | `location` | 촬영 장소 |
| `koWnprzDiz` | `award` | 수상 등급 |
| `koKeyword` | `keywords` | 검색·카드 태그 |
| `orgImage` | `originalImageUrl` | 고품질 배경 후보 |
| `thumbImage` | `thumbnailUrl` | 목록 표시 보조 이미지 |
| `koCmanNm` | `photographer` | 출처 표시용 |
| `cpyrhtDivCd` | `copyrightCode` | 저작권 표시·후속 검토 |

## 5. 프론트 적용

기존 `usePlacesQuery`의 호출 대상을 POC 기간에만 `/award-photos`로 교체한다. 프론트 카드 컴포넌트가 이미 사용하는 최소 필드는 다음과 같이 어댑트한다.

- `id`: `kto-award:{contentId}`
- `name`: `title`
- `region`: `location`
- `thumbnailUrl`: `originalImageUrl` 우선, 없으면 `thumbnailUrl`
- `tags`: 수상 등급과 키워드 일부
- `cat`: `nature` (POC 화면 필터 호환용)
- `lat`, `lng`: `0` (정확한 좌표 매칭 전까지 사용하지 않음)

이 어댑터는 화면 표시를 위한 것이며, 수상작 ID를 실제 `Place` 도메인 ID로 저장하거나 코스 추천 입력으로 확정하지 않는다.

## 6. 오류 및 캐시 정책

- KTO 응답이 실패하면 백엔드가 기존 장소 목록으로 조용히 대체하지 않는다. POC에서 사진 출처를 섞으면 품질 비교가 어려워지므로 오류를 그대로 표시한다.
- 프론트 React Query 캐시 시간은 5분으로 둔다.
- 백엔드 DB 저장은 하지 않는다.
- 이미지 URL이 모두 비어 있는 레코드는 응답에서 제외한다.
- 이미지 URL 자체가 깨진 경우 프론트 `ImageSlot` placeholder를 사용한다.

## 7. 테스트

- KTO 공모전 응답 JSON 파서가 단일/배열 item을 올바르게 읽는지 검증한다.
- `showflag=1`, `lDongRegnCd=51`, 페이지 변환 파라미터가 요청에 포함되는지 검증한다.
- `orgImage` 우선, `thumbImage` 보조 매핑을 검증한다.
- 빈 이미지 레코드가 제외되는지 검증한다.
- 프론트 어댑터가 수상작 응답을 기존 카드 모델로 변환하는지 검증한다.
- 외부 KTO 호출에 의존하지 않는 단위 테스트를 기본으로 한다.

## 8. 후속 전환 조건

POC에서 이미지 품질을 확인한 뒤 다음을 별도 결정한다.

1. 수상작을 실제 장소 대표 이미지로 사용할지
2. 관광사진갤러리(`PhotoGalleryService1`)를 추가 fallback으로 사용할지
3. 원본·썸네일·촬영자·저작권 메타데이터를 DB에 저장할지
4. AI 합성에 수상작을 배경 후보와 구도 참조로 어떻게 전달할지

이 결정 전까지 `KorService2`와 수상작 데이터는 서로 대체 가능한 하나의 원본으로 취급하지 않는다.
