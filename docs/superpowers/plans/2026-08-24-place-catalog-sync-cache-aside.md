# Place Catalog Sync and Cache-Aside Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 강릉 관광지 전체를 별도 동기화로 DB에 저장하고, 화면의 장소 조회는 Redis와 DB만 사용하도록 분리한다.

**Architecture:** `PlaceCatalogSyncService`가 KorService2 요약 목록 전체와 관광사진 카탈로그를 명시적인 시작 옵션에서만 가져와 `places`와 `place_images`에 저장한다. 일반 `/api/v1/places` 요청은 Redis cache-aside 방식으로 동작하며 캐시 미스에도 외부 API를 호출하지 않는다.

**Tech Stack:** Java 17, Spring Boot 4, Spring Data JPA, Redis, JUnit 5, Mockito

**Spec:** 사용자 승인 대화 — 전체 KorService2 장소 동기화, 장소당 최대 5장, Type1만 사용, 화면 요청과 관광공사 갱신 분리, 두 개의 로컬 커밋

## Global Constraints

- 프론트엔드와 Agent 저장소는 수정하지 않는다.
- 외부 API 키를 코드·문서·커밋에 기록하지 않는다.
- 관광공사 이미지 중 Type1만 저장·노출한다.
- 동기화는 `TOUR_API_SYNC_ON_STARTUP=true`일 때만 실행한다.
- 일반 장소 목록·상세 요청은 관광공사 API를 호출하지 않는다.
- 카드 확장과 cache-aside 변경은 각각 독립 커밋으로 남긴다.

---

### Task 1: 전체 장소 카탈로그 동기화

**Files:**
- Modify: `src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiClient.java`
- Modify: `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClient.java`
- Create: `src/main/java/com/mirigangneung/place/service/PlaceCatalogSyncService.java`
- Create: `src/main/java/com/mirigangneung/place/service/PlaceCatalogSyncRunner.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/mirigangneung/place/service/PlaceCatalogSyncServiceTest.java`
- Test: `src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourApiClientTest.java`

**Interfaces:**
- Consumes: `TourApiClient.TourPlace`, `TourismPhotoMatcher.findImageUrls(List<Place>)`, `PlaceRepository`, `PlaceImageRepository`
- Produces: `TourApiClient.searchSummaries(String, String, int, int)`, `PlaceCatalogSyncService.syncAll()`, startup flag `tour.api.sync-on-startup`

- [ ] **Step 1: Write failing summary-fetch and catalog-sync tests**

```java
assertThat(client.searchSummaries(null, null, 0, 100)).hasSize(2);
assertThat(service.syncAll()).satisfies(result -> {
    assertThat(result.fetchedPlaces()).isEqualTo(3);
    assertThat(result.savedPlaces()).isEqualTo(3);
});
verify(tour).searchSummaries(null, null, 0, 1_000);
verify(tour).searchSummaries(null, null, 1, 1_000);
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `bash ./gradlew test --tests '*KoreanTourApiClientTest' --tests '*PlaceCatalogSyncServiceTest'`

Expected: compilation or assertion failure because summary fetching and synchronization do not exist.

- [ ] **Step 3: Implement summary-only pagination and persistence**

```java
default List<TourPlace> searchSummaries(String keyword, String category, int page, int size) {
    return search(keyword, category, page, size);
}
```

`KoreanTourApiClient` overrides this method without calling `detailImage2`. `PlaceCatalogSyncService` requests pages of 1,000 until a short page is returned, upserts by `tourContentId`, preserves existing allowed images, adds matched 관광사진 URLs, deduplicates URLs, and stores at most five Type1 images per place.

- [ ] **Step 4: Add the opt-in startup runner**

```yaml
tour:
  api:
    sync-on-startup: ${TOUR_API_SYNC_ON_STARTUP:false}
```

`PlaceCatalogSyncRunner` uses `@ConditionalOnProperty(prefix = "tour.api", name = "sync-on-startup", havingValue = "true")` and invokes `syncAll()` once after startup.

- [ ] **Step 5: Run focused and full tests**

Run: `bash ./gradlew test --tests '*KoreanTourApiClientTest' --tests '*PlaceCatalogSyncServiceTest'`

Run: `bash ./gradlew test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit card expansion**

Commit subject: `feat: synchronize the full Gangneung place catalog`

### Task 2: 장소 조회를 Redis/DB cache-aside로 전환

**Files:**
- Modify: `src/main/java/com/mirigangneung/place/service/PlaceService.java`
- Modify: `src/test/java/com/mirigangneung/place/service/PlaceServiceTest.java`
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/WORK_LOG.md`

**Interfaces:**
- Consumes: `RedisCache.get`, `RedisCache.put`, `PlaceRepository`, `PlaceImageRepository`
- Produces: 외부 호출 없는 `PlaceService.search`와 `PlaceService.detail`

- [ ] **Step 1: Strengthen cache-miss tests before production changes**

```java
service.search(null, null, 0, 20);
verifyNoInteractions(tour);

assertThat(service.detail(place.getId().toString()).name()).isEqualTo("경포대");
verifyNoInteractions(tour);
```

- [ ] **Step 2: Run tests and verify RED**

Run: `bash ./gradlew test --tests '*PlaceServiceTest'`

Expected: FAIL because a list cache miss currently invokes `tour.search`.

- [ ] **Step 3: Remove request-time synchronization**

`PlaceService.search` reads Redis, then DB, then writes Redis. `PlaceService.detail` reads Redis, then DB, then writes Redis. Remove request-time `tour.search`, `tour.find`, and `TourismPhotoMatcher` calls; keep nearby/related behavior unchanged.

- [ ] **Step 4: Bump the list/detail cache key versions and update docs**

Use `place:list:v5:` and `place:detail:v3:` so previously cached request-time responses cannot mask DB-only behavior.

- [ ] **Step 5: Run focused and full tests**

Run: `bash ./gradlew test --tests '*PlaceServiceTest'`

Run: `bash ./gradlew test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit cache-aside behavior**

Commit subject: `fix: serve place requests from cache and database`

- [ ] **Step 7: Verify the final commit boundary**

Run: `git log --oneline -2`

Expected: the catalog synchronization commit followed by the cache-aside commit.
