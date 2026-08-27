package com.mirigangneung.course.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.AddExternalStopRequest;
import com.mirigangneung.course.dto.NearbyPlaceResponse;
import com.mirigangneung.course.repository.CourseExternalPlaceRepository;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.infrastructure.kakao.KakaoLocalProperties;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void sortsNearbyPlacesByPreferenceOrDistanceAndReturnsRecommendationDetails() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null, List.of("rest"), "couple");
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        CourseStop tourismStop = new CourseStop(course, tourismPlace, 1, true);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(tourismStop));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("CE7"), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(
                        nearby("near", "일반 카페", 37.0005, 128.0005),
                        nearby("match", "안목 바다 카페", 37.0080, 128.0080, "음식점 > 카페", "CE7")
                ));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var recommended = service.nearby(courseId.toString(), "cafe", null, "recommended");
        assertThat(recommended.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .containsExactly("match", "near");
        assertThat(recommended.places().get(0).recommendationScore()).isNotNull();
        assertThat(recommended.places().get(0).recommendationReasons())
                .anyMatch(reason -> reason.contains("휴식"));

        var distance = service.nearby(courseId.toString(), "cafe", null, "distance");
        assertThat(distance.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .containsExactly("near", "match");
    }

    @Test
    void searchesAllGangneungCafesWithoutNearbyDistanceMetadata() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategoryInRect(eq("128.70,37.95,129.05,37.65"), eq("CE7"), eq(0), eq(15)))
                .thenReturn(new KakaoLocalClient.SearchPage(
                        List.of(nearby("all-cafe", "강릉 전체 카페", 37.77, 128.94, "CE7")),
                        0,
                        true
                ));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties(
                        "https://example.test", "secret", null, 2_000, 15,
                        "128.70,37.95,129.05,37.65"
                )
        );

        var response = service.search(
                courseId.toString(), "all", "cafe", null, "recommended", null, 0, 15
        );

        assertThat(response.scope()).isEqualTo("all");
        assertThat(response.category()).isEqualTo("cafe");
        assertThat(response.page()).isZero();
        assertThat(response.isEnd()).isTrue();
        assertThat(response.places()).singleElement().satisfies(place -> {
            assertThat(place.externalPlaceId()).isEqualTo("all-cafe");
            assertThat(place.distanceMeters()).isNull();
            assertThat(place.nearestStopId()).isNull();
            assertThat(place.nearestStopName()).isNull();
            assertThat(place.recommendationScore()).isNull();
            assertThat(place.recommendationReasons()).isEmpty();
        });
        verify(localClient).searchByCategoryInRect(
                "128.70,37.95,129.05,37.65", "CE7", 0, 15
        );
    }

    @Test
    void searchesAllGangneungPlacesByKeywordWhenKeywordIsProvided() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of());
        when(localClient.searchByKeywordInRect(
                eq("테라로사"), eq("128.70,37.95,129.05,37.65"), eq("FD6"), eq(0), eq(15)))
                .thenReturn(new KakaoLocalClient.SearchPage(List.of(), 0, true));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties(
                        "https://example.test", "secret", null, 2_000, 15,
                        "128.70,37.95,129.05,37.65"
                )
        );

        service.search(courseId.toString(), "all", "restaurant", null, "recommended", " 테라로사 ", 0, 15);

        verify(localClient).searchByKeywordInRect(
                "테라로사", "128.70,37.95,129.05,37.65", "FD6", 0, 15
        );
        verify(localClient, never()).searchByCategoryInRect(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void rejectsUnsupportedNearbySort() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null, List.of("rest"), "couple");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        assertThatThrownBy(() -> service.nearby(courseId.toString(), "cafe", null, "popular"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_SORT");
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(stops, localClient);
    }

    @Test
    void limitsNearbyResultsToTheSelectedTourismStop() {
        UUID courseId = UUID.randomUUID();
        UUID selectedStopId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place selectedPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place otherPlace = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");
        CourseStop selectedStop = mock(CourseStop.class);
        CourseStop otherStop = mock(CourseStop.class);
        when(selectedStop.getId()).thenReturn(selectedStopId);
        when(selectedStop.getPlace()).thenReturn(selectedPlace);
        when(selectedStop.getLatitude()).thenReturn(selectedPlace.getLatitude());
        when(selectedStop.getLongitude()).thenReturn(selectedPlace.getLongitude());
        when(selectedStop.getDisplayName()).thenReturn(selectedPlace.getName());
        when(otherStop.getPlace()).thenReturn(otherPlace);
        when(otherStop.getLatitude()).thenReturn(otherPlace.getLatitude());
        when(otherStop.getLongitude()).thenReturn(otherPlace.getLongitude());
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(selectedStop, otherStop));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(nearby("selected", "선택 관광지 주변 식당", 37.001, 128.001)));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant", selectedStopId.toString());

        assertThat(response.places()).singleElement().satisfies(place -> {
            assertThat(place.externalPlaceId()).isEqualTo("selected");
            assertThat(place.nearestStopId()).isEqualTo(selectedStopId.toString());
        });
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 2_000, 0, 15);
        verify(localClient, never()).searchByCategory(128.01, 37.01, "FD6", 2_000, 0, 15);
    }

    @ParameterizedTest
    @CsvSource({
            "attraction, AT4",
            "culture, CT1"
    })
    void searchesAttractionsAndCulturalFacilitiesWithTheirKakaoCategoryCodes(
            String category,
            String categoryCode
    ) {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        List<CourseStop> courseStops = List.of(new CourseStop(course, tourismPlace, 1, true));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(courseStops);
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq(categoryCode), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(nearby("new-place", "주변 장소", 37.001, 128.001, categoryCode)));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), category);

        assertThat(response.category()).isEqualTo(category);
        assertThat(response.places()).singleElement().satisfies(place ->
                assertThat(place.category()).isEqualTo(category)
        );
        verify(localClient).searchByCategory(128.0, 37.0, categoryCode, 2_000, 0, 15);
    }

    @Test
    void excludesAnExistingTourismStopWhenKakaoReturnsItsNormalizedName() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place selectedPlace = new Place("1", "경포해수욕장", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        List<CourseStop> courseStops = List.of(new CourseStop(course, selectedPlace, 1, true));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(courseStops);
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("AT4"), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(nearby("same-place", "경포해변", 37.001, 128.001, "AT4")));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "attraction");

        assertThat(response.places()).isEmpty();
    }

    @Test
    void rejectsAddingAnExistingTourismStopEvenWhenTheKakaoIdDiffers() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place selectedPlace = new Place("1", "경포해수욕장", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, selectedPlace, 1, true)));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        AddExternalStopRequest request = new AddExternalStopRequest(
                "different-kakao-id",
                "경포해변",
                "attraction",
                "관광명소",
                "강릉",
                "강릉",
                "",
                "https://place.map.kakao.com/different-kakao-id",
                128.001,
                37.001
        );

        assertThatThrownBy(() -> service.addExternalStop(courseId.toString(), request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PLACE_ALREADY_IN_COURSE"));
        verify(externalPlaces, never()).save(any());
    }

    private static KakaoLocalClient.NearbyPlace nearby(String id, String name, double lat, double lon) {
        return nearby(id, name, lat, lon, "FD6");
    }

    private static KakaoLocalClient.NearbyPlace nearby(
            String id,
            String name,
            double lat,
            double lon,
            String categoryCode
    ) {
        return nearby(id, name, lat, lon, "주변 장소", categoryCode);
    }

    private static KakaoLocalClient.NearbyPlace nearby(
            String id,
            String name,
            double lat,
            double lon,
            String categoryName,
            String categoryCode
    ) {
        return new KakaoLocalClient.NearbyPlace(
                id, name, categoryName, categoryCode, "강릉", "강릉", "", "https://place.map.kakao.com/" + id,
                lat, lon, null
        );
    }
}
