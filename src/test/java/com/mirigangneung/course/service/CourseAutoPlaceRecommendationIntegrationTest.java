package com.mirigangneung.course.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.course.recommendation.CourseRecommendationEngine;
import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-auto-place-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "tour.api.sync-on-startup=false",
        "image.cache.enabled=false",
        "kakao.local.radius-meters=15000",
        "kakao.local.key=test-key"
})
@Transactional
class CourseAutoPlaceRecommendationIntegrationTest {
    @Autowired
    private CourseService courseService;
    @Autowired
    private PlaceRepository places;
    @MockitoBean
    private CourseRecommendationEngine recommendationEngine;
    @MockitoBean
    private KakaoLocalClient kakaoLocalClient;
    @MockitoBean
    private CourseRouteCalculator routeCalculator;

    @Test
    void addsTheHighestScoringRestaurantAndCafeAfterTheTourismStop() {
        Place tourismPlace = places.save(new Place(
                "auto-recommendation-test-tourism",
                "테스트 해변",
                "강릉시 테스트로",
                "nature",
                "테스트 관광지",
                37.75,
                128.90,
                null,
                "KTO"
        ));
        when(recommendationEngine.recommend(anyList(), any(Place.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> List.of(invocation.<Place>getArgument(1)));
        when(routeCalculator.calculate(anyList())).thenReturn(
                new CourseRouteCalculator.Result("UNAVAILABLE", 0, 0, List.of())
        );
        when(kakaoLocalClient.searchByCategory(
                anyDouble(), anyDouble(), anyString(), anyInt(), anyInt(), anyInt()
        )).thenAnswer(invocation -> {
            String categoryCode = invocation.getArgument(2);
            int page = invocation.getArgument(4);
            if (page > 0) {
                return List.of();
            }
            if ("FD6".equals(categoryCode)) {
                return List.of(
                        kakaoPlace("restaurant-far", "강릉 식당", "음식점", "FD6", 37.751, 128.90),
                        kakaoPlace("restaurant-near", "강릉 식당", "음식점", "FD6", 37.7501, 128.90)
                );
            }
            if ("CE7".equals(categoryCode)) {
                return List.of(
                        kakaoPlace("cafe-franchise", "이디야커피 강릉점", "음식점 > 카페 > 커피전문점",
                                "CE7", 37.75015, 128.90),
                        kakaoPlace("cafe-local", "안목 바다 로스터리", "음식점 > 카페 > 커피전문점",
                                "CE7", 37.7509, 128.90)
                );
            }
            return List.of();
        });

        CourseResponse response = courseService.create(new CreateCourseRequest(
                List.of(tourismPlace.getId().toString()),
                tourismPlace.getId().toString(),
                List.of("nature"),
                List.of(),
                "family",
                "day",
                null,
                null
        ));

        assertThat(response.stops())
                .extracting(CourseResponse.StopResponse::category)
                .containsExactly("nature", "restaurant", "cafe");
        assertThat(response.stops())
                .extracting(CourseResponse.StopResponse::externalPlaceId)
                .containsExactly(null, "restaurant-near", "cafe-local");
    }

    @Test
    void placesAutomaticStopsAtTheLowestDetourPositionsWithoutReorderingTourismStops() {
        Place firstTourismPlace = saveTourismPlace("auto-route-west", "서쪽 관광지", 37.70);
        Place middleTourismPlace = saveTourismPlace("auto-route-middle", "중앙 관광지", 37.75);
        Place lastTourismPlace = saveTourismPlace("auto-route-east", "동쪽 관광지", 37.80);
        when(recommendationEngine.recommend(anyList(), any(Place.class), anyList(), anyString(), anyString()))
                .thenReturn(List.of(firstTourismPlace, middleTourismPlace, lastTourismPlace));
        when(routeCalculator.calculate(anyList())).thenReturn(
                new CourseRouteCalculator.Result("UNAVAILABLE", 0, 0, List.of())
        );
        when(kakaoLocalClient.searchByCategory(
                anyDouble(), anyDouble(), anyString(), anyInt(), anyInt(), anyInt()
        )).thenAnswer(invocation -> {
            String categoryCode = invocation.getArgument(2);
            int page = invocation.getArgument(4);
            if (page > 0) {
                return List.of();
            }
            if ("FD6".equals(categoryCode)) {
                return List.of(kakaoPlace(
                        "restaurant-east",
                        "동쪽 식당",
                        "음식점",
                        "FD6",
                        37.799,
                        128.90
                ));
            }
            if ("CE7".equals(categoryCode)) {
                return List.of(kakaoPlace(
                        "cafe-east",
                        "동쪽 카페",
                        "음식점 > 카페 > 커피전문점",
                        "CE7",
                        37.801,
                        128.90
                ));
            }
            return List.of();
        });

        CourseResponse response = courseService.create(new CreateCourseRequest(
                List.of(firstTourismPlace.getId().toString()),
                firstTourismPlace.getId().toString(),
                List.of("nature"),
                List.of(),
                "family",
                "day",
                null,
                null
        ));

        assertThat(response.stops())
                .extracting(CourseResponse.StopResponse::externalPlaceId)
                .containsExactly(null, null, "restaurant-east", null, "cafe-east");
        assertThat(response.stops())
                .extracting(CourseResponse.StopResponse::name)
                .containsExactly("서쪽 관광지", "중앙 관광지", "동쪽 식당", "동쪽 관광지", "동쪽 카페");
    }

    @Test
    void keepsTheTourismCourseWhenKakaoIsUnavailable() {
        Place tourismPlace = places.save(new Place(
                "auto-recommendation-kakao-failure-tourism",
                "테스트 해변",
                "강릉시 테스트로",
                "nature",
                "테스트 관광지",
                37.75,
                128.90,
                null,
                "KTO"
        ));
        when(recommendationEngine.recommend(anyList(), any(Place.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> List.of(invocation.<Place>getArgument(1)));
        when(routeCalculator.calculate(anyList())).thenReturn(
                new CourseRouteCalculator.Result("UNAVAILABLE", 0, 0, List.of())
        );
        when(kakaoLocalClient.searchByCategory(
                anyDouble(), anyDouble(), anyString(), anyInt(), anyInt(), anyInt()
        )).thenThrow(new ApiException("KAKAO_API_ERROR", HttpStatus.BAD_GATEWAY, "Kakao unavailable"));

        CourseResponse response = courseService.create(new CreateCourseRequest(
                List.of(tourismPlace.getId().toString()),
                tourismPlace.getId().toString(),
                List.of("nature"),
                List.of(),
                "family",
                "day",
                null,
                null
        ));

        assertThat(response.stops())
                .extracting(CourseResponse.StopResponse::category)
                .containsExactly("nature");
    }

    private static KakaoLocalClient.NearbyPlace kakaoPlace(
            String id,
            String name,
            String categoryName,
            String categoryCode,
            double latitude,
            double longitude
    ) {
        return new KakaoLocalClient.NearbyPlace(
                id,
                name,
                categoryName,
                categoryCode,
                "강릉시",
                "강릉시 테스트로",
                "",
                "https://place.map.kakao.com/" + id,
                latitude,
                longitude,
                null
        );
    }

    private Place saveTourismPlace(String contentId, String name, double latitude) {
        return places.save(new Place(
                contentId,
                name,
                "강릉시 테스트로",
                "nature",
                "테스트 관광지",
                latitude,
                128.90,
                null,
                "KTO"
        ));
    }
}
