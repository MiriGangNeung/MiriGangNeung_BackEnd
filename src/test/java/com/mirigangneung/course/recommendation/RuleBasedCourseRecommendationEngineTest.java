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
}
