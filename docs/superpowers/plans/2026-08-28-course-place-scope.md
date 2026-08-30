# Course Place Search Scope Implementation Plan

> **현재 구현 기준 메모 (2026-08-30):** 이 문서는 최초 범위 설계 계획이다. 이후 선호도 기반 구현과 현재 API 계약이 이 문서와 다르면 현재 구현을 기준으로 한다. 현재 백엔드는 `nearby`와 `all` 모두 관광명소(`AT4`)를 지원하고, `scope=all`에서 `keyword`가 비어 있으면 Kakao를 호출하지 않고 빈 결과를 반환한다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코스 결과 화면의 장소 추가 패널을 `주변 추천`과 `강릉 전체` 검색으로 분리하고, 사용자가 카페·음식점·문화시설·관광명소 후보를 지도와 목록에서 직접 확인한 뒤 명시적으로 코스에 추가할 수 있게 한다. `강릉 대표` 탭은 UI 자리만 마련하고 이번 작업에서는 비활성화한다.

**Architecture:** 기존 추천 브랜치의 주변 추천 흐름(선택 관광지 기준 2km, 추천순/거리순)은 유지한다. 백엔드는 Kakao Local API의 카테고리/키워드 검색을 같은 코스 장소 엔드포인트의 `scope`로 구분하고, `nearby`는 기존 거리·추천 메타데이터를 반환하며 `all`은 Gangneung 사각 영역(`rect`)과 Kakao 페이지네이션을 사용해 후보를 반환한다. 프론트는 scope를 쿼리 키와 UI 상태에 포함하고, `강릉 전체`의 추가 후보를 지도에 중립 마커로 표시하며 카드의 명시적 추가 버튼만 코스를 변경한다.

**Tech Stack:** Spring Boot 4, Java 17, Spring Data JPA, Kakao Local REST API, React, TypeScript, TanStack Query, Kakao Maps SDK, Vitest/React Testing Library.

**Spec:** `docs/superpowers/specs/2026-08-28-course-place-scope-design.md`

**Worktrees:**

- Backend: `/Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-backend-place-scope` (`feat/course-place-scope`)
- Frontend: `/Users/seob/Desktop/MiriGangNeung/.worktrees/mirigangneung-frontend-place-scope` (`feat/course-place-scope`)

## Global Constraints

- 사용자 노출 및 백엔드 지원 카테고리는 `cafe`, `restaurant`, `culture`, `attraction` 네 가지다. 관광명소는 Kakao Local `AT4`로 조회한다.
- `nearby`는 기존 선택 관광지 최대 3개 및 `all` 기준 선택, 반경 2km, `recommended`/`distance` 정렬을 보존한다.
- `all`은 Kakao Local API의 `rect` 검색을 사용한다. 빈 키워드는 외부 API를 호출하지 않고 빈 결과를 반환하며, 입력 키워드는 Gangneung 영역 안의 키워드 검색으로 처리한다.
- `all` 결과에는 거리/가장 가까운 관광지 텍스트를 표시하지 않는다. `nearby` 결과에서는 기존 표시를 유지한다.
- 추천 후보를 자동으로 코스에 넣지 않는다. 카드의 추가 동작 또는 지도 후보 선택 후 추가 동작만 코스를 변경한다.
- `강릉 대표`는 비활성화 상태로만 두고, 리뷰/별점 조사와 하드코딩은 이번 범위에 포함하지 않는다.
- Kakao 응답의 place ID를 기준으로 중복을 제거하고, 현재 코스의 관광지·이미 추가된 장소와 이름/ID가 겹치는 후보는 제외한다.
- Kakao API 키와 사각 영역은 환경변수/설정으로 주입하며 키를 소스에 저장하지 않는다.
- 각 단계는 먼저 실패하는 테스트를 추가하고 구현한 뒤 해당 테스트와 관련 회귀 테스트를 실행한다.

---

## 1. Baseline and plan verification

