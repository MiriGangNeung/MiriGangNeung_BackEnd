package com.mirigangneung.course.recommendation;

import com.mirigangneung.place.domain.Place;

import java.util.List;
import java.util.Locale;

/**
 * Provides explainable, conservative preference scores for the P0 course
 * recommendation rules. Unknown values intentionally receive no bonus so the
 * distance-based fallback remains available.
 */
public final class CoursePreferenceScorer {
    private static final int EXACT_TYPE_MATCH = 100;
    private static final int RELATED_TYPE_MATCH = 45;
    private static final int KEYWORD_TYPE_MATCH = 25;
    private static final int COMPANION_CATEGORY_MATCH = 20;
    private static final int COMPANION_KEYWORD_MATCH = 15;

    public int score(Place place, List<String> types, String companion) {
        String category = normalize(place.getCategory());
        String text = normalize(place.getName()) + " " + normalize(place.getDescription());

        int score = 0;
        for (String type : types == null ? List.<String>of() : types) {
            score += typeScore(normalize(type), category, text);
        }
        score += companionScore(normalize(companion), category, text);
        return score;
    }

    private static int typeScore(String type, String category, String text) {
        return switch (type) {
            case "food" -> categoryScore(category, text, "food", 0,
                    new String[]{"맛집", "음식", "카페", "식당"});
            case "rest" -> categoryScore(category, text, "rest", RELATED_TYPE_MATCH,
                    new String[]{"해변", "산책", "카페", "전망", "휴식"});
            case "active" -> categoryScore(category, text, "active", RELATED_TYPE_MATCH,
                    new String[]{"등산", "레저", "드라이브", "체험", "트레킹"});
            case "culture" -> categoryScore(category, text, "culture", 0,
                    new String[]{"문화", "예술", "전시", "박물관", "고택"});
            case "nature" -> categoryScore(category, text, "nature", RELATED_TYPE_MATCH,
                    new String[]{"자연", "해변", "산책", "목장", "숲"});
            default -> 0;
        };
    }

    private static int categoryScore(
            String category,
            String text,
            String exactCategory,
            int relatedScore,
            String[] keywords
    ) {
        if (category.equals(exactCategory)) {
            return EXACT_TYPE_MATCH;
        }
        if (exactCategory.equals("rest") && (category.equals("beach") || category.equals("nature"))) {
            return RELATED_TYPE_MATCH;
        }
        if (exactCategory.equals("active") && (category.equals("nature") || category.equals("beach"))) {
            return relatedScore;
        }
        if (exactCategory.equals("nature") && category.equals("beach")) {
            return relatedScore;
        }
        return containsAny(text, keywords) ? KEYWORD_TYPE_MATCH : 0;
    }

    private static int companionScore(String companion, String category, String text) {
        return switch (companion) {
            case "family" -> companionCategory(category, text, new String[]{"nature", "culture", "beach"},
                    new String[]{"공원", "산책", "목장", "체험"});
            case "couple" -> companionCategory(category, text, new String[]{"beach", "food"},
                    new String[]{"카페", "경포", "정동진", "전망", "사진"});
            case "friends" -> companionCategory(category, text, new String[]{"active", "food", "culture"},
                    new String[]{"체험", "맛집", "사진", "전시"});
            case "solo" -> 0;
            default -> 0;
        };
    }

    private static int companionCategory(String category, String text, String[] categories, String[] keywords) {
        int score = contains(categories, category) ? COMPANION_CATEGORY_MATCH : 0;
        return score + (containsAny(text, keywords) ? COMPANION_KEYWORD_MATCH : 0);
    }

    private static boolean contains(String[] values, String target) {
        for (String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
