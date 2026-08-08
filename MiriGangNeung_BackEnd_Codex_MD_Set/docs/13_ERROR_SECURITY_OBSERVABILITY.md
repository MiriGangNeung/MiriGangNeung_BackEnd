# 13 Error / Security / Observability

## Error response

권장 형식:

```json
{
  "timestamp": "2026-08-08T12:00:00+09:00",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/courses"
}
```

외부 provider의 내부 오류 메시지/stack trace를 사용자에게 그대로 노출하지 않는다.

## Error code 예시

```text
INVALID_REQUEST
PLACE_NOT_FOUND
COURSE_NOT_FOUND
COMPOSITION_NOT_FOUND
COMPOSITION_EXPIRED
INVALID_COMPOSITION_STATE
AI_PROVIDER_ERROR
AI_TIMEOUT
AI_SAFETY_REJECTED
TOUR_API_ERROR
KAKAO_API_ERROR
ROUTE_UNAVAILABLE
IMAGE_TOO_LARGE
UNSUPPORTED_IMAGE
RATE_LIMITED
INTERNAL_ERROR
```

## Validation

- file size
- MIME type
- image dimensions
- placeId 존재
- onePickId가 placeIds에 포함되는지
- types 개수 1~2
- duration enum
- custom 날짜 순서
- share token format

## 파일 보안

- 원본 파일명을 그대로 filesystem path로 사용하지 않는다.
- UUID/ULID 기반 storage key
- path traversal 방지
- 허용 MIME whitelist
- 업로드 size limit
- 이미지 확장자만 믿지 말고 실제 content type 검증

## 개인정보

- 원본 이미지 URL을 로그에 남기지 않는다.
- 사용자 사진을 장기 보관하지 않는다.
- 필요 없는 EXIF/metadata를 보관하지 않는다.
- 에러 로그에 request body 전체를 남기지 않는다.

## CORS

개발:

```text
localhost frontend → localhost backend
```

배포:

허용된 frontend origin만 whitelist.

`*`를 운영환경에 사용하지 않는다.

## Rate limit

회원이 없으므로 악성 반복 요청 방어가 중요하다.

우선:

- composition 생성
- retry
- course 생성

에 제한을 고려한다.

Redis를 사용한다.

## Observability

최소:

- request latency
- HTTP status
- external API latency
- AI generation duration
- AI failure count
- course generation duration
- Redis hit/miss
- cleanup count

request ID/correlation ID를 사용한다.
