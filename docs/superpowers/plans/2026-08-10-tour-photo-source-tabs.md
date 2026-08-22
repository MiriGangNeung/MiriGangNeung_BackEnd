# 관광사진 소스 탭 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand award-photo results to nationwide data, add KTO `PhotoGalleryService1`, and let the frontend compare both sources through tabs in the first background picker.

**Architecture:** Keep the two KTO services behind independent read-only clients and endpoints. Normalize both responses into the frontend `Place` model with a source discriminator, combine them in one cached query so selected IDs remain available on later screens, and filter the picker by an explicit source tab before applying the existing category filter.

**Tech Stack:** Java 17, Spring Boot 4, `RestClient`, Jackson 2 compatibility mapper, JUnit 5, AssertJ, Mockito, `MockRestServiceServer`, React/TypeScript, TanStack Query, Vitest, Docker Compose.

## Global Constraints

- Backend branch: `develop`; frontend branch: `fix/background-picker-api`.
- Do not stage `.DS_Store` or `docs/PROJECT_STRUCTURE_AND_COMMUNICATION.md`.
- Preserve the existing `/api/v1/places` and `KorService2` implementation; this work is for background-photo preview data.
- Do not persist award or gallery photos in `places` or `place_images`.
- Unit tests must not call KTO; use `MockRestServiceServer` and mocked client interfaces.
- Service keys are decoded once from configuration, encoded with `URLEncoder`, and passed through `UriComponentsBuilder.build(true)` so `+`, `/`, and `=` are not corrupted.
- Exclude records whose original and fallback image URLs are both blank.
- The award endpoint omits `lDongRegnCd` by default for nationwide results; its optional region argument remains available for future requests.
- KTO photo-gallery URLs from `tong.visitkorea.or.kr` must be normalized from `http` to `https` before returning them to the browser.
- Source values are `KTO_AWARD`/`KTO_PHOTO_GALLERY` in backend responses and `award`/`gallery` in frontend `Place` values.
- Photo IDs are display-only POC IDs and must not be treated as real tourism-place IDs for course or composition APIs.

---

### Task 1: Make award photos nationwide

**Files:**
- Modify: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourAwardPhotoClient.java`
- Modify: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/place/controller/AwardPhotoController.java`
- Modify: `MiriGangNeung_BackEnd/src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourAwardPhotoClientTest.java`
- Modify: `MiriGangNeung_BackEnd/src/test/java/com/mirigangneung/place/service/AwardPhotoServiceTest.java`
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.ts`
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.test.ts`

**Interfaces:**
- Backend client keeps `search(String regionCode, int page, int size)`; `null` or blank `regionCode` must omit `lDongRegnCd`.
- Controller changes the region request parameter to optional instead of defaulting it to `51`.
- Frontend `fetchAwardPhotos(baseUrl)` calls `/award-photos?page=0&size=100` without `region=51`.
- Award adapter adds `source: 'award'` after the domain source type is introduced in Task 4.

- [ ] **Step 1: Write the failing nationwide tests.**

  Add a client test that calls `search(null, 0, 100)` and asserts the request path is `/B551011/PhokoAwrdService/phokoAwrdSyncList`, the required `showflag=1`/`arrange=C` parameters remain, and `lDongRegnCd` is absent. Update the controller contract test to assert the region parameter is optional and the default page/size remain `0`/`100`. Update the frontend fetch test to expect `/award-photos?page=0&size=100`.

- [ ] **Step 2: Run the focused tests and verify they fail for the old region contract.**

  Run:

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourAwardPhotoClientTest' --tests '*AwardPhotoServiceTest'
  npm test -- --run src/lib/awardPhotosApi.test.ts
  ```

  Expected: the new request assertions fail because the current controller/frontend still default to `51`.

- [ ] **Step 3: Implement the smallest contract change.**

  Remove the controller's `defaultValue = "51"`, keep `@RequestParam(required = false)`, pass `null` through the service, and retain conditional `lDongRegnCd` construction in the client. Remove `region=51` from the frontend award endpoint URL. Do not change the existing KTO endpoint or response field mapping.

- [ ] **Step 4: Run focused tests and confirm green.**

  Re-run the commands from Step 2. The client must prove both paths: a null region omits the parameter, while an explicit region still sends it.

- [ ] **Step 5: Commit the nationwide contract.**

  ```bash
  git add src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourAwardPhotoClient.java src/main/java/com/mirigangneung/place/controller/AwardPhotoController.java src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourAwardPhotoClientTest.java src/test/java/com/mirigangneung/place/service/AwardPhotoServiceTest.java
  git commit -m "feat: fetch nationwide award photos"
  ```

  In the frontend repository, stage only `src/lib/awardPhotosApi.ts` and `src/lib/awardPhotosApi.test.ts` and commit:

  ```bash
  git commit -m "feat: request nationwide award photos"
  ```

### Task 2: Add the PhotoGalleryService1 client

**Files:**
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryApiClient.java`
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryProperties.java`
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClient.java`
- Create: `MiriGangNeung_BackEnd/src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClientTest.java`
- Modify: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiConfig.java`
- Modify: `MiriGangNeung_BackEnd/src/main/resources/application.yml`
- Modify: `MiriGangNeung_BackEnd/docker-compose.yml`
- Modify: `MiriGangNeung_BackEnd/.env.example`

**Interfaces:**

```java
public interface PhotoGalleryApiClient {
    List<PhotoGalleryPhoto> search(int page, int size);

