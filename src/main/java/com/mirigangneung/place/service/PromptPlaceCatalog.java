package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.TourApiClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Places that have a prompt in the Notion pose guide are the only places
 * exposed by the initial place-selection API.
 */
public final class PromptPlaceCatalog {
    private static final Set<String> PROMPT_PLACE_NAMES = Set.of(
            "강남축구공원", "강릉 3·1운동 기념공원", "강릉 경포대", "강릉 솔향수목원",
            "강릉 오죽헌·시립박물관", "강릉 해운정", "강릉생태체험박물관 자연아놀자",
            "강릉시강남축구공원", "강릉시립미술관 교동", "강릉아트센터", "강릉자수박물관",
            "강릉커피거리", "강릉항", "강문솟대다리", "경포가시연습지", "경포저수지",
            "경포해수욕장", "김동명문학관", "노암터널", "노추산 모정탑길",
            "라카이 샌드파인 수영장", "매월당 김시습 기념관", "모래시계공원",
            "사근진해변(사근진해수욕장)", "소돌아들바위공원", "순긋해변", "안목해변",
            "안반데기", "안인해변", "영진해변", "오대산 소금강계곡", "정동심곡 바다부채길",
            "정동진시간박물관", "정동진해변", "주문진 등대", "처음처럼&새로 브랜드 체험관",
            "통일공원(강릉)", "향호해변", "헌화로", "환희컵박물관",
            "해파랑길 39코스", "해파랑길 40코스", "해파랑길 41코스");

    private static final Set<String> API_NAME_ALIASES = Set.of(
            "[해파랑길] 39코스(바우길 05구간)",
            "[해파랑길] 40코스",
            "[해파랑길] 41코스");

    private PromptPlaceCatalog() {
    }

    public static boolean contains(TourApiClient.TourPlace place) {
        return place != null && containsName(place.name());
    }

    public static boolean containsName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return PROMPT_PLACE_NAMES.contains(name.trim()) || API_NAME_ALIASES.contains(name.trim());
    }

    public static Set<String> apiNames() {
        Set<String> names = new HashSet<>(PROMPT_PLACE_NAMES);
        names.addAll(API_NAME_ALIASES);
        return Set.copyOf(names);
    }
}
