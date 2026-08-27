package com.mirigangneung.course.recommendation;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NearbyPlaceRecommendationScorerTest {
    private final NearbyPlaceRecommendationScorer scorer = new NearbyPlaceRecommendationScorer();

    @Test
    void givesAHighExplainableScoreToANearbyCafeForRestAndCouple() {
        var place = nearby("안목 바다 카페", "음식점 > 카페", "강릉시 안목");

        var result = scorer.score(place, "cafe", 150, 2_000,
                List.of("rest"), "couple");

        assertThat(result.score()).isBetween(0, 100);
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("휴식"));
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("커플"));
    }

    @Test
    void averagesTwoTravelTypeFitsWithinTheTravelTypeWeight() {
        var place = nearby("일반 카페", "음식점 > 카페", "강릉시");

        int oneTypeScore = scorer.score(place, "cafe", 500, 2_000,
                List.of("rest"), "solo").score();
        int twoTypeScore = scorer.score(place, "cafe", 500, 2_000,
                List.of("rest", "culture"), "solo").score();

        assertThat(twoTypeScore).isLessThan(oneTypeScore);
    }

    @Test
    void givesCloserPlacesAHigherDistanceContribution() {
        var place = nearby("일반 카페", "음식점 > 카페", "강릉시");

        int nearScore = scorer.score(place, "cafe", 100, 2_000,
                List.of("rest"), "solo").score();
        int farScore = scorer.score(place, "cafe", 1_900, 2_000,
                List.of("rest"), "solo").score();

        assertThat(nearScore).isGreaterThan(farScore);
    }

    @Test
    void explainsWhenNoSelectedProfileKeywordMatches() {
        var place = nearby("일반 카페", "음식점 > 카페", "강릉시");

        var result = scorer.score(place, "cafe", 500, 2_000,
                List.of("active"), "family");

        assertThat(result.score()).isBetween(0, 100);
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("가까"));
    }

    private static KakaoLocalClient.NearbyPlace nearby(
            String name,
            String categoryName,
            String address
    ) {
        return new KakaoLocalClient.NearbyPlace(
                "kakao-1",
                name,
                categoryName,
                "CE7",
                address,
                address,
                "033-000-0000",
                "https://place.map.kakao.com/kakao-1",
                37.77,
                128.94,
                100
        );
    }
}
