package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseExternalPlace;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.infrastructure.kakao.KakaoRouteClient;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRouteCalculatorTest {
    @Test
    void recalculatesEachAdjacentLegWhenAnExternalRestaurantIsInserted() {
        List<List<Double>> requestedCoordinates = new ArrayList<>();
        KakaoRouteClient client = (originLat, originLon, destinationLat, destinationLon) -> {
            requestedCoordinates.add(List.of(originLat, originLon, destinationLat, destinationLon));
            return new KakaoRouteClient.RouteResult(
                    1_000,
                    600,
                    List.of(List.of(originLon, originLat), List.of(destinationLon, destinationLat))
            );
        };
        CourseRouteCalculator calculator = new CourseRouteCalculator(client);
        Course course = new Course("day", null, null);
        Place first = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        CourseExternalPlace restaurant = new CourseExternalPlace(
                "KAKAO", "restaurant-1", "안목반점", "중식", "restaurant",
                "강릉", "강릉", "", "https://place.map.kakao.com/restaurant-1",
                37.005, 128.005
        );
        Place last = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");

        CourseRouteCalculator.Result result = calculator.calculate(List.of(
                new CourseStop(course, first, 1, true),
                new CourseStop(course, restaurant, 2, false),
                new CourseStop(course, last, 3, true)
        ));

        assertThat(requestedCoordinates).containsExactly(
                List.of(37.0, 128.0, 37.005, 128.005),
                List.of(37.005, 128.005, 37.01, 128.01)
        );
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.totalDistanceMeters()).isEqualTo(2_000);
        assertThat(result.totalTravelMinutes()).isEqualTo(20);
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments()).extracting(CourseResponse.RouteSegmentResponse::polyline)
                .containsOnly(List.of(List.of(128.0, 37.0), List.of(128.005, 37.005)),
                        List.of(List.of(128.005, 37.005), List.of(128.01, 37.01)));
    }

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