- [x] 두 워크트리의 현재 브랜치와 변경 상태를 확인하고, 기존 변경을 덮어쓰지 않는다.
- [x] 백엔드 `./gradlew test`와 프론트 기존 테스트/빌드의 기준 결과를 기록한다. 기존 실패가 있으면 구현 실패와 분리해 기록한다.
- [x] 이 계획과 설계 문서에 placeholder, trailing whitespace, `git diff --check` 문제가 없는지 확인한다.

## 2. Backend: Kakao rectangular search contract

**Files:**

- `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoLocalClient.java`
- `src/main/java/com/mirigangneung/infrastructure/kakao/HttpKakaoLocalClient.java`
- `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoLocalProperties.java`
- `src/main/resources/application.yml`
- corresponding infrastructure tests

- [x] `KakaoLocalClient`에 기존 반경 검색을 깨뜨리지 않는 페이지 응답 타입을 추가한다. 예: `SearchPage(List<NearbyPlace> places, int page, boolean isEnd)`.
- [x] 카테고리 사각 검색 계약을 `searchByCategoryInRect(String rect, String categoryCode, int page, int size)`로 추가한다.
- [x] 키워드 사각 검색 계약을 `searchByKeywordInRect(String query, String rect, String categoryCode, int page, int size)`로 추가한다.
- [x] HTTP 구현은 `/v2/local/search/category.json` 또는 `/v2/local/search/keyword.json`에 `rect`, `page`, `size`, `sort=accuracy`를 전송하고 `meta.is_end`를 보존한다. rect 검색은 기준 x/y가 없으므로 distance 정렬을 사용하지 않는다.
- [x] Kakao 문서의 사각형 순서인 `leftX,leftY,rightX,rightY`를 설정 문자열로 명시하고, 기본 Gangneung 영역을 `128.70,37.95,129.05,37.65`로 둔다. 배포/로컬은 `KAKAO_LOCAL_ALL_SEARCH_RECT`로 덮어쓸 수 있게 한다.
- [x] 응답 문서의 `distance`가 없을 수 있으므로 `NearbyPlace.distanceMeters`는 nullable 계약으로 처리하고 기존 nearby 거리 계산에는 영향을 주지 않는다.
- [x] 잘못된 페이지·크기·빈 키워드가 HTTP 요청으로 그대로 새지 않도록 최소/최대 범위를 정한다. Kakao page는 1-based이므로 내부 0-based page를 요청 시 +1로 변환한다.
- [x] 요청 URL, 헤더, `meta.is_end`, nullable distance를 검증하는 단위 테스트를 먼저 작성하고 통과시킨다.

## 3. Backend: scope-aware service, controller, DTO

**Files:**

- `src/main/java/com/mirigangneung/course/service/CoursePlaceService.java`
- `src/main/java/com/mirigangneung/course/controller/CourseController.java`
- `src/main/java/com/mirigangneung/course/dto/NearbyPlaceResponse.java`
- `src/main/java/com/mirigangneung/course/dto/NearbyPlacesResponse.java`
- `src/main/java/com/mirigangneung/infrastructure/kakao/KakaoLocalProperties.java`
- `src/main/resources/application.yml`
- service/controller tests

