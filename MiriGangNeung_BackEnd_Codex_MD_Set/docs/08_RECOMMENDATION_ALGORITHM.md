# 08 Recommendation Algorithm

## 핵심 결정

현재 코스 추천에는 LLM을 사용하지 않는다.

이유:

- 외부 LLM API 비용 절감
- 결과 재현성
- 환각 방지
- 관광공사 실제 데이터만 사용
- 알고리즘의 설명 가능성
- 테스트 가능성

## 전체 파이프라인

```text
Input
  ↓
Candidate Collector
  ↓
Hard Filter
  ↓
Feature Extraction
  ↓
Score
  ↓
Diversity Filter
  ↓
Route/Time Feasibility
  ↓
Final Course
```

## Hard Filter

제거 대상:

- 강릉시 외 지역
- 좌표 없음
- 중복
- 필수 데이터 부족
- 요청 기간에 명백히 운영 불가능한 장소
- 원픽과 중복
- 이동시간 제약을 위반하는 후보

## 추천 점수

정확한 가중치는 초기 구현에서 상수로 두되 한 곳에 모은다.

예:

```text
totalScore =
    wPreference * preferenceScore
  + wOnePickRelated * onePickRelatedScore
  + wPopularity * popularityScore
  + wCrowd * crowdScore
  + wDistance * distanceScore
  + wDiversity * diversityScore
```

가중치는 `RecommendationWeights` 같은 configuration object로 분리한다.

### Preference

현재 `types`와 `companion`에 따라 카테고리/태그 매칭 점수를 계산한다.

### OnePickRelated

원픽의 연관 관광지 데이터가 있으면 높은 점수.

### Popularity

관광공사 방문자 데이터가 제공되는 경우 정규화해서 사용.

### Crowd

혼잡도가 낮은 장소를 선호하도록 설정 가능하되, 사용자 취향/여행 유형에 따라 weight를 조절한다.

### Distance

직전 방문지와 거리가 가까울수록 높은 점수.

## 코스 생성

MVP에서는 다음과 같은 단계적 방법을 권장한다.

1. 원픽을 고정한다.
2. 후보를 score 내림차순으로 정렬한다.
3. 현재 마지막 장소에서 가까운 후보를 우선한다.
4. 같은 카테고리만 연속으로 선택하지 않도록 diversity penalty를 적용한다.
5. 예상 체류시간 + 이동시간이 duration budget을 넘으면 후보를 건너뛴다.
6. 최소/최대 stop 수를 설정한다.
7. 최종 코스에 대해 Kakao route로 실제 이동시간을 확인한다.
8. 시간 초과 시 낮은 점수 stop부터 제거하고 다시 계산한다.

## 중요한 검증

추천 알고리즘이 반환한 placeId는 반드시 실제 Place 저장소에 존재해야 한다.

LLM을 사용하지 않으므로 "존재하지 않는 장소" 환각 문제는 구조적으로 제거한다.

## 설명 가능성

각 stop마다 다음 이유를 만들 수 있다.

```text
- 원픽과 연관성이 높음
- 선택한 여행 유형과 일치
- 이전 장소와 가까움
- 최근 방문자 데이터가 높음
- 혼잡도가 낮음
```

문자열을 LLM으로 생성하지 않고 rule template으로 만든다.

## 향후 LLM

향후 LLM을 넣더라도 `CourseRecommendationEngine` 인터페이스 뒤에 추가한다.

```text
RuleBasedCourseRecommendationEngine
LLMCourseRecommendationEngine (future)
```

현재 구현체는 RuleBased 하나다.
