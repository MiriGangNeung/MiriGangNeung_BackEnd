package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseExternalPlace;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRouteOptimizationServiceTest {
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
    void reordersTourismAndExternalStopsOnlyWhenKakaoWalkingDistanceGetsShorter() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        ReflectionTestUtils.setField(course, "id", courseId);
        CourseStop gyeongpo = tourismStop(course, "경포대", 128.0, 1, true);
        CourseStop restaurant = externalStop(course, "안목반점", "restaurant", 128.02, 2);
        CourseStop jeongdongjin = tourismStop(course, "정동진", 128.01, 3, false);
        CourseStop cafe = externalStop(course, "테라로사", "cafe", 128.03, 4);
        List<CourseStop> originalOrder = List.of(gyeongpo, restaurant, jeongdongjin, cafe);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(originalOrder);
        when(routeCalculator.calculate(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CourseStop> routeOrder = invocation.getArgument(0);
            List<String> names = routeOrder.stream().map(CourseStop::getDisplayName).toList();
            boolean isShorterOrder = names.equals(List.of("경포대", "정동진", "안목반점", "테라로사"));
            int distance = isShorterOrder ? 402 : 900;
            int segmentDistance = distance / (routeOrder.size() - 1);
            List<CourseResponse.RouteSegmentResponse> routeSegments = IntStream.range(1, routeOrder.size())
                    .mapToObj(index -> {
                        CourseStop from = routeOrder.get(index - 1);
                        CourseStop to = routeOrder.get(index);
                        return new CourseResponse.RouteSegmentResponse(
                                from.getId().toString(),
                                to.getId().toString(),
                                segmentDistance,
                                200,
                                List.of(
                                        List.of(from.getLongitude(), from.getLatitude()),
                                        List.of(to.getLongitude(), to.getLatitude())
                                )
                        );
                    })
                    .toList();
            return new CourseRouteCalculator.Result("READY", distance, 10, routeSegments);
        });

        CourseResponse response = service().optimizeStops(courseId.toString());

        assertThat(response.stops()).extracting(CourseResponse.StopResponse::name)
                .containsExactly("경포대", "정동진", "안목반점", "테라로사");
        assertThat(response.stops()).extracting(CourseResponse.StopResponse::sequence)
                .containsExactly(1, 2, 3, 4);
        assertThat(response.totalDistanceMeters()).isEqualTo(402);
        assertThat(response.routeStatus()).isEqualTo("READY");
        assertThat(response.routeSegments()).hasSize(3);
        assertThat(response.routeSegments().get(0).fromStopId())
                .isEqualTo(response.stops().get(0).stopId());
        assertThat(response.routeSegments().get(0).toStopId())
                .isEqualTo(response.stops().get(1).stopId());
        assertThat(gyeongpo.getSequence()).isEqualTo(1);
        assertThat(jeongdongjin.getSequence()).isEqualTo(2);
        assertThat(restaurant.getSequence()).isEqualTo(3);
        assertThat(cafe.getSequence()).isEqualTo(4);
        verify(stops).saveAll(any());
    }

    @Test
    void keepsOriginalOrderWhenNoCandidateHasAShorterWalkingDistance() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        ReflectionTestUtils.setField(course, "id", courseId);
        List<CourseStop> originalOrder = List.of(
                tourismStop(course, "first", 128.0, 1, true),
                tourismStop(course, "second", 128.02, 2, false),
                tourismStop(course, "third", 128.01, 3, false)
        );
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(originalOrder);
        when(routeCalculator.calculate(any())).thenReturn(
                new CourseRouteCalculator.Result("READY", 1_000, 600, List.of())
        );

        CourseResponse response = service().optimizeStops(courseId.toString());

        assertThat(response.stops()).extracting(CourseResponse.StopResponse::name)
                .containsExactly("first", "second", "third");
        assertThat(response.totalDistanceMeters()).isEqualTo(1_000);
        verify(stops, never()).saveAll(any());
    }

    @Test
    void keepsOriginalOrderWhenTheExistingWalkingRouteIsUnavailable() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        ReflectionTestUtils.setField(course, "id", courseId);
        List<CourseStop> originalOrder = List.of(
                tourismStop(course, "first", 128.0, 1, true),
                tourismStop(course, "second", 128.02, 2, false),
                tourismStop(course, "third", 128.01, 3, false)
        );
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(originalOrder);
        when(routeCalculator.calculate(any())).thenReturn(
                new CourseRouteCalculator.Result("UNAVAILABLE", 0, 0, List.of())
        );

        CourseResponse response = service().optimizeStops(courseId.toString());

        assertThat(response.stops()).extracting(CourseResponse.StopResponse::name)
                .containsExactly("first", "second", "third");
        assertThat(response.routeStatus()).isEqualTo("UNAVAILABLE");
        verify(routeCalculator).calculate(originalOrder);
        verify(stops, never()).saveAll(any());
    }

    private CoursePlaceService service() {
        return new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );
    }

    private static CourseStop tourismStop(Course course, String name, double longitude, int sequence, boolean onePick) {
        Place place = new Place(name, name, "Gangneung", "nature", "", 37.0, longitude, null, "KTO");
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        CourseStop stop = new CourseStop(course, place, sequence, onePick);
        ReflectionTestUtils.setField(stop, "id", UUID.randomUUID());
        return stop;
    }

    private static CourseStop externalStop(Course course, String name, String category, double longitude, int sequence) {
        CourseExternalPlace place = new CourseExternalPlace(
                "KAKAO", "external-" + name, name, category, category,
                "Gangneung", "Gangneung", "", "https://place.map.kakao.com/1",
                37.0, longitude
        );
        CourseStop stop = new CourseStop(course, place, sequence, false);
        ReflectionTestUtils.setField(stop, "id", UUID.randomUUID());
        return stop;
    }
}
