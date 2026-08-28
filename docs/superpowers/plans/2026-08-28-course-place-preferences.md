# Course Place Preferences Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 장소 추가 패널을 카테고리 우선의 2단 구조로 정리하고, 여행 타입의 세부 선호를 코스에 저장하여 주변 장소 추천 점수에 반영한다. 강릉 전체 검색은 사용자가 검색어를 제출한 경우에만 선택 카테고리의 카카오 키워드 검색을 호출한다.

**Architecture:** 코스 생성 시 넓은 여행 타입과 세부 선호를 함께 저장한다. 장소 추가 패널은 카페·음식점·문화시설·관광명소 중 하나를 먼저 선택하고, 선택한 카테고리에 대해 주변 추천·강릉 전체 검색·강릉 대표를 표시한다. 주변 추천만 코스 선호 점수를 사용하며, 강릉 전체 검색은 검색어 제출 전에는 외부 API를 호출하지 않는다. 강릉 대표는 이번 범위에서 준비 중 상태로 표시한다.

**Tech Stack:** Spring Boot, JPA, MySQL, Redis, Kakao Local API, React, TypeScript, Zustand, React Query, Vitest.

**Spec:** docs/superpowers/specs/2026-08-28-course-place-preferences-design.md

**Worktrees:**

- Backend: /Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-backend-course-preferences
- Frontend: /Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-frontend-course-preferences
- Backend branch: feat/course-place-preferences
- Frontend branch: feat/course-place-preferences

## Global Constraints

- 기존 작업 트리와 루트 브랜치는 수정하지 않는다.
- 구현 전에 각 변경 단위의 실패 테스트를 먼저 추가하고, 실패를 확인한 뒤 운영 코드를 작성한다.
- 기존 코스 생성 요청과 기존 저장 코스는 detailTypes가 없어도 동작해야 한다.
- active 여행 타입은 새 UI와 새 추천 정책에서 제거하되, 기존 데이터 해석은 깨뜨리지 않는다.
- 카카오 API를 호출하는 범위는 선택된 카테고리와 명시적인 검색 조건으로 제한한다.
- 강릉 대표의 랭킹·리뷰·별점 데이터와 새 외부 API는 이번 구현 범위에 포함하지 않는다.
- 모든 단계마다 관련 테스트를 실행하고, 마지막에 전체 테스트·빌드·diff 검사를 실행한다.

## Task 1: Baseline and implementation documents

- [ ] 두 worktree의 브랜치와 working tree 상태를 확인한다.
- [ ] 백엔드 테스트와 프론트엔드 테스트·빌드를 기준 상태에서 실행한다.
- [ ] 기준 테스트 실패가 있으면 원인과 범위를 기록하고, 이번 변경으로 발생한 실패와 구분한다.
- [ ] 설계 문서와 이 실행 계획을 백엔드 worktree에 커밋한다.

Files:

- docs/superpowers/specs/2026-08-28-course-place-preferences-design.md
- docs/superpowers/plans/2026-08-28-course-place-preferences.md

Verification:

- Backend: ./gradlew test
- Frontend: npm test -- --run
- Frontend: npm run build

## Task 2: Persist detailed course preferences in the backend

- [ ] CreateCourseRequest에 detailTypes를 추가하고, 입력값이 없거나 비어 있는 기존 요청은 허용한다.
- [ ] Course 엔티티에 detailTypes 저장 필드와 getter를 추가한다.
- [ ] 기존 생성자와 새 생성자가 모두 동작하도록 기존 생성자를 새 생성자에 위임한다.
- [ ] CourseService가 요청의 detailTypes를 엔티티에 전달하도록 수정한다.
- [ ] CourseResponse와 변환 로직에 detailTypes를 포함한다.
- [ ] 허용된 세부 선호 ID와 현재 선택한 broad type의 조합을 검증하고, 알 수 없는 ID는 기존 프로젝트의 400 오류 규칙으로 반환한다.
- [ ] CoursePreferenceTest와 CourseServiceTest에 저장·응답·하위 호환·잘못된 입력 테스트를 추가한다.

Expected detail IDs:

- food:korean, food:chinese, food:japanese, food:western
- rest:coffee, rest:dessert
- culture:art, culture:exhibition, culture:museum
- nature에는 세부 선택을 요구하지 않는다.

Files:

