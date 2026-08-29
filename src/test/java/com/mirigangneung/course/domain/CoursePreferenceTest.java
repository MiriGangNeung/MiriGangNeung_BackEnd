package com.mirigangneung.course.domain;

import com.mirigangneung.course.dto.CourseResponse;
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
    void preservesDetailedPreferencesAndReturnsThemWithTheCourse() {
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food", "culture"),
                List.of("food:chinese", "culture:museum"),
                "couple"
        );

        assertThat(course.getDetailTypes())
                .containsExactly("food:chinese", "culture:museum");
        CourseResponse response = new CourseResponse(
                "course-id",
                course.getTitle(),
                course.getDurationType(),
                course.getTravelTypes(),
                course.getDetailTypes(),
                course.getCompanion(),
                List.of(),
                0,
                0,
                "UNAVAILABLE",
                List.of()
        );
        assertThat(response.detailTypes())
                .containsExactly("food:chinese", "culture:museum");
    }

    @Test
    void keepsLegacyCourseWithoutRecommendationPreferencesCompatible() {
        Course course = new Course("day", null, null);

        assertThat(course.getTravelTypes()).isEmpty();
        assertThat(course.getDetailTypes()).isEmpty();
        assertThat(course.getCompanion()).isBlank();
    }
}
