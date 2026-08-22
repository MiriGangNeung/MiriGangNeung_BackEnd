# 관광공사 핵심 장소 API P0 구현 계획

> For agentic workers: follow this plan task-by-task and keep the red-green-refactor loop for every behavior change.

**Goal:** `KorService2`의 장소 목록·검색·상세·이미지 응답을 내부 `Place` 모델과 프론트엔드용 API에 연결하고, 외부 오류·원천 갱신 시각·캐시를 안정적으로 처리한다.

**Architecture:** `TourApiClient` 포트는 유지한다. `KoreanTourApiClient`는 요청만 조립하고 `TourApiResponseParser`와 `TourCategoryMapper`가 응답 envelope·카테고리·이미지 데이터를 정규화한다. `PlaceService`는 정규화 결과를 upsert하고 Redis를 선택적으로 사용한다.

**Tech Stack:** Java 17, Spring Boot 4, `RestClient`, Jackson JSON/XML, Spring Data JPA, Redis, JUnit 5, AssertJ, Mockito, `MockRestServiceServer`.

## Global Constraints

- `develop` 브랜치에서만 작업한다.
- 기존의 `.DS_Store`와 `docs/PROJECT_STRUCTURE_AND_COMMUNICATION.md`는 스테이징하지 않는다.
- 외부 관광공사 서버에 의존하는 테스트는 작성하지 않는다.
- API 키를 로그·캐시 키·예외 메시지에 포함하지 않는다.
- 기존 `TourApiClient` 구현체와 테스트의 9개 인자 `TourPlace` 생성 호환성을 보존한다.
- `related()`는 P1의 `TarRlteTarService1` 연동 전까지 `detailInfo2`를 관련 관광지 데이터로 사용하지 않는다.
- 각 작업은 실패 테스트 → 최소 구현 → 전체 관련 테스트 → 작은 커밋 순서로 수행한다.

---

## Task 1: 응답 envelope 파서와 카테고리 매퍼 추가

**Files:**

- Create `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiResponseParser.java`
- Create `src/main/java/com/mirigangneung/infrastructure/tourapi/TourCategoryMapper.java`
- Create `src/test/java/com/mirigangneung/infrastructure/tourapi/TourApiResponseParserTest.java`
- Create `src/test/java/com/mirigangneung/infrastructure/tourapi/TourCategoryMapperTest.java`
- Modify `src/main/java/com/mirigangneung/common/error/ApiException.java` only if parser needs an existing accessor; do not change its public error contract otherwise.

**Step 1 — Write the failing tests:**

- Parse a successful JSON response whose `items.item` is an array.
- Parse a successful JSON response whose `items.item` is one object.
- Parse the equivalent XML response.
- Return an empty list for a successful response with no items.
- Throw `TOUR_API_ERROR` for a non-success `resultCode`, missing response envelope, and malformed body.
- Map `nature`, `beach`, `culture`, `food`, `active`, `shopping`, `lodging`, `course`, and event aliases to the documented `contentTypeId` values.
- Map `contentTypeId` values `12`, `14`, `15`, `25`, `28`, `32`, `38`, and `39` back to stable internal category names.

**Step 2 — Run tests to confirm red:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*TourApiResponseParserTest' --tests '*TourCategoryMapperTest'
```

Expected result: compilation or test failure because the parser and mapper do not exist yet.

**Step 3 — Implement the minimum:**

- Parse JSON and XML with one reusable Jackson mapper per format.
- Validate `response.header.resultCode` before reading `body.items`.
- Treat only a successful response with absent/blank items as an empty result.
- Keep upstream error details internal and expose only the existing `TOUR_API_ERROR`/`502` contract.
- Make category mapping explicit, with tourist/nature aliases defaulting to `12` and unknown values falling back to the existing tourist-place behavior.

**Step 4 — Run the focused tests to confirm green:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*TourApiResponseParserTest' --tests '*TourCategoryMapperTest'
```

**Step 5 — Commit:**

```bash
git add src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiResponseParser.java src/main/java/com/mirigangneung/infrastructure/tourapi/TourCategoryMapper.java src/test/java/com/mirigangneung/infrastructure/tourapi/TourApiResponseParserTest.java src/test/java/com/mirigangneung/infrastructure/tourapi/TourCategoryMapperTest.java
git commit -m "feat: add tourism response normalization"
```

---

## Task 2: `KorService2` 요청과 장소·이미지 정규화 연결

**Files:**

- Modify `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiClient.java`
- Modify `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- Modify `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiProperties.java`
- Create `src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClientTest.java`

**Step 1 — Write the failing tests:**

- Assert an empty key does not make an HTTP request.
- Assert a blank keyword calls `areaBasedList2` with `contentTypeId`, `lDongRegnCd=51`, `lDongSignguCd=150`, zero-based page conversion, size, and `arrange=A`.
- Assert a keyword calls `searchKeyword2` and includes the keyword and mapped category type.
- Assert `find` calls `detailCommon2`, then `detailImage2` with `imageYN=Y`, and merges the representative image with additional images without duplicate URLs.
- Assert `modifiedtime` is converted to the source `OffsetDateTime` and image `cpyrhtDivCd` is retained.
- Assert `detailIntro2` has a dedicated extension result and does not leak raw response data into the existing place fields.
- Assert a non-success response becomes `ApiException` rather than an empty result.
- Assert `related` does not call `detailInfo2` while the P1 related adapter is absent.

Use `MockRestServiceServer` bound to a `RestClient.Builder` or a package-private client constructor so the tests verify request paths and query parameters without a network call.

**Step 2 — Run tests to confirm red:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*KoreanTourApiClientTest'
```

**Step 3 — Implement the minimum:**

