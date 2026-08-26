package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseScheduleCalculatorTest {
    @Test
    void calculatesDifferentArrivalTimesFromStayAndWalkingTime() {
        Course course = new Course("day", null, null);
        CourseStop first = stop(course, 1, "첫 장소");
        CourseStop second = stop(course, 2, "두 번째 장소");
        CourseStop third = stop(course, 3, "세 번째 장소");
        CourseScheduleCalculator calculator = new CourseScheduleCalculator();

        List<CourseResponse.RouteSegmentResponse> segments = List.of(
                new CourseResponse.RouteSegmentResponse(first.getId().toString(), second.getId().toString(), 500, 301, List.of()),
                new CourseResponse.RouteSegmentResponse(second.getId().toString(), third.getId().toString(), 700, 600, List.of())
        );

        assertThat(calculator.calculate(List.of(first, second, third), segments))
                .containsExactly("09:00", "10:06", "11:16");
    }

    @Test
    void stillAdvancesScheduleWhenRouteIsUnavailable() {
        Course course = new Course("day", null, null);
        CourseStop first = stop(course, 1, "첫 장소");
        CourseStop second = stop(course, 2, "두 번째 장소");

        assertThat(new CourseScheduleCalculator().calculate(List.of(first, second), List.of()))
                .containsExactly("09:00", "10:00");
    }

    private static CourseStop stop(Course course, int sequence, String name) {
        Place place = new Place(name, name, "강릉", "nature", "", 37.0, 128.0, null, "test");
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        CourseStop stop = new CourseStop(course, place, sequence, sequence == 1);
        ReflectionTestUtils.setField(stop, "id", UUID.randomUUID());
        return stop;
    }
}
