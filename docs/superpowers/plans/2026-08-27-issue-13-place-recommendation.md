# 장소 맞춤 추천 및 추천순 정렬 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이슈 #13의 여행 타입·동행 유형을 반영한 설명 가능한 주변 장소 추천과 추천순/거리순 정렬을 백엔드와 프론트엔드에 연결한다.

**Architecture:** 코스 생성 시 최대 2개의 여행 타입과 1개의 동행 유형을 `courses`에 보존한다. 주변 장소 조회는 Kakao 응답의 장소명·카테고리명·좌표·주소만 사용해 거리, 여행 타입, 동행 유형, 정보 완성도를 0~100점으로 계산하고 추천 이유를 함께 반환한다. 추천 장소는 조회 결과로만 제공하며, 사용자가 기존의 명시적인 추가 동작을 수행할 때만 snapshot으로 저장한다.

**Tech Stack:** Java 17, Spring Boot 4.0.7, Spring Data JPA, JUnit 5/AssertJ/Mockito, React 18, TypeScript, TanStack Query, Vitest.

**Spec:** GitHub issue #13 — `[FE/BE] 필터 기반 장소 맞춤 추천 및 추천순 정렬`

## Global Constraints

- Kakao 공식 장소 검색 응답에 없는 리뷰·별점·사진을 생성하거나 표시하지 않는다.
- LLM 및 새 외부 API를 추가하지 않고 현재 Kakao Local 응답 필드만 사용한다.
- 추천 장소는 사용자 동의 없이 코스에 자동 추가하지 않는다.
- 기존 거리순 조회와 기존 코스 추가·삭제·순서 변경 API의 동작을 보존한다.
- 기존 코스처럼 선호값이 없는 데이터는 추천 점수 없이 거리순 fallback으로 처리한다.
- 비밀키와 외부 API 인증정보는 코드나 테스트 fixture에 기록하지 않는다.

---

### Task 1: 코스 선호값 저장 모델과 API 응답 확장

**Files:**
- Modify: `src/main/java/com/mirigangneung/course/domain/Course.java`
- Modify: `src/main/java/com/mirigangneung/course/service/CourseService.java`
- Modify: `src/main/java/com/mirigangneung/course/dto/CourseResponse.java`
- Test: `src/test/java/com/mirigangneung/course/domain/CoursePreferenceTest.java`

**Interfaces:**
- Consumes: `CreateCourseRequest.types()`와 `CreateCourseRequest.companion()`
- Produces: `Course(String, LocalDate, LocalDate, List<String>, String)`, `getTravelTypes()`, `getCompanion()`, 그리고 CourseResponse의 `types`/`companion` 필드

- [x] **Step 1: Write the failing test**

`CoursePreferenceTest`에서 새 생성자가 여행 타입·동행 유형을 보존하고, 기존 생성자는 빈 선호값을 반환하는지 검증한다.

