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
    void usesTheBestTravelTypeInsteadOfAveragingSelectedTypes() {
        var place = nearby("일반 카페", "음식점 > 카페", "강릉시");

        int oneTypeScore = scorer.score(place, "cafe", 500, 2_000,
                List.of("rest"), "solo").score();
        int twoTypeScore = scorer.score(place, "cafe", 500, 2_000,
                List.of("rest", "culture"), "solo").score();

        assertThat(twoTypeScore).isEqualTo(oneTypeScore);
    }

    @Test
    void prioritizesAnExactCuisineMatchOverACloserCuisineMismatch() {
        int exactMatch = scorer.score(
                nearby("정통 중식당", "음식점 > 중식", "강릉시"),
                "restaurant",
                1_800,
                2_000,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        ).score();
        int mismatch = scorer.score(
                nearby("양식 레스토랑", "음식점 > 양식", "강릉시"),
                "restaurant",
                100,
                2_000,
                List.of("food"),
                List.of("food:chinese"),
                "solo"
        ).score();

        assertThat(exactMatch).isGreaterThan(mismatch);
    }

    @Test
    void keepsAnExactCuisineAboveACloserNonMatchingCuisine() {
        int exactMatch = scorer.score(
                nearby("일식당", "음식점 > 일식", "강릉시 일반도로"),
                "restaurant",
                2_000,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                null
        ).score();
        int mismatch = scorer.score(
                nearby("한식당", "음식점 > 한식", "강릉시 일반도로"),
                "restaurant",
                0,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                null
        ).score();

        assertThat(exactMatch).isGreaterThan(mismatch);
    }

    @Test
    void keepsAHighLevelNeutralScoreForAPlaceOutsideTheSelectedCuisine() {
        int seafoodPlace = scorer.score(
                nearby("안목해송횟집", "음식점 > 한식 > 해물,생선 > 회", "강릉시"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:chinese"),
                null
        ).score();
        int neutralRestaurant = scorer.score(
                nearby("일반 식당", "음식점", "강릉시"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:chinese"),
                null
        ).score();

        assertThat(seafoodPlace).isEqualTo(neutralRestaurant);
    }

    @Test
    void doesNotClaimThatAnUnclassifiedRestaurantMatchesTheSelectedCuisine() {
        var result = scorer.score(
                nearby("일반 식당", "음식점", "강릉시"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:chinese"),
                null
        );

        assertThat(result.reasons()).noneMatch(reason -> reason.contains("식도락 취향에 맞는"));
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("일반 후보로 함께 보여드려요"));
    }

    @Test
    void recognizesAnArtMuseumAsARelatedExhibitionPreference() {
        int relatedCulturePlace = scorer.score(
                nearby("정동진역미술관", "문화시설 > 미술관", "강릉시 정동진"),
                "culture",
                1_000,
                2_000,
                List.of("culture"),
                List.of("culture:exhibition"),
                null
        ).score();
        int categoryOnlyCulturePlace = scorer.score(
                nearby("일반 문화공간", "문화시설", "강릉시 정동진"),
                "culture",
                1_000,
                2_000,
                List.of("culture"),
                List.of("culture:exhibition"),
                null
        ).score();

        assertThat(relatedCulturePlace).isGreaterThan(categoryOnlyCulturePlace);
    }

    @Test
    void suppressesAnActivityCenterWhenRecommendingNatureAndWalks() {
        int naturePlace = scorer.score(
                nearby("경포해수욕장", "관광명소 > 해수욕장", "강릉시 경포"),
                "attraction",
                500,
                2_000,
                List.of("nature"),
                List.of(),
                null
        ).score();
        int activityCenter = scorer.score(
                nearby("경포해변인증센터", "관광명소 > 자전거여행", "강릉시 경포"),
                "attraction",
                500,
                2_000,
                List.of("nature"),
                List.of(),
                null
        ).score();

        assertThat(naturePlace).isGreaterThan(activityCenter);
    }

    @Test
    void prioritizesAKeywordMatchInKakaoCategoryPathOverAddressText() {
        int categoryMatch = scorer.score(
                nearby("동네 식당", "음식점 > 일식", "강릉시 일반도로"),
                "restaurant",
                1_000,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                null
        ).score();
        int addressOnlyMatch = scorer.score(
                nearby("동네 식당", "음식점", "강릉시 일식길"),
                "restaurant",
                1_000,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                null
        ).score();

        assertThat(categoryMatch).isGreaterThan(addressOnlyMatch);
    }

    @Test
    void penalizesANonCuisineFoodPlaceEvenWhenItIsCloser() {
        int exactMatch = scorer.score(
                nearby("일식당", "음식점 > 일식", "강릉시 일반도로"),
                "restaurant",
                2_000,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                "couple"
        ).score();
        int nonCuisineMatch = scorer.score(
                nearby("경포 바다 치킨", "음식점 > 치킨", "강릉시 일반도로"),
                "restaurant",
                0,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                "couple"
        ).score();

        assertThat(exactMatch).isGreaterThan(nonCuisineMatch);
    }

    @Test
    void prioritizesCoffeeOverADessertOnlyCafeForCoffeePreference() {
        int coffeeMatch = scorer.score(
                nearby("바다 로스터리", "음식점 > 카페 > 커피전문점", "강릉시"),
                "cafe",
                500,
                2_000,
                List.of("rest"),
                List.of("rest:coffee"),
                null
        ).score();
        int dessertOnlyCafe = scorer.score(
                nearby("디저트카페", "음식점 > 카페 > 디저트카페", "강릉시"),
                "cafe",
                500,
                2_000,
                List.of("rest"),
                List.of("rest:coffee"),
                null
        ).score();

        assertThat(coffeeMatch).isGreaterThan(dessertOnlyCafe);
    }

    @Test
    void doesNotTreatTheCultureParentCategoryAsAnArtDetailMatch() {
        int artPlace = scorer.score(
                nearby("지역 미술관", "문화,예술 > 문화시설 > 미술관", "강릉시"),
                "culture",
                500,
                2_000,
                List.of("culture"),
                List.of("culture:art"),
                null
        ).score();
        int museumPlace = scorer.score(
                nearby("지역 박물관", "문화,예술 > 문화시설 > 박물관", "강릉시"),
                "culture",
                500,
                2_000,
                List.of("culture"),
                List.of("culture:art"),
                null
        ).score();

        assertThat(artPlace).isGreaterThan(museumPlace);
    }

    @Test
    void explainsAnExplicitPreferenceMismatchInsteadOfClaimingItMatches() {
        var result = scorer.score(
                nearby("경포 바다 치킨", "음식점 > 치킨", "강릉시"),
                "restaurant",
                100,
                2_000,
                List.of("food"),
                List.of("food:japanese"),
                "couple"
        );

        assertThat(result.reasons()).noneMatch(reason -> reason.contains("식도락 취향에 맞는"));
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("다른 분류지만 함께 보여드려요"));
    }

    @Test
    void doesNotExplainAnExactMuseumAsAnExplicitMismatchBecauseItIsRelatedToExhibition() {
        var result = scorer.score(
                nearby("주문진해양박물관", "문화,예술 > 문화시설 > 박물관", "강릉시 주문진"),
                "culture",
                500,
                2_000,
                List.of("culture"),
                List.of("culture:museum"),
                "solo"
        );

        assertThat(result.reasons()).anyMatch(reason -> reason.contains("문화·예술 취향에 맞는"));
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("세부 취향과 분류가 달라"));
    }

    @Test
    void usesAddressTextWhenEvaluatingDetailedPreferenceKeywords() {
        var result = scorer.score(
                nearby("동네 식당", "음식점", "강릉시 초당순두부길"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:korean"),
                null
        );

        assertThat(result.reasons()).anyMatch(reason -> reason.contains("식도락"));
    }

    @Test
    void appliesAFranchisePenaltyToNearbyCafeRecommendations() {
        int localCafe = scorer.score(
                nearby("안목 바다 로스터리", "음식점 > 카페", "강릉시"),
                "cafe",
                500,
                2_000,
                List.of("rest"),
                List.of("rest:coffee"),
                "couple"
        ).score();
        int franchiseCafe = scorer.score(
                nearby("스타벅스 바다 카페", "음식점 > 카페", "강릉시"),
                "cafe",
                500,
                2_000,
                List.of("rest"),
                List.of("rest:coffee"),
                "couple"
        ).score();

        assertThat(localCafe - franchiseCafe).isGreaterThanOrEqualTo(8);
    }

    @Test
    void normalizesMultipleCuisineMatchesWithinTheFixedDetailWeight() {
        int bothCuisines = scorer.score(
                nearby("한식 중식 맛집", "음식점", "강릉시"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:korean", "food:chinese"),
                "solo"
        ).score();
        int oneCuisine = scorer.score(
                nearby("한식 맛집", "음식점", "강릉시"),
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of("food:korean", "food:chinese"),
                "solo"
        ).score();

        assertThat(bothCuisines).isGreaterThan(oneCuisine);
        assertThat(bothCuisines - oneCuisine).isLessThanOrEqualTo(60);
    }

    @Test
    void treatsAllCuisineDetailsAsNoSpecificCuisinePreference() {
        var place = nearby("일반 레스토랑", "음식점", "강릉시");
        List<String> allCuisines = List.of(
                "food:korean", "food:chinese", "food:japanese", "food:western"
        );

        int withoutDetails = scorer.score(
                place,
                "restaurant",
                500,
                2_000,
                List.of("food"),
                List.of(),
                "solo"
        ).score();
        int withAllDetails = scorer.score(
                place,
                "restaurant",
                500,
                2_000,
                List.of("food"),
                allCuisines,
                "solo"
        ).score();

        assertThat(withAllDetails).isEqualTo(withoutDetails);
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
