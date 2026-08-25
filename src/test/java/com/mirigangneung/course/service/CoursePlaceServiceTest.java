package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.repository.CourseExternalPlaceRepository;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.infrastructure.kakao.KakaoLocalProperties;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePlaceServiceTest {
    @Mock
    private CourseRepository courses;
    @Mock
    private CourseStopRepository stops;
    @Mock
    private CourseExternalPlaceRepository externalPlaces;
    @Mock
    private KakaoLocalClient localClient;
    @Mock
    private CourseRouteCalculator routeCalculator;

    @Test
    void mergesRestaurantsAroundAllTourismStopsAndSortsByNearestDistance() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place firstPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place secondPlace = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");
        List<CourseStop> courseStops = List.of(
                new CourseStop(course, firstPlace, 1, true),
                new CourseStop(course, secondPlace, 2, false)
        );
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(courseStops);
        when(localClient.searchByCategory(any(Double.class), any(Double.class), eq("FD6"), eq(2_000), any(Integer.class), eq(15)))
                .thenAnswer(invocation -> invocation.getArgument(4, Integer.class) == 0
                        ? List.of(
                        nearby("same", "같은 식당", 37.001, 128.001),
                        nearby("far", "먼 식당", 37.02, 128.02))
                        : List.of());

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.category()).isEqualTo("restaurant");
        assertThat(response.places()).extracting(place -> place.externalPlaceId())
                .containsExactly("same", "far");
        assertThat(response.places().get(0).distanceMeters())
                .isLessThan(response.places().get(1).distanceMeters());
    }

    private static KakaoLocalClient.NearbyPlace nearby(String id, String name, double lat, double lon) {
        return new KakaoLocalClient.NearbyPlace(
                id, name, "음식점", "FD6", "강릉", "강릉", "", "https://place.map.kakao.com/" + id,
                lat, lon, null
        );
    }
}