- [x] 요청 파라미터를 `scope=nearby|all`, `category`, `stopId`, `sort`, `keyword`, `page`, `size`로 정규화한다. 생략된 scope는 하위 호환을 위해 `nearby`로 간주하고, `all` scope의 category를 `cafe|restaurant|culture|attraction`으로 제한한다.
- [x] `nearby`는 현재 구현의 stop 기준, 2km, 최대 페이지 조회, 이름 중복 제거, 추천 점수/거리순 정렬을 그대로 사용한다.
- [x] `all`은 설정된 rect와 선택 카테고리 코드(`CE7`, `FD6`, `CT1`, `AT4`)를 사용한다. 현재 구현은 keyword가 공백이면 외부 API를 호출하지 않고 빈 결과를 반환하며, keyword가 공백이 아니면 Kakao 키워드 사각 검색을 호출한다. 카테고리 사각 검색 Client 계약은 별도로 유지되지만 현재 `all` 서비스 흐름에서는 빈 키워드 전체 목록 조회에 사용하지 않는다.
- [x] `all`은 요청 page/size만큼만 조회하고 `isEnd`를 응답한다. 응답 후보의 `distanceMeters`, `nearestStopId`, `nearestStopName`, 추천 점수와 추천 이유는 null/빈 값으로 반환한다.
- [x] `all` 결과는 Kakao external place ID 기준으로 dedupe하고, 현재 코스 내 장소와 이름/ID가 충돌하는 후보를 제외한다.
- [x] 기존 코스 조회·장소 추가 API의 응답 형식을 깨뜨리지 않도록 변경된 nearby 응답 필드에 필요한 nullable/기본값 호환 처리를 둔다.
- [x] 엔드포인트를 다음 형태로 확장한다: `GET /api/v1/courses/{courseId}/nearby-places?scope=...&category=...&stopId=...&sort=...&keyword=...&page=0&size=15`.
- [x] 잘못된 scope/category/sort, 음수 page, 과도한 size, all에서의 불필요한 stopId를 검증하는 테스트를 추가한다.
- [x] nearby/all 각각의 Kakao 호출 선택, 응답 메타데이터, 후보 제외, nullable 필드를 서비스 테스트로 검증한다.

## 4. Frontend: API types, pagination, query state

**Files:** (frontend worktree)

- `src/types/domain.ts`
- `src/types/api.ts`
- `src/lib/courseApi.ts`
- `src/queries/useCoursePlacesQuery.ts`
- related tests

- [x] 도메인 타입에 `NearbyPlaceScope = 'nearby' | 'all'`을 추가하고 사용자 노출 category를 `cafe | restaurant | culture | attraction`으로 확장한다. distance/nearest/recommendation fields는 all 응답을 수용하도록 nullable/optional로 맞춘다.
- [x] `BackendNearbyPlacesResponse`를 `scope`, `page`, `size`, `isEnd`, `places`를 포함한 페이지 응답으로 확장한다.
- [x] API 함수는 scope, category, stopId, sort, keyword, page, size를 URLSearchParams로 생성하고, `nearby`/`all`의 불필요한 파라미터를 보내지 않는다.
- [x] `useCoursePlacesQuery`는 scope/category/stop/sort/keyword를 query key에 포함하고 page를 누적한다. 기존 호출부가 쓰는 `data`는 평탄화된 장소 배열로 유지하며 `hasNextPage`, `fetchNextPage`, `isFetchingNextPage`를 노출한다.
- [x] mode/category/기준 관광지/정렬/키워드가 바뀌면 이전 페이지 누적 결과가 섞이지 않도록 query key와 상태 초기화를 검증한다.
- [x] exact query string, page mapping, all 응답의 nullable distance, pagination flattening을 테스트한다.

## 5. Frontend: place-add panel modes and cards

**Files:**

- `src/pages/CourseResultPage.tsx`
- `src/components/organisms/CourseResult.tsx`
- `src/components/organisms/NearbyPlaceCard.tsx`
- relevant component tests

