# 공모전 수상작 배경 이미지 POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use only KTO `PhokoAwrdService` contest-winning photographs in the first frontend picker so the team can inspect their quality.

**Architecture:** Keep `KorService2` and `/api/v1/places` unchanged. Add a read-only award-photo client/service/controller that calls `phokoAwrdSyncList`, then adapt its response to the existing frontend card model without database persistence.

**Tech Stack:** Java 17, Spring Boot 4, `RestClient`, Jackson, JUnit 5, AssertJ, `MockRestServiceServer`, React/TypeScript, TanStack Query, Vitest, Docker Compose.

## Global Constraints

- Backend branch: `develop`; frontend branch: `fix/background-picker-api`.
- Do not stage `.DS_Store` or `docs/PROJECT_STRUCTURE_AND_COMMUNICATION.md`.
- Do not modify or delete the existing `KorService2` implementation; the frontend query ignores it during this POC.
- Call `PhokoAwrdService/phokoAwrdSyncList` with `showflag=1`, `lDongRegnCd=51`, `arrange=C`, `MobileOS=ETC`, `MobileApp=MiriGangNeung`, and `_type=json`.
- Do not save award photos in `places` or image tables.
- Do not expose service keys; exclude records with both image URLs blank.
- Unit tests must not call KTO. Preserve the existing Jackson 2 compatibility fix and context test.

---

### Task 1: KTO award-photo client

**Files:** Create `AwardPhotoApiClient.java`, `KoreanTourAwardPhotoClient.java`, `AwardPhotoProperties.java`, and `KoreanTourAwardPhotoClientTest.java` under `src/main/java/com/mirigangneung/infrastructure/tourapi` and `src/test/java/com/mirigangneung/infrastructure/tourapi`; modify `TourApiConfig.java` and `application.yml`.

**Interfaces:**

- `AwardPhotoApiClient.search(String regionCode, int page, int size)` returns `List<AwardPhotoApiClient.AwardPhoto>`.
- `AwardPhoto` fields: `contentId`, `title`, `location`, `award`, `keywords`, `originalImageUrl`, `thumbnailUrl`, `photographer`, `copyrightCode`.
- `AwardPhotoProperties` binds `tour.award.base-url`, `tour.award.key`, and `tour.award.timeout`; `tour.award.key` falls back to `TOUR_API_KEY` in YAML.

- [ ] **Step 1: Write the failing test.** Use `MockRestServiceServer` and a fixture response with one complete item plus one item with blank `orgImage` and `thumbImage`. Assert request path `/B551011/PhokoAwrdService/phokoAwrdSyncList`, `pageNo=1` for page `0`, `numOfRows=100`, `lDongRegnCd=51`, `showflag=1`, `arrange=C`, `MobileOS=ETC`, `MobileApp=MiriGangNeung`, and `_type=json`. Assert the complete item maps `koTitle`, `koFilmst`, `koWnprzDiz`, comma-separated `koKeyword`, `orgImage`, `thumbImage`, `koCmanNm`, and `cpyrhtDivCd`; the blank-image item is excluded; blank key makes no request; an upstream error throws `TOUR_API_ERROR`.
- [ ] **Step 2: Run the red test.** Run `GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourAwardPhotoClientTest'`. It must fail because the client does not exist.
- [ ] **Step 3: Implement the minimum.** Build the configured PhokoAwrdService URI, decode the service key once, reuse `TourApiResponseParser`, map the listed fields, filter blank-image records, and add award base URL/key/timeout defaults.
- [ ] **Step 4: Run the same focused test and confirm green.**
- [ ] **Step 5: Commit with `git add` for only the Task 1 files and `git commit -m "feat: add KTO award photo client"`.**

### Task 2: Read-only backend endpoint

**Files:** Create `AwardPhotoResponse.java`, `AwardPhotoPageResponse.java`, `AwardPhotoService.java`, `AwardPhotoController.java`, and `AwardPhotoServiceTest.java` under the existing place DTO/service/controller/test packages.

**Interfaces:** `GET /api/v1/award-photos?region=51&page=0&size=100`; `AwardPhotoService.search(String region, int page, int size)` returns a page response with `content`, `page`, `size`, `totalElements`, and `totalPages`.

