package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.course.recommendation.CourseRecommendationEngine;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.service.PlaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(courses, stops, places, engine, routeCalculator);
        when(courses.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", UUID.randomUUID());
            return course;
        });
        when(stops.findByCourseOrderBySequenceAsc(any(Course.class))).thenReturn(List.of());
        when(routeCalculator.calculate(any())).thenReturn(
                new CourseRouteCalculator.Result("READY", 0, 0, List.of()));
    }

    @Test
    void forwardsCoursePreferencesToRecommendationEngine() {
        Place onePick = place("one");
        Place second = place("two");
        when(places.find("one")).thenReturn(onePick);
        when(places.find("two")).thenReturn(second);
        when(engine.recommend(any(), any(), any(), any(), any())).thenReturn(List.of(onePick, second));

        CreateCourseRequest request = new CreateCourseRequest(
                List.of("one", "two"),
                "one",
                List.of("food", "rest"),
                "couple",
                "day",
                null,
                null
        );

        CourseResponse response = service.create(request);

        assertThat(response.duration()).isEqualTo("day");
        ArgumentCaptor<List<String>> types = ArgumentCaptor.forClass(List.class);
        verify(engine).recommend(any(), any(), types.capture(), any(), any());
        assertThat(types.getValue()).containsExactly("food", "rest");
    }

    private static Place place(String id) {
        Place place = new Place(id, id, "강릉시", "nature", "", 37.0, 128.0, null, "test");
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        return place;
    }
}
