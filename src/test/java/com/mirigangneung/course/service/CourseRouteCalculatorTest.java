package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.infrastructure.kakao.KakaoRouteClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRouteCalculatorTest {
    @Test
    void sumsWalkingDistanceAndDurationBetweenAdjacentStops() {
        KakaoRouteClient client = (originLat, originLon, destinationLat, destinationLon) ->
                new KakaoRouteClient.RouteResult(1_000, 120, List.of(List.of(128.0, 37.0)));
        CourseRouteCalculator calculator = new CourseRouteCalculator(client);
        Course course = new Course("day", null, null);
        Place first = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place second = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");

        CourseRouteCalculator.Result result = calculator.calculate(List.of(
                new CourseStop(course, first, 1, true),
                new CourseStop(course, second, 2, false)
        ));

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.totalDistanceMeters()).isEqualTo(1_000);
        assertThat(result.totalTravelMinutes()).isEqualTo(2);
        assertThat(result.segments()).hasSize(1);
    }

    @Test
    void reportsUnavailableWhenRouteProviderReturnsNoPath() {
        CourseRouteCalculator calculator = new CourseRouteCalculator(
                (originLat, originLon, destinationLat, destinationLon) ->
                        new KakaoRouteClient.RouteResult(0, 0, List.of())
        );
        Course course = new Course("day", null, null);
        Place first = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place second = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");

        CourseRouteCalculator.Result result = calculator.calculate(List.of(
                new CourseStop(course, first, 1, true),
                new CourseStop(course, second, 2, false)
        ));

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.totalDistanceMeters()).isZero();
        assertThat(result.totalTravelMinutes()).isZero();
        assertThat(result.segments()).isEmpty();
    }
}
