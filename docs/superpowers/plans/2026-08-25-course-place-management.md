# Course Place Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the real course API and let users search, add, delete, and reorder nearby Kakao restaurants/cafes around all selected Gangneung tourist places while persisting external-place snapshots and recalculating walking-route metrics.

**Architecture:** Keep Kakao REST credentials and external normalization in backend adapters. Model Kakao results as course-owned snapshots linked to `CourseStop`, so one course response can contain tourism and external stops. The frontend becomes a thin API client with optimistic course-result interactions and uses the server response as the source of truth.

**Tech Stack:** Java 17, Spring Boot 4, Spring Data JPA, Gradle, Jackson, Kakao Local REST API, Kakao walking-route REST API, React 18, TypeScript, TanStack Query, Zustand, Vitest, Tailwind CSS.

**Spec:** `docs/superpowers/specs/2026-08-25-course-place-management-design.md`

## Global Constraints

- Kakao REST keys stay backend-only; the frontend may keep only the Kakao Maps JavaScript key.
- Use Kakao Local category codes `FD6` for restaurants and `CE7` for cafes.
- Query every tourism stop in the course within `2_000` meters, merge by Kakao external place ID, and sort by minimum distance.
- Persist external place snapshots when they are added to a course; do not add Kakao places to the global KTO `Place` catalog.
- Do not allow deletion of the course one-pick stop.
- Validate complete, unique stop-order arrays in one transaction.
- Distinguish empty Kakao results from missing-key, authorization, quota, timeout, and upstream errors.
- Preserve existing API response fields and existing frontend routes while adding fields needed for external stops and route summaries.
- Write tests first for every production behavior change and run the repository's existing test suites before completion.

---

### Task 1: Record the API/domain contract and create failing backend adapter tests

**Files:**

- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Create: `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoLocalProperties.java`
- Create: `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoLocalClient.java`
- Create: `src/main/java/com/mirigangneung/infrastructure/kakao/HttpKakaoLocalClient.java`
- Modify: `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoConfig.java`
- Create: `src/test/java/com/mirigangneung/infrastructure/kakao/HttpKakaoLocalClientTest.java`

**Interfaces:**

- Produces `KakaoLocalClient.searchByCategory(double longitude, double latitude, String categoryCode, int radiusMeters, int page, int size)` returning `List<KakaoLocalClient.NearbyPlace>`.
- `NearbyPlace` contains `externalPlaceId`, `name`, `categoryName`, `categoryCode`, `address`, `roadAddress`, `phone`, `placeUrl`, `latitude`, `longitude`, and `distanceMeters`.
- Uses `KakaoLocalProperties(baseUrl, key, timeout, radiusMeters, pageSize)` with `kakao.local.*` configuration.

- [ ] **Step 1: Write failing adapter tests**

Test that a fake HTTP response produces the normalized fields and that the request uses `/v2/local/search/category.json`, `category_group_code`, `x`, `y`, `radius=2000`, `sort=distance`, and a 1-based Kakao page. Add a test that a non-2xx response raises `ApiException` with `KAKAO_API_ERROR`, and a blank key raises `KAKAO_API_NOT_CONFIGURED`.

- [ ] **Step 2: Run the focused tests and confirm the expected missing-client failure**

Run:

```bash
bash ./gradlew test --tests com.mirigangneung.infrastructure.kakao.HttpKakaoLocalClientTest
```

Expected: compilation/test failure because the client and properties do not exist yet.

- [ ] **Step 3: Add configuration and the minimal client implementation**

Use Spring `RestClient` with the configured base URL and `Authorization: KakaoAK {key}`. Parse `meta.documents` with the injected `ObjectMapper`; convert Kakao `x` to longitude, `y` to latitude, and an empty distance to `null`. Map `restaurant` to `FD6` and `cafe` to `CE7` only at the service boundary, not in the raw client.

- [ ] **Step 4: Run the focused adapter tests and refactor only after green**

Run the command from Step 2. Expected: all focused tests pass.

- [ ] **Step 5: Commit the adapter boundary**

