# Automatic Nearby Radius and Map Viewport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically broaden nearby recommendations from 2km to 5km, 10km, and 15km when exact preference matches are insufficient, while preserving the user's Kakao Map viewport across course updates and focusing on a selected tourism stop.

**Architecture:** The backend keeps the existing preference-aware nearby search and adds 15km as the final automatic radius. The response exposes the effective search radius so the frontend can show a lightweight notice without offering a manual radius selector. The map stores a plain center/level snapshot before course-stop updates recreate the Kakao map, and applies a focused center/zoom only when a specific tourism stop is selected.

**Tech Stack:** Spring Boot, JUnit 5, Mockito, React, TypeScript, Vitest, Kakao Maps JavaScript SDK.

**Spec:** Latest user request: automatic nearby-radius expansion and map viewport persistence/focus.

## Global Constraints

- Nearby search radius is automatic only: `2km → 5km → 10km → 15km`.
- The next radius is queried only when applicable exact detail-preference matches remain below 3.
- The frontend must not expose a manual radius control.
- Adding a course place must preserve the user's current map center and zoom level.
- Selecting one tourism stop as the nearby-search basis should pan and zoom toward that stop.
- Existing uncommitted changes in both worktrees must remain intact.

### Task 1: Complete automatic backend radius metadata

**Files:**
- Modify: `src/main/java/com/mirigangneung/course/service/CoursePlaceService.java`
- Modify: `src/main/java/com/mirigangneung/course/dto/NearbyPlacesResponse.java`
- Modify: `src/test/java/com/mirigangneung/course/service/CoursePlaceServiceTest.java`
- Modify: `src/main/java/com/mirigangneung/course/controller/CourseController.java` only if endpoint documentation annotations are present
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/openapi.yaml`

**Interfaces:**
- Produces `NearbyPlacesResponse.searchRadiusMeters` for `nearby`; `all` remains `null`.
- Keeps the existing `CoursePlaceService.nearby` overloads source-compatible.

- [ ] **Step 1: Write the failing test**

Add a service test with two exact Chinese candidates at 2/5/10km and no third exact candidate until 15km. Assert that the 15km Kakao request is made, the 15km candidate is returned, and the response radius is `15_000`.

- [ ] **Step 2: Run the focused test and verify it fails**

Run `./gradlew test --tests com.mirigangneung.course.service.CoursePlaceServiceTest` from the backend worktree. Expected failure: no 15km request/metadata exists yet.

- [ ] **Step 3: Implement the minimal radius loop and response metadata**

Replace the duplicated 5km/10km branches with an ordered radius sequence ending at 15km. Track the last attempted radius and add it to nearby responses; preserve existing compatibility constructors and leave all-search responses without a radius.

- [ ] **Step 4: Run the focused backend tests and verify they pass**

Run the same focused Gradle test command and confirm zero failures.

- [ ] **Step 5: Update the API contract**

Document automatic 15km expansion and the `searchRadiusMeters` nearby response field without documenting a user-selectable radius.

### Task 2: Preserve and focus the Kakao Map viewport

**Files:**
- Modify: `src/types/kakao-maps.d.ts`
- Modify: `src/components/organisms/courseMapViewport.ts`
- Modify: `src/components/organisms/courseMapViewport.test.ts`
- Modify: `src/components/organisms/CourseMap.tsx`
- Modify: `src/components/organisms/CourseResult.tsx`
- Modify: `src/pages/CourseResultPage.tsx`

**Interfaces:**
- `CourseMap` consumes an optional `nearbyStopId`.
- `useNearbyPlacesQuery` exposes the backend effective radius to the page/result layer.

- [ ] **Step 1: Write failing viewport tests**

Test capturing a map center/level, restoring it on a new map, and focusing a selected stop without changing an already closer zoom level.

- [ ] **Step 2: Run the focused Vitest tests and verify they fail**

Run `npm test -- --run src/components/organisms/courseMapViewport.test.ts` from the frontend worktree. Expected failure: viewport helpers do not exist.

- [ ] **Step 3: Implement viewport snapshot and selected-stop focus**

Add Kakao `getCenter`, `getLevel`, and `LatLng.getLat/getLng` typings. Capture the current viewport in the map effect cleanup, skip initial `setBounds` when restoring a saved viewport, and pass the selected tourism stop ID to focus that stop at a closer level only when the user changes the basis stop.

- [ ] **Step 4: Run focused frontend tests and verify they pass**

Run the viewport test and the existing course/map component tests.

### Task 3: Surface automatic expansion without a radius control

**Files:**
- Modify: `src/types/api.ts`
- Modify: `src/types/domain.ts`
- Modify: `src/queries/useCoursePlacesQuery.ts`
- Modify: `src/components/organisms/CourseResult.tsx`
- Modify: `src/components/organisms/CoursePlaceSidebar.tsx`
- Modify: `src/lib/courseApi.test.ts`
- Modify: `src/components/organisms/CoursePlaceSidebar.test.tsx`

**Interfaces:**
- `NearbyPlaceSearchState.searchRadiusMeters` is nullable.
- The sidebar receives a read-only automatic-expansion notice and no range selector.

- [ ] **Step 1: Write failing API/UI tests**

Assert that `searchRadiusMeters: 10000` is mapped and that nearby sidebar markup contains an automatic expansion notice while containing no radius selector.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run the course API and sidebar tests. Expected failure: radius metadata is not mapped or rendered.

- [ ] **Step 3: Implement response mapping and notice**

Carry the latest page metadata through the query and render copy such as `조건에 맞는 장소가 적어 10km까지 자동으로 넓혔어요.` only when the effective radius exceeds 2km.

- [ ] **Step 4: Run all frontend tests and type/build checks**

Run `npm test -- --run`, `npm run lint`, and `npm run build` from the frontend worktree.

### Task 4: Full verification

- [ ] **Step 1: Run the complete backend test suite**

Run `./gradlew test` from the backend worktree.

- [ ] **Step 2: Run the complete frontend test, lint, and build suite**

Run `npm test -- --run`, `npm run lint`, and `npm run build` from the frontend worktree.

- [ ] **Step 3: Review the diff and confirm unrelated changes are preserved**

Run `git diff --check` and inspect `git status --short` in both worktrees before reporting.