- [ ] **Step 1: Write the failing service test.** Mock `AwardPhotoApiClient`; assert mapping to response fields, original-image preference, thumbnail fallback, keyword list, source `KTO_AWARD`, and filtering of image-less records. Add a controller test or direct validation test for defaults `region=51`, `page=0`, `size=100` and rejection of `size > 100`.
- [ ] **Step 2: Run the red test.** Run `GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*AwardPhotoServiceTest'`; it must fail because the endpoint types do not exist.
- [ ] **Step 3: Implement the minimum.** Map the client record to `AwardPhotoResponse(id,title,location,award,keywords,originalImageUrl,thumbnailUrl,photographer,copyrightCode,"KTO_AWARD")`; return HTTP 200 for an empty batch; propagate the existing tourism 502 error; never call `PlaceRepository`.
- [ ] **Step 4: Run `GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*AwardPhotoServiceTest' --tests '*MiriGangNeungApplicationTest'` and confirm green.
- [ ] **Step 5: Commit only Task 2 files with `git commit -m "feat: expose award photo preview endpoint"`.**

### Task 3: Frontend award-photo adapter and query switch

**Files:** Create `/Users/seob/Desktop/MiriGangNeung/MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.ts` and `awardPhotosApi.test.ts`; modify `/Users/seob/Desktop/MiriGangNeung/MiriGangNeung_FrontEnd/src/queries/usePlacesQuery.ts`.

**Interfaces:** `mapAwardPhotosResponse(response: BackendAwardPhotosResponse): Place[]`; `fetchAwardPhotos(baseUrl?: string): Promise<Place[]>`; query key `['award-photos']`.

- [ ] **Step 1: Write the failing Vitest.** Assert `id: 'kto-award:award-1'`, `name: title`, `region: location`, `cat: 'nature'`, tags containing award plus two keywords, and `thumbnailUrl` preferring `originalImageUrl`; assert thumbnail fallback, image-less filtering, and URL `/award-photos?region=51&page=0&size=100`.
- [ ] **Step 2: Run `npm test -- --run src/lib/awardPhotosApi.test.ts` in the frontend; confirm it fails because the adapter is absent.**
- [ ] **Step 3: Implement `fetchAwardPhotos` and the adapter.** Use original image then thumbnail, set `lat/lng` to `0`, include at most two non-empty keywords, and make `usePlacesQuery` call this fetcher with key `['award-photos']`.
- [ ] **Step 4: Run the focused test, `npm run build`, and `npm run lint`; require passing tests/build and zero lint errors.**
- [ ] **Step 5: Commit only Task 3 files with `git commit -m "feat: use award photos in place query"`.**

### Task 4: Runtime picker verification

**Files:** Inspect `BackgroundPicker.tsx` and `PlaceCard.tsx`; modify only if verification proves it necessary.

- [ ] **Step 1: Run `rg -n "PLACES|PLACE_PHOTOS|HERO_PHOTO|fetchPlaces|fetchAwardPhotos" src/components src/pages src/queries src/lib` and confirm the picker path uses `fetchAwardPhotos` with no mock photo import.**
- [ ] **Step 2: Run `MYSQL_PORT=3307 docker compose up -d --build` in the backend repository.**
- [ ] **Step 3: Run `curl --fail --silent --show-error 'http://localhost:8080/api/v1/award-photos?region=51&page=0&size=100'`; require HTTP 200 and at least one non-empty original or thumbnail URL. If the configured key lacks PhokoAwrdService permission, report that exact error and do not substitute KorService2 data.**
- [ ] **Step 4: Run `curl --fail --silent --show-error --location -o /dev/null -w '%{http_code} %{content_type}\n' '<returned-image-url>'`; require `200 image/*`.**

### Task 5: Full verification and handoff

- [ ] **Step 1: Run backend `GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test`; frontend `npm test -- --run`, `npm run build`, and `npm run lint`.**
- [ ] **Step 2: Run `git diff --check` and `git status --short --branch` in both repositories; preserve unrelated user files.**
- [ ] **Step 3: Report endpoint, KTO source, returned photo count, image URL status, commits, and the intentional limitation that award IDs are display-only for this POC and are not yet real course/composition place IDs.**
