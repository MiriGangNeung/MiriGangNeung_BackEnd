# ADR: 코스 주변 장소와 Kakao 스냅샷 저장

- 상태: Accepted
- 날짜: 2026-08-25
- 범위: 코스 결과 화면의 카페·음식점 조회, 추가, 삭제, 순서 변경

## 결정

Kakao Local REST API는 백엔드에서만 호출한다. 프론트엔드는 백엔드의 `nearby-places` 응답을 사용하고, Kakao REST 키를 브라우저에 노출하지 않는다. 음식점은 `FD6`, 카페는 `CE7` 카테고리로 조회한다.

사용자가 선택한 최대 3개 관광지 정거장을 모두 검색 기준으로 사용한다. 같은 Kakao 외부 장소가 여러 기준점에서 발견되면 외부 장소 ID로 합치고 최소 거리와 가장 가까운 관광지 이름을 반환한다.

사용자가 장소를 코스에 추가하는 순간 이름·분류·주소·전화번호·URL·좌표를 `CourseExternalPlace`에 snapshot으로 저장하고 `CourseStop`과 연결한다. 외부 장소는 전역 KTO `Place` 카탈로그에 넣지 않는다.

장소 추가·삭제·순서 변경은 코스 응답을 다시 반환하며, 인접 정거장별 Kakao 도보 경로를 재계산한다. 경로 API가 없거나 실패해도 코스 장소 CRUD는 성공시키고 `routeStatus=UNAVAILABLE`로 표시한다.

## 이유

- 브라우저 직접 호출은 REST 키 관리와 CORS·쿼터 제어가 어렵다.
- 코스에 추가한 외부 장소를 매번 Kakao에서 재조회하면 공유·새로고침 때 데이터가 사라지거나 이름/주소가 바뀔 수 있다.
- 외부 장소를 전역 관광지로 승격하면 KTO 사진·저작권·배경 합성 대상 정책과 섞인다.
- 세 관광지 주변을 모두 조회해야 사용자가 어느 관광지로 이동할지 결정하기 전에도 충분한 선택지를 제공할 수 있다.
- 경로 계산은 부가 정보이므로 경로 제공자 장애가 코스 편집 자체를 막아서는 안 된다.

## 결과

- `KAKAO_API_KEY`는 백엔드 환경변수로만 설정한다.
- `CourseExternalPlace`는 코스 정거장과 함께 삭제되며, 코스 결과 새로고침·공유 시에도 유지된다.
- 카페·음식점 추가 후에는 DB가 화면의 source of truth이고 Redis를 장소 스냅샷 저장소로 사용하지 않는다.
- Kakao Local·도보 API 계약은 [Kakao Local 개발 가이드](https://developers.kakao.com/docs/ko/local/dev-guide)와 [Kakao 지도 REST API](https://developers.kakao.com/docs/ko/kakaomap/rest-api)를 따른다.
