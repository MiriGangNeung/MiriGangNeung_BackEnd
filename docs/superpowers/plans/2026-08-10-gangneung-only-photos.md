# Gangneung-Only Tourism Photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict both background-photo sources to photos whose recorded location is Gangneung, excluding the five non-Gangneung Gangwon award results and all other regions.

**Architecture:** Keep the backend as the source-of-truth for geographic scope. The award client receives the existing Gangwon region request, while the gallery client switches to KTO's `gallerySearchList1` keyword operation with `강릉`; both backend services apply a strict location check containing `강릉` before returning DTOs. The frontend continues consuming the same source-tab endpoints, with the award adapter restoring the explicit `region=51` request.

**Tech Stack:** Java 17, Spring Boot 4, RestClient, JUnit 5, Mockito, MockRestServiceServer, React/TypeScript, Vitest.

## Global Constraints

- Only records whose location contains the Korean text `강릉` are returned.
- `lDongRegnCd=51` is a coarse Gangwon filter, not a Gangneung filter; it must not be treated as sufficient by itself.
- Gallery requests use `PhotoGalleryService1/gallerySearchList1` with URL-encoded keyword `강릉`.
- Unit tests never call KTO; use MockRestServiceServer and mocked client interfaces.
- Preserve the existing source tabs and downstream `Place` IDs.
- Do not stage `.DS_Store`, `docs/PROJECT_STRUCTURE_AND_COMMUNICATION.md`, or unrelated frontend user changes.

---

### Task 1: Apply the Gangneung scope to backend services

**Files:**
- Modify: `src/main/java/com/mirigangneung/place/service/AwardPhotoService.java`
- Modify: `src/main/java/com/mirigangneung/place/service/TourismPhotoService.java`
- Modify: `src/main/java/com/mirigangneung/place/controller/AwardPhotoController.java`
- Modify: `src/test/java/com/mirigangneung/place/service/AwardPhotoServiceTest.java`
- Modify: `src/test/java/com/mirigangneung/place/service/TourismPhotoServiceTest.java`

**Interfaces:**
- Award service keeps `search(String region, int page, int size)` and defaults the controller region to `51`.
- Gallery service calls `PhotoGalleryApiClient.search("강릉", page, size)`.
- Both services drop records when `location == null` or `location` does not contain `강릉`, before building the page response.

- [ ] **Step 1: Add failing service tests.**

  Add a non-Gangneung photo with a valid image to each service fixture and assert it is absent from the response. Update gallery mock calls to include the exact keyword `강릉`. Update the award controller contract test to expect the region default `51`.

- [ ] **Step 2: Run the focused backend tests and verify RED.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*AwardPhotoServiceTest' --tests '*TourismPhotoServiceTest'
  ```

  Expected: the tests fail because services currently return valid-image records from every location and the gallery service does not pass a keyword.

- [ ] **Step 3: Implement the smallest backend scope change.**

  Filter each mapped DTO with `photo.location() != null && photo.location().contains("강릉")`; set the award controller's `region` parameter back to `@RequestParam(defaultValue = "51")`; call the gallery client with the literal scope keyword `강릉`.

- [ ] **Step 4: Run focused tests and confirm GREEN.**

  Re-run the command from Step 2 and confirm both non-Gangneung records are excluded.

- [ ] **Step 5: Commit backend scope filtering.**

  ```bash
  git add src/main/java/com/mirigangneung/place/service/AwardPhotoService.java src/main/java/com/mirigangneung/place/service/TourismPhotoService.java src/main/java/com/mirigangneung/place/controller/AwardPhotoController.java src/test/java/com/mirigangneung/place/service/AwardPhotoServiceTest.java src/test/java/com/mirigangneung/place/service/TourismPhotoServiceTest.java
  git commit -m "fix: restrict tourism photos to Gangneung"
  ```

### Task 2: Use the KTO gallery keyword-search operation

**Files:**
- Modify: `src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryApiClient.java`
- Modify: `src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClient.java`
- Modify: `src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClientTest.java`

**Interfaces:**

```java
List<PhotoGalleryPhoto> search(String keyword, int page, int size);
```

- [ ] **Step 1: Update the client test first.**

  Change the fixture call to `search("강릉", 0, 100)`, expect path `/B551011/PhotoGalleryService1/gallerySearchList1`, assert `keyword=강릉` plus `arrange=C`, common parameters, page/size, and the existing encoded service key.

- [ ] **Step 2: Run the gallery client test and verify RED.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourPhotoGalleryClientTest'
  ```

  Expected: compilation or request assertions fail because the interface and implementation still use `galleryList1` without a keyword.

- [ ] **Step 3: Implement the keyword request.**

  Change the interface signature, use `gallerySearchList1`, add `keyword` with `URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8)`, keep `build(true)`, and return an empty list when the keyword is blank or the key is blank.

- [ ] **Step 4: Run the client test and confirm GREEN.**

  ```bash
  GRADLE_USER_HOME=/private/tmp/miri-gangneung-gradle bash ./gradlew --no-daemon test --tests '*KoreanTourPhotoGalleryClientTest'
  ```

- [ ] **Step 5: Commit the KTO operation change.**

  ```bash
  git add src/main/java/com/mirigangneung/infrastructure/tourapi/PhotoGalleryApiClient.java src/main/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClient.java src/test/java/com/mirigangneung/infrastructure/tourapi/KoreanTourPhotoGalleryClientTest.java
  git commit -m "fix: search gallery photos by Gangneung keyword"
  ```

### Task 3: Restore frontend regional request and verify runtime

**Files:**
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.ts`
- Modify: `MiriGangNeung_FrontEnd/src/lib/awardPhotosApi.test.ts`

- [ ] **Step 1: Update the frontend request test to expect `region=51`.**
- [ ] **Step 2: Run the focused test and verify RED.**

  ```bash
  npm test -- --run src/lib/awardPhotosApi.test.ts
  ```

- [ ] **Step 3: Restore `region=51` in the award endpoint URL and run the focused test GREEN.**
- [ ] **Step 4: Run backend full tests, frontend full tests, build, and lint.**
- [ ] **Step 5: Rebuild Docker and verify both endpoint responses contain only locations including `강릉`.**

  ```bash
  MYSQL_PORT=3307 docker compose up -d --build --force-recreate
  curl --fail 'http://localhost:8080/api/v1/award-photos?page=0&size=100'
  curl --fail 'http://localhost:8080/api/v1/tourism-photos?page=0&size=100'
  ```

- [ ] **Step 6: Commit the frontend request change.**

  ```bash
  git add src/lib/awardPhotosApi.ts src/lib/awardPhotosApi.test.ts
  git commit -m "fix: request Gangneung award photos"
  ```
