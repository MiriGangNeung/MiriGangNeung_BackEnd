package com.mirigangneung.course.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.recommendation.CourseRecommendationEngine;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.place.service.PlaceService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock
    private CourseRepository courses;
    @Mock
    private CourseStopRepository stops;
    @Mock
    private PlaceService places;
    @Mock
    private CourseRecommendationEngine engine;
    @Mock
    private CourseRouteCalculator routeCalculator;

    @Test
    void rejectsDetailedPreferenceThatDoesNotBelongToSelectedTravelType() {
        CourseService service = new CourseService(courses, stops, places, engine, routeCalculator);
        CreateCourseRequest request = new CreateCourseRequest(
                List.of("place-id"),
                "place-id",
                List.of("food"),
                List.of("culture:museum"),
                "couple",
                "day",
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getCode())
                            .isEqualTo("INVALID_PREFERENCE");
                    org.assertj.core.api.Assertions.assertThat(exception.getStatus())
                            .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(courses, stops, places, engine, routeCalculator);
    }

    @Test
    void acceptsAllTravelTypesAndCuisineDetails() {
        CreateCourseRequest request = new CreateCourseRequest(
                List.of("place-id"),
                "place-id",
                List.of("food", "rest", "culture", "nature"),
                List.of("food:korean", "food:chinese", "food:japanese", "food:western"),
                "couple",
                "day",
                null,
                null
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsAnOversizedDetailPreferenceListAtRequestValidation() {
        CreateCourseRequest request = new CreateCourseRequest(
                List.of("place-id"),
                "place-id",
                List.of("food", "rest", "culture", "nature"),
                List.of(
                        "food:korean",
                        "food:chinese",
                        "food:japanese",
                        "food:western",
                        "rest:coffee",
                        "rest:dessert",
                        "culture:art",
                        "culture:exhibition",
                        "culture:museum",
                        "food:korean"
                ),
                "couple",
                "day",
                null,
                null
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("detailTypes"));
        }
    }

    @Test
    void rejectsAnUnknownDetailPreferenceWhenItsBroadTypeIsSelected() {
        CourseService service = new CourseService(courses, stops, places, engine, routeCalculator);
        CreateCourseRequest request = new CreateCourseRequest(
                List.of("place-id"),
                "place-id",
                List.of("food"),
                List.of("food:unknown"),
                "couple",
                "day",
                null,
                null
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_PREFERENCE"));
        verifyNoInteractions(courses, stops, places, engine, routeCalculator);
    }
}