- src/main/java/com/mirigangneung/course/domain/Course.java
- src/main/java/com/mirigangneung/course/dto/CreateCourseRequest.java
- src/main/java/com/mirigangneung/course/dto/CourseResponse.java
- src/main/java/com/mirigangneung/course/service/CourseService.java
- src/test/java/com/mirigangneung/course/CoursePreferenceTest.java
- src/test/java/com/mirigangneung/course/service/CourseServiceTest.java

Verification:

- Run the focused course preference and course service tests after the RED and GREEN phases.

## Task 3: Replace recommendation scoring with detail-aware scoring

- [ ] Scorer 테스트에 세부 선호가 정확히 일치하는 장소가 더 가까운 불일치 장소보다 우선되는 사례를 추가한다.
- [ ] 음식 세부 타입, 휴식 세부 타입, 문화 세부 타입, 자연 타입의 키워드 표를 테스트로 고정한다.
- [ ] 동행 유형 점수는 보조 지표로만 반영하고, 한 장소에서 여러 키워드가 맞아도 동행 점수 상한을 넘지 않도록 테스트한다.
- [ ] 장소명·카테고리명·주소·도로명 주소를 검색 텍스트로 사용하도록 테스트한다.
- [ ] 카페 주변 추천에서 메가MGC커피·메가커피·컴포즈커피·스타벅스·투썸플레이스 등 프랜차이즈 토큰이 있으면 감점하는 테스트를 추가한다.
- [ ] Scorer의 점수 비중을 세부·broad 적합도 60, 거리 20, 동행 10, 데이터 완성도 10으로 변경한다.
- [ ] 세부 일치 60, broad 키워드 일치 40, 카테고리만 일치 10, 부적합 0의 적합도 단계와 프랜차이즈 -8 감점을 구현한다.
- [ ] 선택된 broad type이 둘 이상이어도 점수를 평균내지 않고 장소에 가장 잘 맞는 프로필의 점수를 사용한다.
- [ ] 기존 active 데이터가 들어오면 기존 broad 규칙으로 안전하게 처리한다.

Files:

- src/main/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorer.java
- src/test/java/com/mirigangneung/course/recommendation/NearbyPlaceRecommendationScorerTest.java

Verification:

- Run NearbyPlaceRecommendationScorerTest and the full backend test suite.

## Task 4: Update nearby and all-search backend behavior

- [ ] CoursePlaceService가 course의 detailTypes를 scorer에 전달하도록 수정한다.
- [ ] 전체 검색 허용 카테고리에 attraction을 추가하고, 카테고리별 Kakao code 매핑을 유지한다.
- [ ] all 검색에 keyword가 비어 있으면 Kakao API를 호출하지 않고 빈 결과와 종료 상태를 반환한다.
- [ ] keyword가 있으면 선택 카테고리만 대상으로 Kakao keyword 검색을 호출하고, 기존 장소 중복 제거 규칙을 유지한다.
- [ ] nearby의 stopId, 추천순·거리순, 2km 범위, 페이지네이션 동작은 유지한다.
- [ ] representative 요청은 외부 API를 호출하지 않고 준비 중 상태로 처리한다.
- [ ] CoursePlaceServiceTest에 attraction, blank all search no-call, submitted keyword search, detailTypes 전달, representative 경로 테스트를 추가한다.
- [ ] API 계약 문서와 프로젝트 상태 문서에서 실제 지원 범위와 검색 전제 조건을 갱신한다.

Files:

- src/main/java/com/mirigangneung/course/service/CoursePlaceService.java
- src/main/java/com/mirigangneung/course/controller/CourseController.java
- src/test/java/com/mirigangneung/course/service/CoursePlaceServiceTest.java
- docs/API_CONTRACT.md
- docs/PROJECT_STATUS.md

Verification:

- Run CoursePlaceServiceTest, controller tests if present, and the full backend test suite.

## Task 5: Add detailed preference state and course creation payload to the frontend

- [ ] 여행 타입 카탈로그에 확정된 세부 선호를 추가한다.
- [ ] active를 새 기본 선택값으로 사용하지 않도록 하고, 기존 세션에 active만 남아 있어도 안전하게 읽는다.
- [ ] Zustand store에 detailTypes와 세부 선호 토글 action을 추가하고 세션 저장 대상에 포함한다.
- [ ] 타입과 세부 선호의 연결 관계를 검증하여 broad type을 해제하면 해당 detailTypes를 정리한다.
- [ ] CourseOptions에서 선택된 broad type 아래에 해당 세부 선호 칩을 표시한다.
- [ ] 코스 생성 요청에 detailTypes를 포함하고, 코스 응답에서 detailTypes를 읽도록 타입과 매핑을 수정한다.
- [ ] CourseOptions와 course API 매핑 테스트를 추가한다.