```bash
git add src/main/resources/application.yml .env.example src/main/java/com/mirigangneung/infrastructure/kakao src/test/java/com/mirigangneung/infrastructure/kakao/HttpKakaoLocalClientTest.java
git commit -m "feat: add Kakao local category client"
```

### Task 2: Support tourism and external snapshots in course stops

**Files:**

- Create: `src/main/java/com/mirigangneung/course/domain/CourseExternalPlace.java`
- Modify: `src/main/java/com/mirigangneung/course/domain/CourseStop.java`
- Modify: `src/main/java/com/mirigangneung/course/repository/CourseStopRepository.java`
- Create: `src/main/java/com/mirigangneung/course/repository/CourseExternalPlaceRepository.java`
- Create: `src/test/java/com/mirigangneung/course/domain/CourseStopTest.java`
- Create: `src/test/java/com/mirigangneung/course/domain/CourseExternalPlaceTest.java`

**Interfaces:**

- `CourseExternalPlace` stores `source`, `externalPlaceId`, name/category/address/roadAddress/phone/placeUrl, latitude/longitude, and created timestamp.
- `CourseStop` has exactly one of `Place place` or `CourseExternalPlace externalPlace`, plus `sequence`; expose `getStopId()`, `getPlaceId()`, `getExternalPlaceId()`, `getDisplayName()`, `getLatitude()`, `getLongitude()`, `getKind()`, and `isOnePick()` for DTO mapping.
- `CourseStopRepository` adds `findByIdAndCourse(UUID stopId, Course course)` and sequence-ordered retrieval already used by the service.

- [ ] **Step 1: Write failing domain tests**

Cover tourism-stop mapping, external-stop mapping, mutual exclusivity, and display coordinate/name accessors. Assert that an external stop reports `RESTAURANT` or `CAFE` and a tourism stop reports `TOURISM`.

- [ ] **Step 2: Run the domain tests and verify they fail for missing accessors/entities**

```bash
bash ./gradlew test --tests com.mirigangneung.course.domain.CourseStopTest --tests com.mirigangneung.course.domain.CourseExternalPlaceTest
```

- [ ] **Step 3: Implement the snapshot entity and CourseStop relationship**

Use nullable `@ManyToOne` fields with a domain constructor guard so exactly one source is present. Keep the existing tourism constructor and add an external constructor. Add database indexes/unique constraint for `(source, external_place_id)` on snapshots and use UUID stop IDs for reorder/delete APIs.

- [ ] **Step 4: Run all course domain tests**

Run the command from Step 2 and then `bash ./gradlew test --tests 'com.mirigangneung.course.*'`. Expected: all course tests pass.

- [ ] **Step 5: Commit the snapshot model**

```bash
git add src/main/java/com/mirigangneung/course/domain src/main/java/com/mirigangneung/course/repository src/test/java/com/mirigangneung/course/domain
git commit -m "feat: persist external course place snapshots"
```

### Task 3: Implement nearby-place aggregation and course mutation APIs

**Files:**

- Create: `src/main/java/com/mirigangneung/course/dto/NearbyPlaceResponse.java`
- Create: `src/main/java/com/mirigangneung/course/dto/NearbyPlacesResponse.java`
- Create: `src/main/java/com/mirigangneung/course/dto/AddExternalStopRequest.java`
- Create: `src/main/java/com/mirigangneung/course/dto/StopOrderRequest.java`
- Modify: `src/main/java/com/mirigangneung/course/dto/CourseResponse.java`
- Modify: `src/main/java/com/mirigangneung/course/service/CourseService.java`
- Modify: `src/main/java/com/mirigangneung/course/controller/CourseController.java`
- Create: `src/test/java/com/mirigangneung/course/service/CoursePlaceManagementTest.java`

**Interfaces:**

- `CourseService.nearby(String courseId, String category)` returns normalized, merged `NearbyPlacesResponse`.
- `CourseService.addExternalStop(String courseId, AddExternalStopRequest request)` returns the updated `CourseResponse`.
- `CourseService.deleteStop(String courseId, String stopId)` returns the updated `CourseResponse`.
- `CourseService.reorderStops(String courseId, StopOrderRequest request)` returns the updated `CourseResponse`.
- Controller routes:

```text
GET   /api/v1/courses/{courseId}/nearby-places?category=restaurant|cafe
POST  /api/v1/courses/{courseId}/external-stops
DELETE /api/v1/courses/{courseId}/stops/{stopId}
PATCH /api/v1/courses/{courseId}/stops/order
```

- [ ] **Step 1: Write failing service tests**

Use a fake `KakaoLocalClient` and repositories to prove three tourism stops are queried, duplicate Kakao IDs collapse to one result with the minimum distance/reference stop, invalid categories are rejected, external stops append with the next sequence, duplicate external IDs return `COURSE_STOP_ALREADY_EXISTS`, one-pick deletion returns `COURSE_ONE_PICK_REQUIRED`, and reorder rejects duplicates/missing IDs.

- [ ] **Step 2: Run focused tests and confirm missing methods fail**

```bash
bash ./gradlew test --tests com.mirigangneung.course.service.CoursePlaceManagementTest
```

- [ ] **Step 3: Implement category mapping and aggregation**

Load the course and its sequence-ordered stops, filter to tourism stops with coordinates, call the local client once per stop, merge by `externalPlaceId`, keep the candidate with the smallest distance, attach its nearest stop ID/name, and sort by distance then external ID for deterministic output. Return an empty list for no matches and propagate upstream errors as `ApiException`.

- [ ] **Step 4: Implement add/delete/reorder transactions**

Add validates allowed categories and coordinates, creates the snapshot and stop, and appends at the end. Delete resolves the stop by course, refuses `isOnePick`, deletes its snapshot if present, and compacts sequence values. Reorder compares the request set to the current course set before assigning sequence values. Call the route summary service after each mutation.

- [ ] **Step 5: Extend CourseResponse without removing legacy fields**

Add `stopId`, nullable `placeId`, nullable `externalPlaceId`, `kind`, `address`, `placeUrl`, `routeStatus`, `routeSegments`, and `routePoints`. Keep existing response names and make external thumbnails nullable.

- [ ] **Step 6: Run focused and full backend tests**

```bash
bash ./gradlew test --tests com.mirigangneung.course.service.CoursePlaceManagementTest
bash ./gradlew test
```

- [ ] **Step 7: Commit the course API**

```bash
git add src/main/java/com/mirigangneung/course src/test/java/com/mirigangneung/course
git commit -m "feat: add course place management APIs"
```

### Task 4: Replace route adapter with Kakao walking metrics

**Files:**

- Modify: `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoRouteProperties.java`
- Modify: `src/main/java/com/mirigangneung/infrastructure/kakao/HttpKakaoRouteClient.java`
- Create: `src/main/java/com/mirigangneung/course/service/CourseRouteCalculator.java`
- Modify: `src/main/java/com/mirigangneung/course/service/CourseService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `src/test/java/com/mirigangneung/infrastructure/kakao/HttpKakaoRouteClientTest.java` or create it when absent
- Create: `src/test/java/com/mirigangneung/course/service/CourseRouteCalculatorTest.java`

**Interfaces:**

- `KakaoRouteClient.walking` calls `GET https://dapi.kakao.com/v2/routing/walk` with WGS84 coordinates and returns `RouteResult(distanceMeters, durationSeconds, polyline)`.
- `CourseRouteCalculator.calculate(List<CourseStop> stops)` returns `RouteSummary(status, totalDistanceMeters, totalTravelMinutes, segments, routePoints)`.

- [ ] **Step 1: Write failing route tests**

Assert the HTTP client sends `start_x/start_y/end_x/end_y`, parses `route.properties.totalDistance/totalTime`, flattens `legs[].steps[].path.points` in order, and converts seconds to rounded-up minutes. Assert the calculator sums adjacent segments and returns `UNAVAILABLE` without a key or when an upstream route call fails.

- [ ] **Step 2: Run focused route tests and confirm the old adapter contract fails**

