# ADR: Backend-Agent 이미지 합성 Job 연동

- 날짜: 2026-09-08
- 상태: Accepted

## 배경

백엔드에는 `AiGenerationClient` 인터페이스와 Composition Job API가 있었지만 실제 Agent 생성,
provider Job 식별자 저장, polling, 결과 다운로드가 연결되지 않았다. 사용자 사진은
`TemporaryImageStorage`에 저장되고 관광지 원본은 별도의 `PlaceImageStorage`에 저장되어 있어 두
저장소를 혼동하면 안 된다. 또한 AI 합성은 변경이 허용되는 `copyrightCode=Type1` 관광지 이미지만
사용해야 한다.

## 결정

- 백엔드는 `HttpAiGenerationClient`로 FastAPI Agent의 `/v1/generations` 계약을 호출한다.
- 실제 AI Provider와 모델은 Agent의 설정이 선택하며 백엔드는 선택하지 않는다.
- `onePickId`는 현재 장소 선택 흐름에서 사용하는 `Place.id` UUID만 지원한다.
- `kto-award:*`, `kto-gallery:*` 표시용 ID를 UUID로 변환하지 않고 `INVALID_ONE_PICK_ID`로 거부한다.
- 배경은 해당 Place의 Type1 `PlaceImage`만 사용하며 `PlaceImageStorage.open(originalStorageKey)`로
  읽는다. 파일이 없을 때만 기존 `ImageAssetCacheService`를 사용해 같은 원본 URL을 복구한다.
- 선택 이미지 전달을 위해 기존 API에 선택적인 `backgroundImageUrl` multipart 필드를 추가한다.
  생략하면 첫 Type1 이미지를 사용하므로 기존 호출과 호환된다.
- Agent `providerJobId`와 진행 상태를 MySQL `CompositionJob`에 저장하고 기본 2초 polling한다.
- DONE 결과는 `TemporaryImageStorage`에 저장하고 백엔드 download API로 제공한다.
- Agent의 `error.retryable`을 보존하며 retry는 동일 백엔드 Job에서 새 Agent generation을 만든다.

## 결과

프론트는 백엔드 API만 polling하면 되고 Agent 주소나 인증키를 알 필요가 없다. 관광지 배경과 사용자
업로드의 저장 경계, Type1 저작권 제한이 유지된다. 현재 저장소에 migration 도구가 없으므로 기존
정책인 Hibernate `ddl-auto=update`로 CompositionJob 컬럼이 추가된다. 운영에서 별도 schema migration
도구를 채택하면 이 변경도 명시적 migration으로 옮겨야 한다.

별도 사진 소스의 표시용 ID와 저작권 정보가 `Place.id`/Type1 계약으로 정규화되기 전에는 해당 소스를
Composition API에서 지원하지 않는다.
