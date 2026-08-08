# 07 Data Model

## 설계 원칙

회원이 없으므로 `User` 테이블은 만들지 않는다.

이미지 binary는 MySQL에 저장하지 않는다.

외부 API raw JSON 전체를 핵심 도메인 테이블에 넣지 않는다.

## 주요 Entity

### Place

```text
id
tourContentId
name
region
category
description
latitude
longitude
thumbnailUrl
source
sourceUpdatedAt
createdAt
updatedAt
```

`tourContentId`는 한국관광공사 contentId 계열 식별자를 내부 id와 분리한다.

### PlaceImage

```text
id
placeId
imageUrl
title
source
sortOrder
createdAt
```

### CompositionJob

```text
id
onePickPlaceId
status
stage
progress
inputStorageKey
resultStorageKey
provider
modelVersion
promptVersion
safetyStatus
errorCode
retryCount
createdAt
startedAt
completedAt
expiresAt
```

원본 사용자 사진과 결과 파일은 임시 저장소에 있고, DB에는 storage key/metadata만 저장한다.

### Course

```text
id
title
durationType
startDate nullable
endDate nullable
shareTokenHash nullable
shareExpiresAt nullable
createdAt
expiresAt nullable
```

### CourseStop

```text
id
courseId
sequence
placeId
arrivalTime
stayMinutes
crowdLevel
note
isOnePick
latitudeSnapshot
longitudeSnapshot
```

좌표 snapshot은 장소 원본 데이터가 바뀌어도 저장된 코스가 깨지지 않도록 한다.

### CourseRecommendationScore

MVP에서 필요할 경우:

```text
courseStopId
preferenceScore
distanceScore
popularityScore
crowdScore
diversityScore
totalScore
```

디버깅/설명 가능성을 위해 저장할 수 있다.

## Redis Key

예:

```text
tour:place:{contentId}
tour:places:gangneung:{queryHash}
composition:job:{jobId}
course:share:{tokenHash}
```

모든 임시 key에는 TTL을 설정한다.

## 관계

```text
Place 1 ─ N PlaceImage

Place 1 ─ N CompositionJob (onePick)

Course 1 ─ N CourseStop
CourseStop N ─ 1 Place

Course 1 ─ 0..1 ShareToken
```

ShareToken은 별도 entity로 분리해도 된다.

## 삭제

- expired CompositionJob → 파일 삭제 + metadata cleanup 정책
- expired Course → 익명 데이터 cleanup
- share revoke → token 무효화