    record PhotoGalleryPhoto(
            String contentId,
            String title,
            String location,
            String photographyMonth,
            List<String> keywords,
            String originalImageUrl,
            String thumbnailUrl,
            String photographer) {}
}
```

- `PhotoGalleryProperties` binds `tour.photo-gallery.base-url`, `tour.photo-gallery.key`, and `tour.photo-gallery.timeout`.
- `KoreanTourPhotoGalleryClient.search(0, 100)` calls `/B551011/PhotoGalleryService1/galleryList1` with `arrange=C`, `MobileOS=ETC`, `MobileApp=MiriGangNeung`, `_type=json`, `pageNo=1`, and `numOfRows=100`.

- [ ] **Step 1: Write the failing client tests.**

  Use `MockRestServiceServer` with a fixture containing one complete gallery item and one item whose `galWebImageUrl` is blank. Assert `/galleryList1`, all common query parameters, key encoding with a fixture key containing `%2B`, field mapping from `galContentId`, `galTitle`, `galPhotographyLocation`, `galPhotographyMonth`, `galSearchKeyword`, `galWebImageUrl`, and `galPhotographer`, blank-image filtering, empty-key no-call behavior, and `TOUR_API_ERROR` on an upstream error.

- [ ] **Step 2: Run the client test red.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourPhotoGalleryClientTest'
  ```

  Expected: compilation fails because the gallery client and properties do not exist.

- [ ] **Step 3: Implement the minimum client and configuration.**

  Reuse `TourApiResponseParser` and the award client's timeout/error pattern. Decode the configured key once, encode it with `URLEncoder.encode(..., StandardCharsets.UTF_8)`, call `build(true)`, split `galSearchKeyword` on commas, and map `galWebImageUrl` to both image fields. Normalize only `http://tong.visitkorea.or.kr` to `https://tong.visitkorea.or.kr`; leave unrelated URLs unchanged. Register `PhotoGalleryProperties` in `TourApiConfig` and add:

  ```yaml
  tour:
    photo-gallery:
      base-url: ${TOUR_PHOTO_GALLERY_API_BASE_URL:https://apis.data.go.kr/B551011/PhotoGalleryService1}
      key: ${TOUR_PHOTO_GALLERY_API_KEY:${TOUR_API_KEY:}}
      timeout: ${TOUR_PHOTO_GALLERY_API_TIMEOUT:5s}
  ```

  Pass `TOUR_PHOTO_GALLERY_API_KEY: ${TOUR_PHOTO_GALLERY_API_KEY:-${TOUR_API_KEY:-}}` through Docker Compose and document the optional variable in `.env.example` without committing a value.

- [ ] **Step 4: Run the focused client test green.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourPhotoGalleryClientTest'
  ```

- [ ] **Step 5: Commit the gallery client.**

  ```bash
  git add src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryApiClient.java src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryProperties.java src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClient.java src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClientTest.java src/main/java/com/mirigangneung/infrastructure/tourapi/TourApiConfig.java src/main/resources/application.yml docker-compose.yml .env.example
  git commit -m "feat: add KTO photo gallery client"
  ```

### Task 3: Expose the gallery backend endpoint

**Files:**
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/place/dto/TourismPhotoResponse.java`
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/place/dto/TourismPhotoPageResponse.java`
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/place/service/TourismPhotoService.java`
- Create: `MiriGangNeung_BackEnd/src/main/java/com/mirigangneung/place/controller/TourismPhotoController.java`
- Create: `MiriGangNeung_BackEnd/src/test/java/com/mirigangneung/place/service/TourismPhotoServiceTest.java`

**Interfaces:**

