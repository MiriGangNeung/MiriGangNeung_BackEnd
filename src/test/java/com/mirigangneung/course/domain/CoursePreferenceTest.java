package com.mirigangneung.course.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoursePreferenceTest {
    @Test
    void preservesSelectedTravelTypesAndCompanion() {
        Course course = new Course("day", null, null, List.of("nature", "rest"), "couple");

        assertThat(course.getTravelTypes()).containsExactly("nature", "rest");
        assertThat(course.getCompanion()).isEqualTo("couple");
    }

    @Test
    void keepsLegacyCourseWithoutRecommendationPreferencesCompatible() {
        Course course = new Course("day", null, null);

        assertThat(course.getTravelTypes()).isEmpty();
        assertThat(course.getCompanion()).isBlank();
    }
}
