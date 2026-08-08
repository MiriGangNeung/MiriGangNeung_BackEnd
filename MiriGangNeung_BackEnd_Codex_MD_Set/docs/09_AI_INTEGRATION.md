# 09 AI Integration

## 현재 결정

AI 담당 팀원이 별도로 있으며 AI Provider/모델은 아직 미정이다.

따라서 백엔드가 특정 Provider를 임의 선택하지 않는다.

## Adapter

```java
public interface AiGenerationClient {
    AiGenerationResponse create(AiGenerationRequest request);
    AiGenerationStatus getStatus(String providerJobId);
    void cancel(String providerJobId);
}
```

실제 provider가 HTTP API를 제공하면 `HttpAiGenerationClient` 등의 구현체를 둔다.

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
  "result": {
    "imageReference": "string"
  },
  "safety": {
    "status": "PASSED|REJECTED|UNKNOWN",
    "reasonCode": null
  },
  "error": null
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