```bash
bash ./gradlew test --tests com.mirigangneung.infrastructure.kakao.HttpKakaoRouteClientTest --tests com.mirigangneung.course.service.CourseRouteCalculatorTest
```

- [ ] **Step 3: Implement walking request/response parsing and route summary**

Use the injected `ObjectMapper`, keep the existing `KakaoRouteClient` interface shape, and make the route calculator best-effort so course CRUD remains usable when route metrics are unavailable. Include route status in every CourseResponse.

- [ ] **Step 4: Run route and full backend tests**

```bash
bash ./gradlew test --tests com.mirigangneung.infrastructure.kakao.HttpKakaoRouteClientTest --tests com.mirigangneung.course.service.CourseRouteCalculatorTest
bash ./gradlew test
```

- [ ] **Step 5: Commit walking metrics**

```bash
git add src/main/java/com/mirigangneung/infrastructure/kakao src/main/java/com/mirigangneung/course/service src/main/resources/application.yml .env.example src/test/java/com/mirigangneung/infrastructure/kakao src/test/java/com/mirigangneung/course/service
git commit -m "feat: calculate course walking metrics"
```

### Task 5: Connect frontend course creation and API queries

**Files:**

- Create: `src/lib/courseApi.ts`
- Create: `src/lib/courseApi.test.ts`
- Modify: `src/types/api.ts`
- Modify: `src/types/domain.ts`
- Modify: `src/store/useAppStore.ts`
- Modify: `src/queries/usePlacesQuery.ts`
- Modify: `src/pages/CourseOptionsPage.tsx`
- Modify: `src/components/organisms/CourseOptions.tsx`
- Modify: `src/pages/CourseResultPage.tsx`

**Interfaces:**

- `createCourse(request: CreateCourseRequest): Promise<BackendCourseResponse>`
- `fetchCourse(courseId: string): Promise<BackendCourseResponse>`
- `fetchNearbyPlaces(courseId: string, category: 'restaurant' | 'cafe'): Promise<NearbyPlacesResponse>`
- `addExternalStop(courseId: string, place: NearbyPlace): Promise<BackendCourseResponse>`
- `deleteCourseStop(courseId: string, stopId: string): Promise<BackendCourseResponse>`
- `reorderCourseStops(courseId: string, stopIds: string[]): Promise<BackendCourseResponse>`
- Zustand gains nullable `courseId` and `setCourseId`, persisted in the existing session storage.

- [ ] **Step 1: Write failing API mapping tests**

Test request body mapping for selected IDs/types/companion/duration/dates, response mapping from backend `sequence/placeId/stopId/kind/routeSegments` to the frontend `CourseStop`, and the nearby/add/delete/reorder URL/method/body contracts.

- [ ] **Step 2: Run focused frontend tests and confirm missing API module failures**

```bash
npm test -- --run src/lib/courseApi.test.ts
```

- [ ] **Step 3: Implement the API client and query hooks**

Use `VITE_API_BASE_URL`, parse non-2xx responses into a typed `ApiError`, and make `CourseOptionsPage` await course creation before navigation. Store the returned course ID and make `CourseResultPage` query the server course rather than `buildMockCourseStops`.

- [ ] **Step 4: Run focused tests, build, and existing query tests**

```bash
npm test -- --run src/lib/courseApi.test.ts src/queries/usePlacesQuery.test.ts
npm run build
```

- [ ] **Step 5: Commit frontend API wiring**

```bash
git add src/lib/courseApi.ts src/lib/courseApi.test.ts src/types src/store/useAppStore.ts src/queries/usePlacesQuery.ts src/pages/CourseOptionsPage.tsx src/components/organisms/CourseOptions.tsx src/pages/CourseResultPage.tsx
git commit -m "feat: connect frontend to course API"
```

### Task 6: Build the course-result place management UI

**Files:**

