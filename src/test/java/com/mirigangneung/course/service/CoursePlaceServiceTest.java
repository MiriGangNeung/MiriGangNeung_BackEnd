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
import static org.mockito.ArgumentMatchers.anyDouble;
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
        assertThat(response.size()).isEqualTo(15);
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
    void keepsNonMatchingCandidatesButRanksTheSelectedDetailedPreferenceHigher() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(
                        nearby("closer-western", "양식 레스토랑", 37.0005, 128.0005, "음식점 > 양식", "FD6"),
                        nearby("farther-chinese", "안목반점", 37.0080, 128.0080, "음식점 > 중식 > 중국요리", "FD6"),
                        nearby("closer-fish", "안목해송횟집", 37.0005, 128.0005, "음식점 > 한식 > 해물,생선 > 회", "FD6")
                ));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .containsExactlyInAnyOrder("farther-chinese", "closer-western", "closer-fish");
        assertThat(response.places()).filteredOn(place -> place.externalPlaceId().equals("farther-chinese"))
                .singleElement()
                .extracting(NearbyPlaceResponse::recommendationScore)
                .satisfies(score -> assertThat(score).isGreaterThan(60));
        assertThat(response.places()).filteredOn(place -> !place.externalPlaceId().equals("farther-chinese"))
                .extracting(NearbyPlaceResponse::recommendationScore)
                .allSatisfy(score -> assertThat(score).isGreaterThan(40));
        List<Integer> nonMatchingScores = response.places().stream()
                .filter(place -> !place.externalPlaceId().equals("farther-chinese"))
                .map(NearbyPlaceResponse::recommendationScore)
                .toList();
        assertThat(nonMatchingScores).hasSize(2);
        assertThat(nonMatchingScores.get(0)).isEqualTo(nonMatchingScores.get(1));
        assertThat(response.places().stream()
                .filter(place -> place.externalPlaceId().equals("farther-chinese"))
                .findFirst()
                .orElseThrow()
                .recommendationScore())
                .isGreaterThan(nonMatchingScores.get(0));
    }

    @Test
    void fetchesUpToFiveSelectedCuisinePlacesForEachTourismStop() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place firstPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place secondPlace = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(
                new CourseStop(course, firstPlace, 1, true),
                new CourseStop(course, secondPlace, 2, false)
        ));
        when(localClient.searchByCategory(
                anyDouble(), anyDouble(), eq("FD6"), anyInt(), anyInt(), eq(15)
        )).thenReturn(List.of());
        when(localClient.searchByKeyword(
                eq("중식"), anyDouble(), anyDouble(), eq(2_000), anyInt(), eq(15)
        )).thenAnswer(invocation -> {
            double longitude = invocation.getArgument(1, Double.class);
            int page = invocation.getArgument(4, Integer.class);
            if (page > 0) {
                return List.of();
            }
            String prefix = longitude < 128.005 ? "first" : "second";
            return List.of(
                    nearby(prefix + "-fish", prefix + " 횟집", 37.0005, 128.0005, "음식점 > 한식 > 해물,생선 > 회", "FD6"),
                    nearby(prefix + "-chinese-1", prefix + " 중식당 1", 37.001, 128.001, "음식점 > 중식", "FD6"),
                    nearby(prefix + "-chinese-2", prefix + " 중식당 2", 37.002, 128.002, "음식점 > 중식", "FD6"),
                    nearby(prefix + "-chinese-3", prefix + " 중식당 3", 37.003, 128.003, "음식점 > 중식", "FD6"),
                    nearby(prefix + "-chinese-4", prefix + " 중식당 4", 37.004, 128.004, "음식점 > 중식", "FD6"),
                    nearby(prefix + "-chinese-5", prefix + " 중식당 5", 37.005, 128.005, "음식점 > 중식", "FD6")
            );
        });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.searchRadiusMeters()).isEqualTo(2_000);
        assertThat(response.places()).filteredOn(place ->
                place.externalPlaceId().endsWith("-chinese-1")
                        || place.externalPlaceId().endsWith("-chinese-2")
                        || place.externalPlaceId().endsWith("-chinese-3")
                        || place.externalPlaceId().endsWith("-chinese-4")
                        || place.externalPlaceId().endsWith("-chinese-5")
        ).hasSize(10);
        assertThat(response.places()).noneMatch(place -> place.externalPlaceId().endsWith("-fish"));
        verify(localClient).searchByKeyword("중식", 128.0, 37.0, 2_000, 0, 15);
        verify(localClient).searchByKeyword("중식", 128.01, 37.01, 2_000, 0, 15);
        verify(localClient, never()).searchByKeyword(
                eq("중식"), anyDouble(), anyDouble(), eq(5_000), anyInt(), eq(15)
        );
    }

    @Test
    void expandsNearbySearchToFiveKilometersWhenFiveKilometersProvideEnoughExactMatches() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), anyInt(), eq(0), eq(15)))
                .thenAnswer(invocation -> switch (invocation.getArgument(3, Integer.class)) {
                    case 2_000 -> List.of(
                            nearby("near-chinese", "강릉 중식당", 37.001, 128.001, "음식점 > 중식", "FD6"),
                            nearby("near-western", "강릉 양식당", 37.0005, 128.0005, "음식점 > 양식", "FD6"));
                    case 5_000 -> List.of(
                            nearby("expanded-chinese-1", "강릉 중국집1", 37.02, 128.02, "음식점 > 중식", "FD6"),
                            nearby("expanded-chinese-2", "강릉 중국집2", 37.021, 128.021, "음식점 > 중식", "FD6"),
                            nearby("expanded-chinese-3", "강릉 중국집3", 37.022, 128.022, "음식점 > 중식", "FD6"),
                            nearby("expanded-chinese-4", "강릉 중국집4", 37.023, 128.023, "음식점 > 중식", "FD6"),
                            nearby("expanded-chinese-5", "강릉 중국집5", 37.024, 128.024, "음식점 > 중식", "FD6"));
                    default -> List.of();
                });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .containsExactlyInAnyOrder(
                        "near-chinese",
                        "near-western",
                        "expanded-chinese-1",
                        "expanded-chinese-2",
                        "expanded-chinese-3",
                        "expanded-chinese-4",
                        "expanded-chinese-5"
                );
        assertThat(response.places().stream()
                .filter(place -> place.externalPlaceId().equals("expanded-chinese-1"))
                .findFirst()
                .orElseThrow()
                .recommendationReasons())
                .anyMatch(reason -> reason.contains("5km까지 검색"));
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 2_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 5_000, 0, 15);
        verify(localClient, never()).searchByCategory(128.0, 37.0, "FD6", 10_000, 0, 15);
    }

    @Test
    void expandsNearbySearchToTenKilometersWhenFiveKilometersStillHaveFewerThanFiveExactPreferences() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), anyInt(), eq(0), eq(15)))
                .thenAnswer(invocation -> switch (invocation.getArgument(3, Integer.class)) {
                    case 2_000 -> List.of(
                            nearby("near-chinese", "강릉 중식당", 37.001, 128.001, "음식점 > 중식", "FD6"),
                            nearby("near-western", "강릉 양식당", 37.0005, 128.0005, "음식점 > 양식", "FD6"));
                    case 5_000 -> List.of(nearby(
                            "five-kilometer-chinese", "강릉 중국집", 37.02, 128.02, "음식점 > 중식", "FD6"));
                    case 10_000 -> List.of(
                            nearby("ten-kilometer-chinese", "강릉 중국요리점", 37.06, 128.06, "음식점 > 중식", "FD6"),
                            nearby("ten-kilometer-chinese-2", "강릉 중국요리점2", 37.061, 128.061, "음식점 > 중식", "FD6"),
                            nearby("ten-kilometer-chinese-3", "강릉 중국요리점3", 37.062, 128.062, "음식점 > 중식", "FD6"));
                    default -> List.of();
                });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .contains("ten-kilometer-chinese");
        assertThat(response.places().stream()
                .filter(place -> place.externalPlaceId().equals("ten-kilometer-chinese"))
                .findFirst()
                .orElseThrow()
                .recommendationReasons())
                .anyMatch(reason -> reason.contains("10km까지 검색"));
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 2_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 5_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 10_000, 0, 15);
    }

    @Test
    void scoresMergedCandidatesAgainstTheFinalSearchRadius() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(anyDouble(), anyDouble(), eq("FD6"), anyInt(), eq(0), eq(15)))
                .thenAnswer(invocation -> switch (invocation.getArgument(3, Integer.class)) {
                    case 2_000 -> List.of(nearby(
                            "near-exact", "가까운 중식당", 37.0, 128.021, "음식점 > 중식", "FD6"));
                    case 5_000 -> List.of(nearby(
                            "far-exact", "먼 중식당", 37.035, 128.0, "음식점 > 중식", "FD6"));
                    case 10_000 -> List.of(
                            nearby("ten-exact-1", "중식당 3", 37.06, 128.06, "음식점 > 중식", "FD6"),
                            nearby("ten-exact-2", "중식당 4", 37.065, 128.065, "음식점 > 중식", "FD6"),
                            nearby("ten-exact-3", "중식당 5", 37.07, 128.07, "음식점 > 중식", "FD6"));
                    default -> List.of();
                });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.searchRadiusMeters()).isEqualTo(10_000);
        int nearScore = response.places().stream()
                .filter(place -> place.externalPlaceId().equals("near-exact"))
                .findFirst()
                .orElseThrow()
                .recommendationScore();
        int farScore = response.places().stream()
                .filter(place -> place.externalPlaceId().equals("far-exact"))
                .findFirst()
                .orElseThrow()
                .recommendationScore();
        assertThat(nearScore).isGreaterThan(farScore);
    }

    @Test
    void preservesTheFirstExpansionStageWhenACloserStopReplacesACandidate() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place firstPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        Place secondPlace = new Place("2", "안목해변", "강릉", "nature", "", 37.01, 128.01, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(
                new CourseStop(course, firstPlace, 1, true),
                new CourseStop(course, secondPlace, 2, false)
        ));
        when(localClient.searchByCategory(anyDouble(), anyDouble(), eq("FD6"), anyInt(), eq(0), eq(15)))
                .thenAnswer(invocation -> {
                    double longitude = invocation.getArgument(0, Double.class);
                    int radius = invocation.getArgument(3, Integer.class);
                    if (radius == 2_000 && longitude == 128.0) {
                        return List.of(nearby(
                                "shared-place", "공통 식당", 37.001, 128.001, "음식점 > 양식", "FD6"));
                    }
                    if (radius == 5_000 && longitude == 128.0) {
                        return List.of(nearby(
                                "shared-place", "공통 중식당", 37.0101, 128.0101, "음식점 > 중식", "FD6"));
                    }
                    if (radius == 5_000 && longitude == 128.01) {
                        return List.of(
                                nearby("expanded-1", "중식당 1", 37.0102, 128.0102, "음식점 > 중식", "FD6"),
                                nearby("expanded-2", "중식당 2", 37.0103, 128.0103, "음식점 > 중식", "FD6"),
                                nearby("expanded-3", "중식당 3", 37.0104, 128.0104, "음식점 > 중식", "FD6"));
                    }
                    return List.of();
                });
        when(localClient.searchByKeyword(
                eq("중식"), anyDouble(), anyDouble(), anyInt(), anyInt(), eq(15)
        )).thenAnswer(invocation -> {
            double longitude = invocation.getArgument(1, Double.class);
            int radius = invocation.getArgument(3, Integer.class);
            if (radius == 5_000 && longitude == 128.0) {
                return List.of(
                        nearby("keyword-expanded-1", "중식당 키워드1", 37.011, 128.011, "음식점 > 중식", "FD6"),
                        nearby("keyword-expanded-2", "중식당 키워드2", 37.012, 128.012, "음식점 > 중식", "FD6"),
                        nearby("keyword-expanded-3", "중식당 키워드3", 37.013, 128.013, "음식점 > 중식", "FD6"),
                        nearby("keyword-expanded-4", "중식당 키워드4", 37.014, 128.014, "음식점 > 중식", "FD6")
                );
            }
            if (radius == 5_000 && longitude == 128.01) {
                return List.of(
                        nearby("keyword-second-1", "중식당 두번째1", 37.015, 128.015, "음식점 > 중식", "FD6"),
                        nearby("keyword-second-2", "중식당 두번째2", 37.016, 128.016, "음식점 > 중식", "FD6")
                );
            }
            return List.of();
        });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.searchRadiusMeters()).isEqualTo(5_000);
        assertThat(response.places().stream()
                .filter(place -> place.externalPlaceId().equals("shared-place"))
                .findFirst()
                .orElseThrow()
                .recommendationReasons())
                .noneMatch(reason -> reason.contains("5km까지 검색"));
        assertThat(response.places()).filteredOn(place -> place.externalPlaceId().startsWith("expanded-"))
                .allSatisfy(place -> assertThat(place.recommendationReasons())
                        .anyMatch(reason -> reason.contains("5km까지 검색")));
    }

    @Test
    void expandsNearbySearchToFifteenKilometersWhenTenKilometersStillHaveFewerThanThreeExactPreferences() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), anyInt(), eq(0), eq(15)))
                .thenAnswer(invocation -> switch (invocation.getArgument(3, Integer.class)) {
                    case 2_000 -> List.of(nearby(
                            "near-western", "강릉 양식당", 37.0005, 128.0005, "음식점 > 양식", "FD6"));
                    case 5_000 -> List.of(nearby(
                            "five-kilometer-western", "강릉 양식당2", 37.02, 128.02, "음식점 > 양식", "FD6"));
                    case 10_000 -> List.of(nearby(
                            "ten-kilometer-western", "강릉 양식당3", 37.06, 128.06, "음식점 > 양식", "FD6"));
                    case 15_000 -> List.of(nearby(
                            "fifteen-kilometer-chinese", "강릉 중국집", 37.12, 128.12, "음식점 > 중식", "FD6"));
                    default -> List.of();
                });

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .contains("fifteen-kilometer-chinese");
        assertThat(response.searchRadiusMeters()).isEqualTo(15_000);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 2_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 5_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 10_000, 0, 15);
        verify(localClient).searchByCategory(128.0, 37.0, "FD6", 15_000, 0, 15);
    }

    @Test
    void keepsTheTwoKilometerSearchWhenAtLeastFiveExactPreferencesMatch() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course(
                "day",
                null,
                null,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        );
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course))
                .thenReturn(List.of(new CourseStop(course, tourismPlace, 1, true)));
        when(localClient.searchByCategory(eq(128.0), eq(37.0), eq("FD6"), eq(2_000), eq(0), eq(15)))
                .thenReturn(List.of(
                        nearby("chinese-1", "강릉 중식당1", 37.001, 128.001, "음식점 > 중식", "FD6"),
                        nearby("chinese-2", "강릉 중식당2", 37.002, 128.002, "음식점 > 중식", "FD6"),
                        nearby("chinese-3", "강릉 중식당3", 37.003, 128.003, "음식점 > 중식", "FD6"),
                        nearby("chinese-4", "강릉 중식당4", 37.004, 128.004, "음식점 > 중식", "FD6"),
                        nearby("chinese-5", "강릉 중식당5", 37.005, 128.005, "음식점 > 중식", "FD6")));

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        var response = service.nearby(courseId.toString(), "restaurant");

        assertThat(response.places()).hasSize(5);
        verify(localClient, never()).searchByCategory(128.0, 37.0, "FD6", 5_000, 0, 15);
        verify(localClient, never()).searchByCategory(128.0, 37.0, "FD6", 10_000, 0, 15);
    }

    @Test
    void doesNotCallKakaoBeforeAnAllSearchKeywordIsSubmitted() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        Place tourismPlace = new Place("1", "경포대", "강릉", "nature", "", 37.0, 128.0, null, "KTO");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));

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
                courseId.toString(), "all", "cafe", null, "recommended", " ", 0, 15
        );

        assertThat(response.scope()).isEqualTo("all");
        assertThat(response.category()).isEqualTo("cafe");
        assertThat(response.page()).isZero();
        assertThat(response.isEnd()).isTrue();
        assertThat(response.places()).isEmpty();
        verifyNoInteractions(localClient, stops);
    }

    @Test
    void searchesAllGangneungPlacesByKeywordWhenKeywordIsProvided() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        CourseStop existingStop = mock(CourseStop.class);
        when(existingStop.getExternalPlaceId()).thenReturn("existing-id");
        when(existingStop.getKakaoPlaceId()).thenReturn("catalog-kakao-id");
        when(existingStop.getDisplayName()).thenReturn("기존 장소");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.findByCourseOrderBySequenceAsc(course)).thenReturn(List.of(existingStop));
        when(localClient.searchByKeywordInRect(
                eq("테라로사"), eq("128.70,37.95,129.05,37.65"), eq("FD6"), eq(0), eq(15)))
                .thenReturn(new KakaoLocalClient.SearchPage(List.of(
                        nearby("eligible-id", "테라로사 강릉점", 37.001, 128.001, "음식점 > 카페", "FD6"),
                        nearby("wrong-category-id", "테라로사 카페", 37.002, 128.002, "카페", "CE7"),
                        nearby("existing-id", "기존 장소 이름 변경", 37.003, 128.003, "음식점", "FD6"),
                        nearby("catalog-kakao-id", "카탈로그 장소 이름 변경", 37.004, 128.004, "음식점", "FD6"),
                        nearby("same-name-id", "기존장소", 37.005, 128.005, "음식점", "FD6")
                ), 0, true));

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
                courseId.toString(), "all", "restaurant", null, "recommended", " 테라로사 ", 0, 15
        );

        verify(localClient).searchByKeywordInRect(
                "테라로사", "128.70,37.95,129.05,37.65", "FD6", 0, 15
        );
        verify(localClient, never()).searchByCategoryInRect(anyString(), anyString(), anyInt(), anyInt());
        assertThat(response.places()).extracting(NearbyPlaceResponse::externalPlaceId)
                .containsExactly("eligible-id");
        assertThat(response.places().get(0).distanceMeters()).isNull();
        assertThat(response.places().get(0).recommendationScore()).isNull();
        assertThat(response.places().get(0).recommendationReasons()).isEmpty();
    }

    @Test
    void rejectsAllSearchWhenSortIsUnsupported() {
        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        assertThatThrownBy(() -> service.search(
                UUID.randomUUID().toString(), "all", "restaurant", null, "popular", "테라로사", 0, 15
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_SORT"));
        verifyNoInteractions(courses, stops, localClient);
    }

    @Test
    void rejectsAZeroBasedKakaoPageThatWouldExceedTheProviderLimit() {
        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        assertThatThrownBy(() -> service.search(
                UUID.randomUUID().toString(), "all", "restaurant", null, "recommended", "테라로사", 45, 15
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_PAGE"));
        verifyNoInteractions(courses, stops, localClient);
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

    @Test
    void rejectsAddingAPlaceWhenItsKakaoIdBelongsToACatalogStop() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course("day", null, null);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(stops.existsByCourseAndExternalPlace_ExternalPlaceId(course, "catalog-kakao-id"))
                .thenReturn(false);
        when(stops.existsByCourseAndPlace_KakaoPlaceId(course, "catalog-kakao-id"))
                .thenReturn(true);

        CoursePlaceService service = new CoursePlaceService(
                courses,
                stops,
                externalPlaces,
                localClient,
                routeCalculator,
                new KakaoLocalProperties("https://example.test", "secret", null, 2_000, 15)
        );

        AddExternalStopRequest request = new AddExternalStopRequest(
                " catalog-kakao-id ",
                "이름이 바뀐 장소",
                "restaurant",
                "음식점",
                "강릉",
                "강릉",
                "",
                "https://place.map.kakao.com/catalog-kakao-id",
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
