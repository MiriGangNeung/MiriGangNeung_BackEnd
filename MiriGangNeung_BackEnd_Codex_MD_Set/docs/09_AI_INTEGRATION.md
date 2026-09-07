# 09 AI Integration

## 현재 결정

백엔드는 AI 담당 팀원의 FastAPI Agent와 HTTP Job 계약으로 연결한다. 실제 AI Provider/모델은
Agent의 `AI_PROVIDER` 설정이 선택하며 백엔드는 특정 Provider를 임의 선택하지 않는다.

## Adapter

```java
public interface AiGenerationClient {
    AiGenerationResponse create(AiGenerationRequest request);
    AiGenerationResponse getStatus(String providerJobId);
    DownloadedImage downloadResult(String providerJobId);
    void cancel(String providerJobId);
}
```

`HttpAiGenerationClient`는 `AI_BASE_URL`의 Agent에 multipart 생성 요청을 보내고 상태 및 결과를
가져온다. 모든 요청은 `AI_API_KEY`가 설정된 경우 `X-API-Key` 헤더를 사용한다.

```text
POST /v1/generations
GET  /v1/generations/{providerJobId}
GET  /v1/generations/{providerJobId}/result
POST /v1/generations/{providerJobId}/cancel
```

사용자 원본은 `TemporaryImageStorage`, 관광지 원본은 `PlaceImageStorage`에서 각각 열어 multipart의
`photo`, `background`로 보낸다. 배경은 `copyrightCode=Type1`만 허용한다.

## Spring 내부 흐름

```text
CompositionController
  ↓
CompositionService
  ↓
AiGenerationClient
  ↓
AI Provider
```

Agent가 `QUEUED`를 반환하면 `providerJobId`를 MySQL의 CompositionJob에 저장한다. 백엔드의
`CompositionPollingJob`이 기본 2초 간격으로 활성 Job을 조회하고, `DONE`이면 결과 바이트를
`TemporaryImageStorage`에 TTL과 함께 저장한다.

## Job 상태

```text
QUEUED
ANALYZING
COMPOSITING
QUALITY_CHECK
DONE
FAILED
```

## AI 결과 계약

AI 담당자와 반드시 합의해야 하는 최소 계약:

```json
{
  "providerJobId": "string",
  "status": "RUNNING|DONE|FAILED",
  "stage": "string",
  "progress": 0,
  "result": { "imageReference": "string" },
  "safety": {
    "status": "PASSED|REJECTED|UNKNOWN",
    "reasonCode": null,
    "warnings": []
  },
  "error": { "code": "string", "message": "string", "retryable": true }
}
```

## 안전성

공모전 요구사항에는 생성 전후 이미지 유해성/인물 왜곡/신체 오류 검사가 포함되어 있다.

백엔드는 AI 서버가 safety result를 반환할 수 있도록 필드를 유지한다.

현재 백엔드가 자체 모델/검사기를 임의 구현하지 않는다.

## Prompt

프롬프트 자체는 AI 담당자가 관리한다.

백엔드는 `promptVersion` metadata를 기록할 수 있어야 한다.

## 비용

백엔드에서 LLM을 코스 생성에 호출하지 않는다.

이미지 생성 비용은 AI Provider 선택 후 별도로 관리한다.

## Retry

- timeout/provider temporary failure: 제한된 retry
- safety rejected: 자동 무한 retry 금지
- invalid user input: retry 금지
- provider job unknown: 상태 재조회 후 판단

백엔드 retry API는 기존 로컬 Job ID와 입력 파일을 유지하면서 Agent generation을 새로 만든다.
Agent가 `retryable=false`로 반환한 오류는 retry API가 거부한다. polling 중 일시적인 네트워크 오류는
즉시 Job을 실패시키지 않고 다음 polling에서 다시 조회한다.

## 설정

```text
AI_BASE_URL=http://localhost:8100
AI_API_KEY=백엔드와 Agent가 공유하는 선택적 인증값
AI_CONNECT_TIMEOUT=5s
AI_READ_TIMEOUT=30s
AI_POLL_DELAY=2s
```

로컬 통합 검증은 Agent를 `AI_PROVIDER=mock`으로 실행해 실제 모델 비용 없이 수행한다.
