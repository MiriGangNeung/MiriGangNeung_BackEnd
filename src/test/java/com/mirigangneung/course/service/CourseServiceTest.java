package com.mirigangneung.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.recommendation.CourseRecommendationEngine;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.service.PlaceService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    @Mock
    private CoursePlaceService coursePlaceService;

    @Test
    void getReturnsReadyRouteWithLongitudeLatitudePolyline() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        ReflectionTestUtils.setField(course, "id", courseId);
        Place firstPlace = new Place(
                "place-1", "안목해변", "강릉", "nature", "", 37.772, 128.948, null, "KTO");
        Place secondPlace = new Place(
                "place-2", "강릉항", "강릉", "nature", "", 37.773, 128.949, null, "KTO");
        ReflectionTestUtils.setField(firstPlace, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(secondPlace, "id", UUID.randomUUID());
        List<CourseStop> courseStops = List.of(
                new CourseStop(course, firstPlace, 1, true),
                new CourseStop(course, secondPlace, 2, false)
        );
        List<List<Double>> polyline = List.of(
                List.of(128.948, 37.772),
                List.of(128.9485, 37.7725),
                List.of(128.949, 37.773)
        );
        CourseResponse.RouteSegmentResponse segment = new CourseResponse.RouteSegmentResponse(
                "stop-1", "stop-2", 240, 180, polyline);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(courseStops);
        when(routeCalculator.calculate(courseStops)).thenReturn(
                new CourseRouteCalculator.Result("READY", 240, 3, List.of(segment)));

        CourseResponse response = new CourseService(
                courses, stops, places, engine, routeCalculator, coursePlaceService
        ).get(courseId.toString());
        JsonNode responseJson = new ObjectMapper().valueToTree(response);

        assertThat(responseJson.path("routeStatus").asText()).isEqualTo("READY");
        JsonNode routeSegments = responseJson.path("routeSegments");
        assertThat(routeSegments.isArray()).isTrue();
        assertThat(routeSegments.size()).isPositive();
        JsonNode responsePolyline = routeSegments.path(0).path("polyline");
        assertThat(responsePolyline.size()).isGreaterThanOrEqualTo(2);
        assertThat(responsePolyline.get(0).get(0).asDouble()).isEqualTo(128.948);
        assertThat(responsePolyline.get(0).get(1).asDouble()).isEqualTo(37.772);
        assertThat(responsePolyline.get(1).get(0).asDouble()).isEqualTo(128.9485);
        assertThat(responsePolyline.get(1).get(1).asDouble()).isEqualTo(37.7725);
    }

    @Test
    void rejectsDetailedPreferenceThatDoesNotBelongToSelectedTravelType() {
        CourseService service = new CourseService(courses, stops, places, engine, routeCalculator, coursePlaceService);
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
        verifyNoInteractions(courses, stops, places, engine, routeCalculator, coursePlaceService);
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
        CourseService service = new CourseService(courses, stops, places, engine, routeCalculator, coursePlaceService);
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
        verifyNoInteractions(courses, stops, places, engine, routeCalculator, coursePlaceService);
    }
}