Files:

- src/data/places.ts
- src/store/useAppStore.ts
- src/components/organisms/CourseOptions.tsx
- src/pages/CourseOptionsPage.tsx
- src/types/domain.ts
- src/types/api.ts
- src/lib/courseApi.ts
- src/test files covering the store, CourseOptions, and course API mapping

Verification:

- Run focused frontend tests after RED and GREEN phases, then npm test -- --run.

## Task 6: Refactor the place-add panel into category-first nested options

- [ ] NearbyPlaceCategory에 attraction을 추가한다.
- [ ] UI용 장소 추가 모드를 nearby, all, representative로 정의한다. API scope에는 representative를 전송하지 않는다.
- [ ] 기존에 평면으로 노출되던 3개 scope와 4개 category 버튼을 제거한다.
- [ ] 첫 번째 줄에는 카페·음식점·문화시설·관광명소만 표시한다.
- [ ] 선택된 카테고리 안에 주변 추천·강릉 전체 검색·강릉 대표를 segmented/in-option 형태로 표시한다.
- [ ] 대표 모드는 비활성 또는 준비 중 상태로 보여주고 조회 요청을 만들지 않는다.
- [ ] 주변 추천 모드에서는 선택한 관광지 최대 3개 중 하나를 기준으로 선택할 수 있게 유지하고, 추천순·거리순을 유지한다.
- [ ] 강릉 전체 검색 모드에서는 초기 진입 시 빈 상태와 검색 안내만 보여준다.
- [ ] 검색어 입력값과 제출값을 분리하고, 검색 버튼 또는 Enter 제출 전에는 React Query가 실행되지 않게 한다.
- [ ] 카테고리·모드 변경 시 이전 제출 검색어와 결과를 정리한다.
- [ ] 전체 검색 카드에는 거리와 추천 점수를 표시하지 않고, 주변 추천 카드에는 기존 추천 점수를 표시한다.
- [ ] 장소 추가 패널의 empty, loading, error, no-result 상태를 새 구조에 맞게 정리한다.
- [ ] CourseResult와 CourseResultPage 테스트로 7개 flat option 미노출, category-first, blank no-fetch, submit fetch, representative no-fetch를 검증한다.

Files:

- src/types/domain.ts
- src/queries/useCoursePlacesQuery.ts
- src/pages/CourseResultPage.tsx
- src/components/organisms/CourseResult.tsx
- src/components/organisms/NearbyPlaceCard.tsx
- src/test files covering CourseResult, CourseResultPage, and useCoursePlacesQuery

Verification:

- Run focused component/query tests, npm test -- --run, and npm run build.

## Task 7: Documentation and final verification

- [ ] API 계약에 detailTypes, attraction, blank all search guard, search submission, representative 준비 중 상태를 반영한다.
- [ ] 실행 방법과 로컬 확인 절차가 변경되었으면 README 또는 관련 문서를 갱신한다.
- [ ] 백엔드와 프론트엔드 diff에서 mock 데이터, 임시 로그, 사용하지 않는 import, dead state를 제거한다.
- [ ] 두 worktree에서 git diff --check를 실행한다.
- [ ] 백엔드 ./gradlew test를 실행한다.
- [ ] 프론트엔드 npm test -- --run과 npm run build를 실행한다.
- [ ] 실제 API key가 없는 환경에서도 representative나 blank all search가 외부 API를 호출하지 않는지 테스트 결과로 확인한다.
- [ ] 변경 요약, 검증 결과, 남은 비범위 항목을 작업 보고서에 기록한다.

Files:

- docs/API_CONTRACT.md
- docs/PROJECT_STATUS.md
- README.md when the execution flow changed

## Completion Criteria

- 사용자는 장소 추가 패널에서 카테고리를 먼저 고르고 그 안에서 검색 방식을 고를 수 있다.
- 강릉 전체 검색은 검색어 제출 전 Kakao API를 호출하지 않는다.
- 주변 추천은 선택한 세부 선호와 동행 유형을 반영하고, 거리만으로 부적합한 장소가 상위로 올라가지 않는다.
- 코스 생성과 조회에서 detailTypes가 유지되며, 기존 저장 데이터와 기존 요청은 호환된다.
- 카페·음식점·문화시설·관광명소 네 카테고리가 UI와 백엔드에서 일관되게 동작한다.
- 전체 테스트와 프론트엔드 빌드가 통과한다.
