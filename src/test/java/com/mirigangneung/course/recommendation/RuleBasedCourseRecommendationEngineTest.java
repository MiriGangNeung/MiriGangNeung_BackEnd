package com.mirigangneung.course.recommendation;

import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCourseRecommendationEngineTest {
    @Test void alwaysIncludesOnePickFirstAndLimitsDayCourse() {
        Place one = new Place("one", "경포대", "강릉시", "관광지", "", 37.0, 128.0, null, "test");
        Place second = new Place("two", "안목해변", "강릉시", "해변", "", 37.01, 128.01, null, "test");
        Place third = new Place("three", "오죽헌", "강릉시", "문화", "", 37.02, 128.02, null, "test");
        var result = new RuleBasedCourseRecommendationEngine().recommend(List.of(one, second, third), one, List.of("active"), "couple", "day");
        assertThat(result).contains(one).startsWith(one).hasSize(3);
    }

    @Test
    void prioritizesPlacesMatchingSelectedTypeBeforeDistance() {
        Place one = place("one", "원픽", "nature", 37.0, 128.0);
        Place nearbyCulture = place("culture", "오죽헌", "culture", 37.001, 128.001);
        Place fartherActive = place("active", "괘방산 등산", "active", 37.02, 128.02);

        var result = new RuleBasedCourseRecommendationEngine().recommend(
                List.of(one, nearbyCulture, fartherActive), one, List.of("active"), "solo", "day");

        assertThat(result).containsExactly(one, fartherActive, nearbyCulture);
    }

    @Test
    void combinesTwoTypesAndCompanionPreference() {
        Place one = place("one", "원픽", "nature", 37.0, 128.0);
        Place culture = place("culture", "전시관", "culture", 37.01, 128.01);
        Place coupleCafe = place("cafe", "경포 카페", "food", 37.02, 128.02);

        var result = new RuleBasedCourseRecommendationEngine().recommend(
                List.of(one, culture, coupleCafe), one, List.of("food", "rest"), "couple", "day");

        assertThat(result).containsExactly(one, coupleCafe, culture);
    }

    @Test
    void fallsBackToDistanceWhenPreferencesDoNotMatch() {
        Place one = place("one", "원픽", "nature", 37.0, 128.0);
        Place nearby = place("nearby", "장소 A", "unknown", 37.001, 128.001);
        Place far = place("far", "장소 B", "unknown", 37.02, 128.02);

        var result = new RuleBasedCourseRecommendationEngine().recommend(
                List.of(one, far, nearby), one, List.of("unknown"), "unknown", "day");

        assertThat(result).containsExactly(one, nearby, far);
    }

    private static Place place(String id, String name, String category, double latitude, double longitude) {
        return new Place(id, name, "강릉시", category, "", latitude, longitude, null, "test");
    }
}