- Extend `TourPlace` with source modification time and typed `TourImage` metadata while keeping a backward-compatible constructor and `imageUrls()` accessor.
- Add a small `TourPlaceIntro` extension result for `detailIntro2`.
- Build full request URIs from the configured base URL, decode the service key exactly once, apply the configured connect/read timeout, and use the shared parser.
- Map `contenttypeid`, `firstimage`/`firstimage2`, `mapx`/`mapy`, `modifiedtime`, `cpyrhtDivCd`, and detail-image fields.
- Keep `nearby` behavior intact except for the shared parser and error handling.
- Return no false “related” data until the documented related-tourism API is implemented in P1.

**Step 4 — Run focused and existing tests:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*KoreanTourApiClientTest' --tests '*RuleBasedCourseRecommendationEngineTest'
```

**Step 5 — Commit:**

```bash
git add src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiClient.java src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiProperties.java src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClientTest.java
git commit -m "feat: integrate Korean tourism core endpoints"
```

---

## Task 3: 장소 저장·이미지·원천 갱신 시각 연결

**Files:**

- Modify `src/main/java/com/mirigangneung/place/domain/Place.java`
- Modify `src/main/java/com/mirigangneung/place/domain/PlaceImage.java`
- Modify `src/main/java/com/mirigangneung/place/repository/PlaceImageRepository.java`
- Modify `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- Modify `src/main/java/com/mirigangneung/place/dto/PlaceDetailResponse.java`
- Create `src/main/java/com/mirigangneung/place/dto/PlaceImageResponse.java`
- Create `src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java`

**Step 1 — Write the failing tests:**

- Upserting the same `tourContentId` updates the existing row instead of creating a duplicate.
- Upserting a place stores the upstream modification time, not the local current time when an upstream time is present.
- Images are replaced deterministically by sort order and are not duplicated on a second upsert.
- Image URL, title, source, and copyright code are exposed in detail output while the legacy `imageUrls` list remains available.
- A place without images still returns successfully.
- Category filtering uses the same normalized internal category that the API mapper stores.
- When an external search fails, the existing local page is returned; detail still raises the existing not-found/API error when no local fallback exists.

**Step 2 — Run tests to confirm red:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*PlaceServiceTest'
```

**Step 3 — Implement the minimum:**

- Add source-time access/update methods to `Place` without changing the existing constructor call sites.
- Add `copyrightCode` to `PlaceImage` and a repository delete-by-place operation.
- Normalize and save image metadata once per upsert; preserve existing images when the upstream response did not provide image data.
- Use `TourCategoryMapper` for both persistence and repository filtering.
- Keep the existing REST response fields and add structured image metadata compatibly.

**Step 4 — Run focused tests:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*PlaceServiceTest' --tests '*KoreanTourApiClientTest'
```

**Step 5 — Commit:**

```bash
git add src/main/java/com/mirigangneung/place/domain/Place.java src/main/java/com/mirigangneung/place/domain/PlaceImage.java src/main/java/com/mirigangneung/place/repository/PlaceImageRepository.java src/main/java/com/mirigangneung/place/service/PlaceService.java src/main/java/com/mirigangneung/place/dto/PlaceDetailResponse.java src/main/java/com/mirigangneung/place/dto/PlaceImageResponse.java src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java
git commit -m "feat: persist tourism place images and source dates"
```

---

## Task 4: Redis 캐시와 장애 우회

**Files:**

- Create `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiCacheProperties.java`
- Modify `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiConfig.java`
- Modify `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- Modify `src/main/resources/application.yml`
- Extend `src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java`

**Step 1 — Write the failing tests:**

- A repeated list request with the same category, keyword, page, and size returns the cached response without calling the remote client.
- Detail cache keys use the internal place identifier and do not contain the API key.
- A cache read/write exception or malformed cached JSON is treated as a cache miss.
- List and detail cache TTLs are distinct and configurable.

**Step 2 — Run tests to confirm red:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*PlaceServiceTest'
```

**Step 3 — Implement the minimum:**

- Add `tour.api.cache.list-ttl` and `tour.api.cache.detail-ttl` with safe defaults of 5 minutes and 1 hour.
- Serialize only normalized response DTOs with Jackson.
- Version cache keys and encode user-supplied values; never include `TOUR_API_KEY`.
- Keep `RedisCache` fail-open behavior so Redis unavailability falls back to the database/API flow.
- Catch only the known tourism upstream error for search fallback; do not hide programming or validation errors.

**Step 4 — Run focused tests:**

```bash
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test --tests '*PlaceServiceTest'
```

**Step 5 — Commit:**

```bash
git add src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiCacheProperties.java src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiConfig.java src/main/java/com/mirigangneung/place/service/PlaceService.java src/main/resources/application.yml src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java
git commit -m "feat: cache tourism place responses"
```

---

## Task 5: 전체 검증과 P0 인수 기준 확인

**Files:**

- Modify only files required by failing verification; do not broaden into P1 API work.

**Step 1 — Run formatting and full tests:**

```bash
git diff --check
GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew test
```

**Step 2 — Review the final diff:**

```bash
git diff main...HEAD --stat
git diff main...HEAD -- src/main/java/com/mirigangneung/infrastructure/tourapi src/main/java/com/mirigangneung/place src/test/java/com/mirigangneung/infrastructure/tourapi src/test/java/com/mirigangneung/place
git status --short --branch
```

**Step 3 — Confirm:**

- `areaBasedList2`, `searchKeyword2`, `detailCommon2`, `detailImage2`, and the `detailIntro2` extension boundary use the manual-defined contract.
- No code path treats `detailInfo2` as related tourism.
- All existing tests and new P0 tests pass.
- Existing untracked user files remain untouched.
- The final report includes the exact branch, commits, test command/result, and known P1 boundary.
