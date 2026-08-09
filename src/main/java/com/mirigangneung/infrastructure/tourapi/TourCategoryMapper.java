package com.mirigangneung.infrastructure.tourapi;

import java.util.Locale;
import java.util.Map;

public final class TourCategoryMapper {
    private static final String DEFAULT_CONTENT_TYPE = "12";

    private static final Map<String, String> CATEGORY_TO_CONTENT_TYPE = Map.ofEntries(
            Map.entry("12", "12"),
            Map.entry("nature", "12"),
            Map.entry("beach", "12"),
            Map.entry("자연", "12"),
            Map.entry("해변", "12"),
            Map.entry("관광지", "12"),
            Map.entry("14", "14"),
            Map.entry("culture", "14"),
            Map.entry("문화", "14"),
            Map.entry("15", "15"),
            Map.entry("event", "15"),
            Map.entry("festival", "15"),
            Map.entry("행사", "15"),
            Map.entry("축제", "15"),
            Map.entry("25", "25"),
            Map.entry("course", "25"),
            Map.entry("코스", "25"),
            Map.entry("28", "28"),
            Map.entry("active", "28"),
            Map.entry("activity", "28"),
            Map.entry("레저", "28"),
            Map.entry("32", "32"),
            Map.entry("lodging", "32"),
            Map.entry("숙박", "32"),
            Map.entry("38", "38"),
            Map.entry("shopping", "38"),
            Map.entry("쇼핑", "38"),
            Map.entry("39", "39"),
            Map.entry("food", "39"),
            Map.entry("맛집", "39"),
            Map.entry("음식", "39")
    );

    private static final Map<String, String> CONTENT_TYPE_TO_CATEGORY = Map.of(
            "12", "nature",
            "14", "culture",
            "15", "event",
            "25", "course",
            "28", "active",
            "32", "lodging",
            "38", "shopping",
            "39", "food"
    );

    private TourCategoryMapper() {
    }

    public static String toContentTypeId(String category) {
        if (category == null || category.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CATEGORY_TO_CONTENT_TYPE.getOrDefault(category.trim().toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }

    public static String toInternalCategory(String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return CONTENT_TYPE_TO_CATEGORY.get(DEFAULT_CONTENT_TYPE);
        }
        return CONTENT_TYPE_TO_CATEGORY.getOrDefault(contentTypeId.trim(), CONTENT_TYPE_TO_CATEGORY.get(DEFAULT_CONTENT_TYPE));
    }
}
