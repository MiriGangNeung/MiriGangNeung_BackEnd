package com.mirigangneung.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.infrastructure.tourapi.TourApiClient;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * AI 에이전트가 판정한 "인물 합성에 쓸 수 있는" 장소·사진만 노출하기 위한 카탈로그.
 *
 * <p>목록은 손으로 적지 않는다. 에이전트 레포의 {@code assets/places/place_insights.json}
 * (한국관광공사 Type1 이미지 159장을 VLM으로 판정한 결과)에서
 * {@code scripts/export_place_filter.py}가 생성한 것을 {@code /data/viable-places.json}으로
 * 복사해 쓴다. 적용 규칙 세 가지는 그 JSON의 {@code rules}에 함께 적혀 있다 —
 * portraitViability가 low가 아닐 것, 드론 항공샷(high-angle)이 아닐 것,
 * 두 발로 설 표면(standableSurface)이 실제로 있을 것.
 *
 * <p>이전 버전은 다른 문서(팀 포즈 조사 43곳)를 손으로 옮겨 적은 것이라 판정 결과와
 * 15곳만 겹쳤고, "설 자리 없음"으로 판정된 헌화로·소돌아들바위공원이 그대로 노출되고
 * 있었다. 목록을 갱신할 때는 에이전트 레포에서 스크립트를 다시 돌려 JSON을 교체한다.
 */
public final class PromptPlaceCatalog {
    private static final String RESOURCE = "/data/viable-places.json";

    private static final Map<String, Set<String>> USABLE_IMAGE_URLS = load();
    private static final Set<String> PLACE_NAMES = Set.copyOf(USABLE_IMAGE_URLS.keySet());

    private PromptPlaceCatalog() {
    }

    private static Map<String, Set<String>> load() {
        try (InputStream input = PromptPlaceCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(RESOURCE + " 을 클래스패스에서 찾을 수 없습니다.");
            }
            JsonNode root = new ObjectMapper().readTree(input);
            Map<String, Set<String>> byName = new LinkedHashMap<>();
            for (JsonNode place : root.path("places")) {
                String name = place.path("placeName").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                Set<String> urls = new LinkedHashSet<>();
                for (JsonNode url : place.path("usableImageUrls")) {
                    String value = url.asText("").trim();
                    if (!value.isEmpty()) {
                        urls.add(value);
                    }
                }
                byName.put(name, Set.copyOf(urls));
            }
            if (byName.isEmpty()) {
                throw new IllegalStateException(RESOURCE + " 에 노출 가능한 장소가 없습니다.");
            }
            return Map.copyOf(byName);
        } catch (Exception exception) {
            // 조용히 빈 목록으로 넘어가면 필터가 사라져 전체 관광지가 노출된다. 기동을 멈춘다.
            throw new IllegalStateException("장소 노출 카탈로그를 읽을 수 없습니다: " + RESOURCE, exception);
        }
    }

    public static boolean contains(TourApiClient.TourPlace place) {
        return place != null && containsName(place.name());
    }

    public static boolean containsName(String name) {
        return name != null && !name.isBlank() && PLACE_NAMES.contains(name.trim());
    }

    public static Set<String> apiNames() {
        return PLACE_NAMES;
    }

    /**
     * 그 장소에서 인물 합성 배경으로 쓸 수 있다고 판정된 원본 이미지 URL.
     * 목록에 없는 장소면 빈 집합.
     */
    public static Set<String> usableImageUrls(String placeName) {
        if (placeName == null || placeName.isBlank()) {
            return Set.of();
        }
        return USABLE_IMAGE_URLS.getOrDefault(placeName.trim(), Set.of());
    }
}