- Create: `src/lib/courseStops.ts`
- Create: `src/lib/courseStops.test.ts`
- Modify: `src/components/organisms/CourseResult.tsx`
- Modify: `src/pages/CourseResultPage.tsx`
- Modify: `src/components/organisms/CourseMap.tsx`
- Modify: `src/components/organisms/courseMapHelpers.ts`
- Modify: `src/components/organisms/courseMapHelpers.test.ts`

**Interfaces:**

- `reorderStopIds(stopIds: string[], fromIndex: number, toIndex: number): string[]`
- `filterNearbyPlaces(places: NearbyPlace[], query: string): NearbyPlace[]`
- `CourseResult` receives server `course`, `nearbyPlaces`, `nearbyCategory`, loading/error flags, and callbacks for category change, search, add, delete, reorder, and active stop.

- [ ] **Step 1: Write failing pure-helper/UI behavior tests**

Cover stable drag reorder, local search filtering across name/address/category, category tabs, external place cards showing nearest tourism stop/distance, disabled duplicate add state, and one-pick delete protection.

- [ ] **Step 2: Run focused frontend tests and confirm the new helper/UI behavior is absent**

```bash
npm test -- --run src/lib/courseStops.test.ts src/components/organisms/CourseResult.test.tsx
```

- [ ] **Step 3: Implement the minimal UI**

Replace the direct `searchKakaoPlaces` call with the backend nearby query. Render `음식점`/`카페` tabs, loading/error/empty states, local search, a card with address/distance/reference tourist place, and an explicit `코스에 추가` action. Add delete controls to non-one-pick stops. Use native HTML drag events for reorder, update the list optimistically, call the API, and restore the prior course on rejection.

- [ ] **Step 4: Sync map and route summary**

Render all server stops in sequence order, use `stopId` for active keys/markers, use the server route points when available, and display the server total distance/travel time instead of hardcoded `이동 42km`. Keep the existing map zoom-preservation behavior.

- [ ] **Step 5: Run focused tests, full Vitest, lint, and production build**

```bash
npm test -- --run src/lib/courseStops.test.ts src/components/organisms/CourseResult.test.tsx
npm test -- --run
npm run lint
npm run build
```

- [ ] **Step 6: Commit the course-result UI**

```bash
git add src/lib/courseStops.ts src/lib/courseStops.test.ts src/components/organisms/CourseResult.tsx src/pages/CourseResultPage.tsx src/components/organisms/CourseMap.tsx src/components/organisms/courseMapHelpers.ts src/components/organisms/courseMapHelpers.test.ts
git commit -m "feat: manage nearby places in course result"
```

### Task 7: Update API documentation, status, and run integration checks

**Files:**

- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/openapi.yaml`
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/WORK_LOG.md`
- Create: `docs/adr/2026-08-25-kakao-course-place-snapshots.md`

- [ ] **Step 1: Document the new endpoints and environment variables**

Document the nearby/add/delete/order endpoints, `CourseResponse` additions, Kakao REST key configuration, empty/error semantics, and local curl examples. Add the official Kakao Local and walking API links used by the adapter.

- [ ] **Step 2: Add the ADR**

Record why Kakao Local is backend-only, why external places are course-owned snapshots rather than global KTO places, and why all selected tourism stops are searched and merged by external ID.

- [ ] **Step 3: Update current status and work log with actual results**

Keep `PROJECT_STATUS.md` factual, set the current KST timestamp and agent, and record the baseline test failure resolution, final test counts, changed files, and commit hashes in `WORK_LOG.md`.

- [ ] **Step 4: Run final verification**

```bash
cd /Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-backend-course-place
bash ./gradlew test
cd /Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-frontend-course-place
npm test -- --run
npm run lint
npm run build
```

Also run a local contract smoke test with Kakao disabled to verify that the app returns the documented `KAKAO_API_NOT_CONFIGURED` state without exposing a key.

- [ ] **Step 5: Commit documentation and verification notes**

```bash
git add docs/API_CONTRACT.md docs/openapi.yaml docs/PROJECT_STATUS.md docs/WORK_LOG.md docs/adr/2026-08-25-kakao-course-place-snapshots.md
git commit -m "docs: document course place management contract"
```