```java
GET /api/v1/tourism-photos?page=0&size=100
TourismPhotoService.search(int page, int size)
  -> TourismPhotoPageResponse(content, page, size, totalElements, totalPages)
```

`TourismPhotoResponse` fields are `id`, `title`, `location`, `photographyMonth`, `keywords`, `originalImageUrl`, `thumbnailUrl`, `photographer`, and `source`. The service maps the client record, sets `source` to `KTO_PHOTO_GALLERY`, filters records with no image, and never injects `PlaceRepository`.

- [ ] **Step 1: Write the failing service/controller test.**

  Mock `PhotoGalleryApiClient`, assert complete field mapping, source value, image URL preservation, blank-image filtering, empty successful batch as HTTP-compatible page data, controller defaults `page=0`/`size=100`, and rejection of `size > 100` through executable validation.

- [ ] **Step 2: Run the focused test red.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*TourismPhotoServiceTest'
  ```

  Expected: compilation fails because the gallery endpoint types do not exist.

- [ ] **Step 3: Implement the endpoint.**

  Follow `AwardPhotoService`/`AwardPhotoController`: use `@RestController`, `@RequestMapping("/api/v1/tourism-photos")`, `@Validated`, `@Min(0)`, `@Min(1)`, and `@Max(100)`. Let `ApiException("TOUR_API_ERROR", BAD_GATEWAY, ...)` propagate from the client.

- [ ] **Step 4: Run the focused service test and context test green.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*TourismPhotoServiceTest' --tests '*MiriGangNeungApplicationTest'
  ```

- [ ] **Step 5: Commit the endpoint.**

  ```bash
  git add src/main/java/com/mirigangneung/place/dto/TourismPhotoResponse.java src/main/java/com/mirigangneung/place/dto/TourismPhotoPageResponse.java src/main/java/com/mirigangneung/place/service/TourismPhotoService.java src/main/java/com/mirigangneung/place/controller/TourismPhotoController.java src/test/java/com/mirigangneung/place/service/TourismPhotoServiceTest.java
  git commit -m "feat: expose tourism photo gallery endpoint"
  ```

### Task 4: Add frontend gallery adapter and combined source query

**Files:**
- Create: `MiriGangNeung_FrontEnd/src/lib/tourismPhotosApi.ts`
- Create: `MiriGangNeung_FrontEnd/src/lib/tourismPhotosApi.test.ts`
- Create: `MiriGangNeung_FrontEnd/src/lib/backgroundPhotosApi.ts`
- Create: `MiriGangNeung_FrontEnd/src/lib/backgroundPhotosApi.test.ts`
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.ts`
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.test.ts`
- Modify: `MiriGangNeung_FrontEnd/src/types/domain.ts`
- Modify: `MiriGangNeung_FrontEnd/src/queries/usePlacesQuery.ts`

**Interfaces:**

```ts
export type BackgroundPhotoSource = 'award' | 'gallery';

export interface Place {
  // existing fields...
  source?: BackgroundPhotoSource;
}

export async function fetchTourismPhotos(baseUrl?: string): Promise<Place[]>;
export async function fetchBackgroundPhotos(baseUrl?: string): Promise<Place[]>;
```

- `mapAwardPhotosResponse` sets `source: 'award'` and keeps `kto-award:<id>`.
- `mapTourismPhotosResponse` sets `source: 'gallery'`, uses `kto-gallery:<id>`, maps location/keywords/photographer, and chooses the image URL.
- `fetchBackgroundPhotos` starts both requests concurrently with `Promise.allSettled`. It returns successful results from either source; it throws only when both requests fail, preserving the existing page error state while allowing one source to remain usable.
- `usePlacesQuery` uses query key `['background-photos']` and `fetchBackgroundPhotos` so later pages can resolve IDs from either tab.

- [ ] **Step 1: Write failing Vitest tests.**

  Assert gallery field mapping, `kto-gallery:` IDs, source values, image fallback and HTTPS normalization. Assert the combined fetch requests `/award-photos?page=0&size=100` and `/tourism-photos?page=0&size=100`, merges successful responses, and returns one source when the other rejects; assert both failures reject.

- [ ] **Step 2: Run the focused frontend tests red.**

  ```bash
  npm test -- --run src/lib/tourismPhotosApi.test.ts src/lib/backgroundPhotosApi.test.ts
  ```

  Expected: module resolution fails because the gallery adapter and combined fetcher do not exist.

- [ ] **Step 3: Implement the adapters and combined query.**

  Keep the existing award mapping shape, add the source discriminator, create the gallery adapter against `BackendTourismPhotosResponse`, and implement `Promise.allSettled` with the exact “one succeeds / both fail” behavior above. Keep the endpoint base URL override through `VITE_API_BASE_URL`.