- [x] 상단 mode 버튼을 `주변 추천`, `강릉 전체`, `강릉 대표` 순서로 만들고 `강릉 대표`는 disabled/준비 중 상태로 둔다.
- [x] category 버튼을 `카페`, `음식점`, `문화시설`, `관광명소`로 표시한다.
- [x] nearby mode에는 기준 관광지(`전체` + 최대 3개)와 추천순/거리순을 표시한다.
- [x] all mode에는 기준 관광지와 거리순 UI를 숨기고 검색어 입력 및 `더 불러오기`/다음 페이지 UI를 표시한다. 빈 검색어는 외부 API를 호출하지 않고 빈 결과로 처리한다.
- [x] all mode 카드에는 거리, 기준 관광지, 추천 점수/추천 이유를 표시하지 않는다. Kakao 장소 URL의 리뷰 버튼과 명시적 추가 동작은 유지한다.
- [x] 장소를 추가하면 현재 모드나 검색 결과가 자동으로 바뀌지 않고 기존 코스 순서/카드 동작을 보존한다.
- [x] 모드 전환 시 기준 관광지·정렬·검색어를 명확히 초기화하고, 비활성 대표 탭은 클릭해도 네트워크 요청을 발생시키지 않는다.
- [ ] 네 카테고리 렌더링, all mode의 숨김 필드, 대표 disabled, 페이지 추가, 명시적 추가 동작을 컴포넌트 테스트로 검증한다. 현재 저장소 테스트 환경은 static markup 기반이라 API/빌드 검증으로 대체했다.

## 6. Frontend: map candidate markers

**Files:**

- `src/components/organisms/CourseMap.tsx`
- `src/components/organisms/CourseResult.tsx`
- `src/pages/CourseResultPage.tsx`
- map tests

- [x] `CourseMap`에 현재 검색 결과와 후보 선택 콜백을 전달할 수 있는 props를 추가한다.
- [x] all mode에서 로드된 장소를 번호 없는 중립 마커로 표시하고, 코스의 번호 마커·경로와 시각적으로 구분한다.
- [x] 후보 마커 클릭은 카드 선택/미리보기만 수행하고 자동으로 코스에 추가하지 않는다. 카드의 추가 버튼과 동일한 명시적 흐름을 사용한다.
- [x] 결과 페이지/페이지네이션 업데이트로 후보 마커를 갱신해도 기존 코스 경로와 지도 viewport가 불필요하게 초기화되지 않도록 후보 마커 effect를 코스 마커 effect와 분리한다.
- [x] 후보 목록이 비거나 mode가 nearby/대표 disabled이면 후보 마커를 정리하고, 코스 마커/경로 cleanup은 그대로 동작하게 한다.
- [ ] candidate marker 갱신, 클릭 콜백, 기존 경로 유지에 대한 테스트를 추가한다. Kakao Maps SDK 의존 영역은 이번 자동 테스트에서 제외하고 build 및 기존 지도 helper 회귀 테스트로 검증했다.

## 7. Contract docs and regression checks

**Files:**

- `docs/API_CONTRACT.md`
- `docs/openapi.yaml`
- relevant README/config comments if needed

- [x] scope별 query parameter, response page metadata, all mode에서 nullable인 필드, category code를 API 문서에 반영한다.
- [x] Kakao rect 환경변수와 로컬 실행 시 설정 예시를 문서화하되 실제 키는 기록하지 않는다.
- [x] 백엔드 `./gradlew test`를 실행한다.
- [x] 프론트 lint, typecheck/build, Vitest를 실행한다.
- [x] 두 워크트리에서 `git diff --check`와 변경 파일 검토를 수행하고, 기존 브랜치의 unrelated 변경이 섞이지 않았는지 확인한다.

## Commit checkpoints

- [x] Backend commit: `feat: add scoped Kakao course-place search`
- [x] Frontend commit: `feat: add Gangneung-wide course-place search`
- [ ] Docs/test follow-up commit only if it is meaningfully separate; otherwise include with the corresponding repository commit.

## Completion criteria

- [x] UI에 네 카테고리와 세 mode가 의도한 상태로 보인다.
- [x] nearby 기존 추천 흐름이 회귀하지 않는다.
- [x] all mode에서 Kakao rect/keyword 페이지 결과가 목록과 지도에 보이고, 거리 텍스트 없이 리뷰 URL/추가 동작이 제공된다. `attraction` 카테고리도 동일한 흐름을 사용한다.
- [x] 대표 탭은 구현/호출되지 않는다.
- [x] 양쪽 저장소 테스트와 빌드가 통과했다는 실제 출력이 확인된다.
