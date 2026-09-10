package com.mirigangneung.place.service;

import java.util.Map;

/** Backend-owned short copy for the curated places exposed to the frontend. */
public final class PlaceShortDescriptionCatalog {
    private static final String DEFAULT = "강릉의 아름다운 여행지를 만나보세요.";

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            entry("국립대관령치유의숲", "울창한 금강소나무 숲과 치유 숲길이 어우러진 산림치유 공간입니다. 솔향을 따라 걸으며 대관령의 맑은 숲을 천천히 느껴보세요."),
            entry("강문해변", "푸른 동해와 아담한 강문항이 나란히 어우러진 해변입니다. 포토존과 해변 산책을 즐기며 강릉 바다의 여유를 느껴보세요."),
            entry("강릉 경포대", "경포호 북쪽 언덕에 자리한 관동팔경의 누각이자 보물입니다. 고즈넉한 누각에서 경포호와 주변 풍경의 운치를 느껴보세요."),
            entry("경포해수욕장", "넓은 백사장과 울창한 송림이 어우러진 강릉의 대표 해변입니다. 시원한 동해와 솔숲을 함께 즐기며 여유롭게 쉬어가세요."),
            entry("안목해변", "푸른 바다와 강릉 커피거리가 맞닿아 있는 해변입니다. 바다를 바라보며 산책하고 커피 한 잔의 여유도 함께 즐겨보세요."),
            entry("향호해변", "주문진해변과 이어진 백사장과 소나무숲이 인상적인 해변입니다. BTS 앨범 재킷 촬영지로 알려진 버스정류장 포토존도 만나보세요."),
            entry("정동진", "푸른 동해와 해안 풍경으로 유명한 강릉의 대표 관광지입니다. 탁 트인 바다와 정동진 특유의 해안 풍경을 천천히 즐겨보세요."),
            entry("허난설헌 생가터", "조선 시대 문인 허난설헌의 삶과 문학을 기리는 고즈넉한 공간입니다. 솔숲과 생가터를 둘러보며 그녀의 문학 세계를 만나보세요."),
            entry("정동심곡 바다부채길", "정동항과 심곡항 사이 해안을 따라 이어지는 탐방로입니다. 푸른 동해와 기암괴석, 해안 절벽이 만든 웅장한 풍경을 만나보세요."),
            entry("구룡폭포(소금강)", "소금강의 울창한 숲과 계곡을 따라 폭포가 이어지는 명소입니다. 기암괴석 사이로 흐르는 시원한 계곡과 자연의 절경을 감상해보세요."),
            entry("강릉솔향수목원", "금강소나무 숲을 중심으로 다양한 식물과 테마원이 조성된 수목원입니다. 솔향과 물소리를 따라 걸으며 숲속의 여유를 즐겨보세요."),
            entry("안반데기", "대관령 고산지대에 넓은 고랭지 밭이 펼쳐진 산촌 마을입니다. 산 능선을 따라 이어지는 독특하고 탁 트인 풍경을 바라보세요."),
            entry("정동진해변", "정동진역과 모래시계공원 인근에 펼쳐진 동해의 해변입니다. 탁 트인 바다와 함께 정동진의 아름다운 해돋이 풍경을 만나보세요."),
            entry("강릉항", "안목해변과 가까이 자리한 강릉의 항구입니다. 항구와 바다가 맞닿은 풍경을 바라보며 강릉 해안의 정취를 느껴보세요."),
            entry("경포가시연습지", "가시연을 비롯한 다양한 생물이 살아가는 경포호 주변의 생태습지입니다. 탐방로를 걸으며 습지의 자연과 생태를 가까이에서 살펴보세요."),
            entry("강릉 임당동성당", "오랜 시간 강릉 도심을 지켜온 역사적인 성당입니다. 뾰족한 종탑과 아치형 창호가 만드는 고풍스러운 분위기를 느껴보세요."),
            entry("솔바람다리", "안목과 남항진을 이어주는 바닷가의 보행 다리입니다. 바다와 강이 만나는 풍경을 바라보며 시원한 바닷바람을 느껴보세요."),
            entry("주문진 등대", "오랜 시간 주문진 앞바다를 밝혀온 역사 깊은 등대입니다. 언덕 위에서 등대와 주문진항, 푸른 동해가 어우러진 풍경을 감상해보세요."),
            entry("송정해변", "넓은 백사장과 해변을 따라 이어진 울창한 송림이 매력적인 곳입니다. 소나무 사이로 바다를 바라보며 한적한 산책을 즐겨보세요."),
            entry("영진해변", "조용한 해변과 영진항의 어촌 풍경이 어우러진 곳입니다. 한적한 바닷가를 따라 걸으며 여유로운 강릉의 해안 풍경을 즐겨보세요."),
            entry("정동진시간박물관", "모래시계공원에 자리한 기차 형태의 박물관으로 시간을 주제로 다양한 전시를 선보입니다. 여러 시대의 시계와 시간 이야기를 따라 특별한 관람을 즐겨보세요."),
            entry("대관령박물관", "대관령의 자연 속에서 강릉과 영동 지역의 다양한 유물을 만날 수 있는 박물관입니다. 전시를 둘러보며 지역의 역사와 문화 이야기를 만나보세요."),
            entry("강릉 월화거리", "강릉 도심의 옛 철길을 공원과 산책길로 되살린 문화거리입니다. 월화 이야기를 따라 걸으며 시장과 도심의 다양한 풍경을 함께 즐겨보세요.")
    );

    private static final Map<String, String> ALIASES = Map.of(
            "임당동 성당", DESCRIPTIONS.get("강릉 임당동성당"),
            "강릉 솔향수목원", DESCRIPTIONS.get("강릉솔향수목원")
    );

    private PlaceShortDescriptionCatalog() {
    }

    public static String get(String placeName) {
        if (placeName == null) {
            return null;
        }
        String name = placeName.trim();
        return DESCRIPTIONS.containsKey(name) ? DESCRIPTIONS.get(name) : ALIASES.get(name);
    }

    public static String fallback() {
        return DEFAULT;
    }

    static int size() {
        return DESCRIPTIONS.size();
    }

    private static Map.Entry<String, String> entry(String name, String description) {
        return Map.entry(name, description);
    }
}