- [ ] **Step 4: Run focused tests, full Vitest, and build.**

  ```bash
  npm test -- --run src/lib/tourismPhotosApi.test.ts src/lib/backgroundPhotosApi.test.ts
  npm test -- --run
  npm run build
  ```

- [ ] **Step 5: Commit the frontend data layer.**

  ```bash
  git add src/lib/tourismPhotosApi.ts src/lib/tourismPhotosApi.test.ts src/lib/backgroundPhotosApi.ts src/lib/backgroundPhotosApi.test.ts src/lib/awardPhotosApi.ts src/lib/awardPhotosApi.test.ts src/types/domain.ts src/queries/usePlacesQuery.ts
  git commit -m "feat: combine award and gallery photo sources"
  ```

### Task 5: Add source tabs to the background picker

**Files:**
- Create: `MiriGangNeung_FrontEnd/src/lib/backgroundPhotoFilters.ts`
- Create: `MiriGangNeung_FrontEnd/src/lib/backgroundPhotoFilters.test.ts`
- Modify: `MiriGangNeung_FrontEnd/src/pages/BackgroundPickerPage.tsx`
- Modify: `MiriGangNeung_FrontEnd/src/components/organisms/BackgroundPicker.tsx`

**Interfaces:**

```ts
export const PHOTO_SOURCE_TABS = [
  { id: 'award', label: '공모전 수상작' },
  { id: 'gallery', label: '관광사진 갤러리' },
] as const;

export function filterBackgroundPhotos(
  places: Place[],
  source: BackgroundPhotoSource,
  category: string,
): Place[];
```

- [ ] **Step 1: Write the failing filter test.**

  Provide award and gallery fixtures, assert the selected source is applied before `all`/`filter`/category filtering, and assert an award source cannot appear while the gallery tab is active.

- [ ] **Step 2: Run the focused filter test red.**

  ```bash
  npm test -- --run src/lib/backgroundPhotoFilters.test.ts
  ```

  Expected: module resolution fails because the source filter helper does not exist.

- [ ] **Step 3: Implement the source-tab UI.**

  Add `source` state defaulting to `'award'` in `BackgroundPickerPage`. Pass `source` and `onSource` to `BackgroundPicker`. Render the two source buttons above the existing category chips and use `filterBackgroundPhotos` for the card list. Keep selection order, max-picks behavior, hero image, loading state, and existing category buttons unchanged.

- [ ] **Step 4: Run filter tests, full tests, build, and lint.**

  ```bash
  npm test -- --run src/lib/backgroundPhotoFilters.test.ts
  npm test -- --run
  npm run build
  npm run lint
  ```

  Lint must have zero errors; existing unrelated warnings in `PhotoUpload.tsx` may remain.

- [ ] **Step 5: Commit the source-tab UI.**

  ```bash
  git add src/lib/backgroundPhotoFilters.ts src/lib/backgroundPhotoFilters.test.ts src/pages/BackgroundPickerPage.tsx src/components/organisms/BackgroundPicker.tsx
  git commit -m "feat: add background photo source tabs"
  ```

### Task 6: Runtime smoke test and final verification

**Files:** No planned source changes; inspect only. Preserve unrelated user files.

- [ ] **Step 1: Rebuild and start the backend.**

  ```bash
  MYSQL_PORT=3307 docker compose up -d --build --force-recreate
  ```

- [ ] **Step 2: Verify both backend endpoints and count-bearing content.**

  ```bash
  curl --fail --silent --show-error 'http://localhost:8080/api/v1/award-photos?page=0&size=100'
  curl --fail --silent --show-error 'http://localhost:8080/api/v1/tourism-photos?page=0&size=100'
  ```

  Require HTTP 200, at least one item from each source, the expected source values, and non-empty image URLs. Do not substitute `/api/v1/places` data if either photo endpoint fails.

- [ ] **Step 3: Verify one image URL from each source.**

  ```bash
  curl --fail --silent --show-error --location -o /dev/null -w '%{http_code} %{content_type}\n' '<award-image-url>'
  curl --fail --silent --show-error --location -o /dev/null -w '%{http_code} %{content_type}\n' '<gallery-image-url>'
  ```

  Require `200 image/*` for both.

- [ ] **Step 4: Run complete project verification.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test
  npm test -- --run
  npm run build
  npm run lint
  git diff --check
  ```

- [ ] **Step 5: Report the handoff.**

  Report both endpoint paths, returned source counts, image URL status, commit hashes, and the display-only ID limitation. Report the exact pre-existing untracked/modified user files that were preserved.
