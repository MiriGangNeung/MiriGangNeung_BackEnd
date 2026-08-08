# 10 Image Storage

## 현재 결정

AWS S3를 MVP 필수로 사용하지 않는다.

사용자 요구사항은 생성 이미지를 장기 보관하지 않고 다운로드 후 폐기하는 것이다.

## MVP

단일 서버/단일 인스턴스 환경에서는 로컬 임시 디렉터리를 사용할 수 있다.

```text
/tmp/mirigangneung/
  input/
  result/
```

DB에는 파일 binary가 아니라 key/path와 metadata만 저장한다.

## TTL

파일에는 `expiresAt`을 둔다.

정리 작업:

```text
scheduled cleanup
  ↓
expired input/result 검색
  ↓
파일 삭제
  ↓
DB metadata cleanup
```

다운로드했다고 즉시 삭제하지 않아도 된다. 다운로드 중 삭제 race를 피하기 위해 TTL 기반 삭제가 기본이다.

## 확장

서버가 다중 인스턴스로 늘어나면 local filesystem을 사용하면 안 된다.

이때:

```java
interface TemporaryImageStorage
```

구현체를:

```text
LocalTemporaryImageStorage
S3CompatibleTemporaryImageStorage
```

로 교체한다.

S3가 필요해지는 시점은 다중 서버, AI worker 분리, CDN, 대용량 처리 등이 발생했을 때다.

## 다운로드

권장:

```http
GET /api/v1/compositions/{jobId}/download
```

백엔드가 파일을 스트리밍한다.

향후 object storage를 쓰면 short-lived signed URL로 교체할 수 있다.

## 개인정보

원본 사용자 사진은 서비스 목적에 필요한 최소 기간만 유지한다.

로그에 이미지 자체/개인 사진 URL을 남기지 않는다.