```java
@Test
void preservesSelectedTravelTypesAndCompanion() {
    Course course = new Course("day", null, null, List.of("nature", "rest"), "couple");

    assertThat(course.getTravelTypes()).containsExactly("nature", "rest");
    assertThat(course.getCompanion()).isEqualTo("couple");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `bash gradlew test --tests com.mirigangneung.course.domain.CoursePreferenceTest`
Expected: FAIL because the preference-aware constructor and getters do not exist.

- [x] **Step 3: Write minimal implementation**

`Course`에 `travelTypes` 문자열 컬럼과 `companion` 컬럼을 추가한다. 타입은 최대 2개라는 현재 요청 계약에 맞춰 쉼표로 직렬화하고, getter에서 불변 리스트로 복원한다. 기존 생성자는 빈 선호값을 사용하는 새 생성자를 호출한다. `CourseService.create()`는 새 생성자에 요청값을 전달하고, `CourseResponse.from()`은 코스의 선호값을 응답한다. 기존 CourseResponse 호환 생성자는 빈 타입/동행값을 사용한다.

- [x] **Step 4: Run test to verify it passes**

Run: `bash gradlew test --tests com.mirigangneung.course.domain.CoursePreferenceTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/mirigangneung/course/domain/Course.java src/main/java/com/mirigangneung/course/service/CourseService.java src/main/java/com/mirigangneung/course/dto/CourseResponse.java src/test/java/com/mirigangneung/course/domain/CoursePreferenceTest.java
git commit -m "feat: persist course recommendation preferences"
```

### Task 2: 설명 가능한 주변 장소 점수 계산

**Files:**
- Create: `src/main/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorer.java`
- Test: `src/test/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorerTest.java`

**Interfaces:**
- Consumes: `KakaoLocalClient.NearbyPlace`, normalized nearby category, computed distance/radius, `Course.getTravelTypes()`, `Course.getCompanion()`
- Produces: `Recommendation score(NearbyPlace, String, int, int, List<String>, String)` returning `score()` and ordered `reasons()`

- [x] **Step 1: Write the failing test**

점수 범위, 가까운 장소의 거리 점수 우위, 여행 타입·동행 유형 키워드 반영, 두 타입 선택 시 평균 처리, 미일치 fallback 이유를 검증한다.

```java
@Test
void givesAHighExplainableScoreToANearbyCafeForRestAndCouple() {
    var place = nearby("안목 바다 카페", "음식점 > 카페", "강릉시 안목");

    var result = scorer.score(place, "cafe", 150, 2_000,
            List.of("rest"), "couple");

    assertThat(result.score()).isBetween(0, 100);
    assertThat(result.reasons()).anyMatch(reason -> reason.contains("휴식"));
    assertThat(result.reasons()).anyMatch(reason -> reason.contains("커플"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `bash gradlew test --tests com.mirigangneung.course.recommendation.NearbyPlaceRecommendationScorerTest`
Expected: FAIL because the scorer does not exist.

- [x] **Step 3: Write minimal implementation**

여행 타입 5개와 동행 유형 4개의 카테고리·키워드 규칙을 클래스 내부의 불변 profile map으로 관리한다. 점수는 거리 40점, 여행 타입 30점, 동행 유형 20점, 정보 완성도 10점으로 구성한다. 두 여행 타입은 각각 계산한 값을 평균해 30점 안에서 반영하며, 정규화한 장소명·카테고리명만 키워드 비교에 사용한다. 점수와 이유는 고정된 순서로 생성하고, 알 수 없는 선호값이나 키워드 미일치는 거리·정보 점수만 남긴다.

- [x] **Step 4: Run test to verify it passes**

Run: `bash gradlew test --tests com.mirigangneung.course.recommendation.NearbyPlaceRecommendationScorerTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorer.java src/test/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorerTest.java
git commit -m "feat: score nearby places from course preferences"
```

### Task 3: 주변 장소 추천 API와 추천순/거리순 정렬

**Files:**
- Modify: `src/main/java/com/mirigangneung/course/controller/CourseController.java`
- Modify: `src/main/java/com/mirigangneung/course/service/CoursePlaceService.java`
- Modify: `src/main/java/com/mirigangneung/course/dto/NearbyPlaceResponse.java`
- Modify: `src/main/java/com/mirigangneung/course/dto/NearbyPlacesResponse.java` only if response metadata is required
- Test: `src/test/java/com/mirigangneung/course/service/CoursePlaceServiceTest.java`

**Interfaces:**
- Consumes: `GET /api/v1/courses/{id}/nearby-places?category=...&stopId=...&sort=recommended|distance`
- Produces: nearby item fields `recommendationScore` and `recommendationReasons`; default `sort=recommended`; invalid sort returns 400.

- [x] **Step 1: Write the failing test**

선호값이 있는 코스에서 먼 장소라도 키워드가 맞으면 추천순에서 먼저 나오고 점수/이유가 반환되는지, `distance`에서는 기존 가까운 순서가 유지되는지 검증한다.

```java
@Test
void recommendationSortUsesPreferencesButDistanceSortKeepsLegacyOrder() {
    Course course = new Course("day", null, null, List.of("rest"), "couple");
    when(courses.findById(courseId)).thenReturn(Optional.of(course));
    when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(tourismStop));
    when(localClient.searchByCategory(any(Double.class), any(Double.class), eq("CE7"), eq(2_000), eq(0), eq(15)))
            .thenReturn(List.of(
                    nearby("near", "일반 카페", 37.0005, 128.0005),
                    nearby("match", "안목 바다 카페", 37.0100, 128.0100)
            ));

    assertThat(service.nearby(courseId.toString(), "cafe", null, "recommended").places())
            .extracting(NearbyPlaceResponse::externalPlaceId)
            .containsExactly("match", "near");
    assertThat(service.nearby(courseId.toString(), "cafe", null, "distance").places())
            .extracting(NearbyPlaceResponse::externalPlaceId)
            .containsExactly("near", "match");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `bash gradlew test --tests com.mirigangneung.course.service.CoursePlaceServiceTest`
Expected: FAIL because the service always sorts by distance and the response has no recommendation fields.

- [x] **Step 3: Write minimal implementation**

`CoursePlaceService`에 추천 scorer를 연결하고, 코스 선호값을 이용해 모든 후보에 추천 정보를 계산한다. `recommended`는 점수 내림차순→거리 오름차순→이름순, `distance`는 거리 오름차순→이름순으로 정렬한다. 선호값이 없는 기존 코스는 추천 점수를 null로 두고 거리순으로 fallback한다. Controller의 sort 기본값은 `recommended`로 두고 허용값 외에는 명시적인 400 오류를 반환한다. 기존 3개 인자 service 메서드와 생성자는 호환 오버로드를 유지한다.

- [x] **Step 4: Run test to verify it passes**

Run: `bash gradlew test --tests com.mirigangneung.course.service.CoursePlaceServiceTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/mirigangneung/course/controller/CourseController.java src/main/java/com/mirigangneung/course/service/CoursePlaceService.java src/main/java/com/mirigangneung/course/dto/NearbyPlaceResponse.java src/test/java/com/mirigangneung/course/service/CoursePlaceServiceTest.java
git commit -m "feat: expose nearby place recommendations"
```

### Task 4: 프론트엔드 API 계약과 추천순/거리순 UI

**Files:**
- Modify: `src/types/domain.ts`
- Modify: `src/types/api.ts`
- Modify: `src/lib/courseApi.ts`
- Modify: `src/queries/useCoursePlacesQuery.ts`
- Modify: `src/pages/CourseResultPage.tsx`
- Modify: `src/components/organisms/CourseResult.tsx`
- Modify: `src/components/organisms/NearbyPlaceCard.tsx`
- Test: `src/lib/courseApi.test.ts`
- Test: `src/components/organisms/NearbyPlaceCard.test.tsx`

**Interfaces:**
- Consumes: backend `recommendationScore`, `recommendationReasons`, and `sort` query parameter
- Produces: recommended default tab, distance fallback tab, score/reason display, existing explicit add action unchanged

- [x] **Step 1: Write the failing test**

API 테스트에 거리순 query string과 추천 필드 mapping을 추가하고, 카드 테스트에 추천 점수와 첫 번째 추천 이유가 보이는지 추가한다.

```ts
it('requests distance sorting only when the user selects it', async () => {
  await fetchNearbyPlaces('course-1', 'cafe', undefined, baseUrl, 'distance');
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('sort=distance'));
});
```

- [x] **Step 2: Run test to verify it fails**

Run: `npm test -- --run src/lib/courseApi.test.ts src/components/organisms/NearbyPlaceCard.test.tsx`
Expected: FAIL because the sort parameter and recommendation fields are not mapped or rendered.

- [x] **Step 3: Write minimal implementation**

도메인/API 타입에 추천 필드를 추가하고, `fetchNearbyPlaces`에 호환 가능한 sort 인자를 추가한다. 추천순은 query parameter를 생략해 백엔드 기본값을 사용하고 거리순만 `sort=distance`를 보낸다. React Query key에 sort를 포함해 두 정렬 결과가 섞이지 않게 한다. 장소 추가 패널에 추천순·거리순 segmented control을 추가하고, 카드에는 추천 점수·추천 이유·기존 거리를 함께 표시한다. 카드 선택과 코스 추가 버튼은 기존처럼 사용자가 직접 누를 때만 동작하게 둔다.

- [x] **Step 4: Run test to verify it passes**

Run: `npm test -- --run src/lib/courseApi.test.ts src/components/organisms/NearbyPlaceCard.test.tsx`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/types/domain.ts src/types/api.ts src/lib/courseApi.ts src/queries/useCoursePlacesQuery.ts src/pages/CourseResultPage.tsx src/components/organisms/CourseResult.tsx src/components/organisms/NearbyPlaceCard.tsx src/lib/courseApi.test.ts src/components/organisms/NearbyPlaceCard.test.tsx
git commit -m "feat: add nearby recommendation controls"
```

### Task 5: 계약 문서와 전체 검증

**Files:**
- Modify: `docs/API_CONTRACT.md`
- Modify: `docs/openapi.yaml`
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/WORK_LOG.md`

- [x] **Step 1: Document the API contract**

`nearby-places`의 `sort` query parameter와 `recommendationScore`/`recommendationReasons` 응답 필드를 예시와 함께 기록하고, 선호값이 없는 구 코스의 distance fallback을 명시한다.

- [x] **Step 2: Run backend verification**

Run: `bash gradlew test` in the backend worktree.
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run frontend verification**

Run: `npm test -- --run` and `npm run build` in the frontend worktree with the existing dependency installation made available to the worktree.
Expected: all tests pass and TypeScript/Vite build succeeds.

- [x] **Step 4: Inspect the final diff and status**

Run `git diff --check`, `git status --short --branch`, and review both worktree diffs for secrets, unintended files, and preserved explicit-add behavior.

- [x] **Step 5: Commit documentation**

```bash
git add docs/API_CONTRACT.md docs/openapi.yaml docs/PROJECT_STATUS.md docs/WORK_LOG.md
git commit -m "docs: record issue 13 place recommendation contract"
```
