package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRouteOrderOptimizerTest {
    @Test
    void considersTheReverseOrderWhenThereAreTwoStops() {
        Course course = new Course("day", null, null);
        CourseStop first = stop(course, "first", 128.0);
        CourseStop second = stop(course, "second", 128.02);

        assertThat(new CourseRouteOrderOptimizer().candidateOrders(List.of(first, second)))
                .containsExactly(List.of(second, first));
    }

    @Test
    void proposesAShorterOpenPathWithoutDroppingOrDuplicatingStops() {
        Course course = new Course("day", null, null);
        CourseStop first = stop(course, "first", 128.0);
        CourseStop second = stop(course, "second", 128.02);
        CourseStop third = stop(course, "third", 128.01);
        CourseStop fourth = stop(course, "fourth", 128.03);

        List<CourseStop> currentOrder = List.of(first, second, third, fourth);
        List<List<CourseStop>> candidates = new CourseRouteOrderOptimizer().candidateOrders(currentOrder);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).anySatisfy(order ->
                assertThat(order).containsExactly(first, third, second, fourth)
        );
        assertThat(candidates).allSatisfy(order ->
                assertThat(order).containsExactlyInAnyOrder(first, second, third, fourth)
        );
        assertThat(candidates).allSatisfy(order -> assertThat(order).isNotEqualTo(currentOrder));
    }

    @Test
    void doesNotSuggestReorderingWhenAnyStopHasNoCoordinates() {
        Course course = new Course("day", null, null);
        CourseStop first = stop(course, "first", 128.0);
        CourseStop second = new CourseStop(course,
                new Place("missing", "missing", "Gangneung", "nature", "", null, null, null, "KTO"),
                2,
                false);
        CourseStop third = stop(course, "third", 128.02);

        assertThat(new CourseRouteOrderOptimizer().candidateOrders(List.of(first, second, third))).isEmpty();
    }

    private static CourseStop stop(Course course, String name, double longitude) {
        Place place = new Place(name, name, "Gangneung", "nature", "", 37.0, longitude, null, "KTO");
        return new CourseStop(course, place, 1, false);
    }
}
