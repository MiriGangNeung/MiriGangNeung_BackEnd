package com.mirigangneung.course.recommendation;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calculates a deterministic score for a Kakao nearby-place candidate.
 *
 * <p>The scorer intentionally uses only fields already returned by Kakao
 * Local. It does not infer ratings, reviews, popularity, or any other
 * information that the provider does not return.</p>
 */
public final class NearbyPlaceRecommendationScorer {
    private static final int DISTANCE_WEIGHT = 40;
    private static final int TRAVEL_TYPE_WEIGHT = 30;
    private static final int COMPANION_WEIGHT = 20;
    private static final int COMPLETENESS_WEIGHT = 10;
    private static final int MAX_REASON_COUNT = 3;

    private static final Map<String, TravelProfile> TRAVEL_PROFILES = Map.of(
            "food", new TravelProfile(
                    "식도락",
                    Set.of("restaurant"),
                    Set.of("맛집", "식당", "한식", "분식", "고기", "해산물", "횟집", "브런치", "레스토랑", "치킨", "국수")
            ),
            "rest", new TravelProfile(
                    "휴식",
                    Set.of("cafe"),
                    Set.of("바다", "해변", "전망", "산책", "정원", "공원", "힐링", "조용", "감성", "휴식")
            ),
            "active", new TravelProfile(
                    "액티비티",
                    Set.of("attraction"),
                    Set.of("체험", "레저", "스포츠", "서핑", "등산", "트레킹", "자전거", "액티비티", "테마파크", "오션")
            ),
            "culture", new TravelProfile(
                    "문화·예술",
                    Set.of("culture"),
                    Set.of("문화", "전시", "박물관", "미술관", "기념관", "예술", "역사", "고택", "공연", "도서관")
            ),
            "nature", new TravelProfile(
                    "자연·산책",
                    Set.of("attraction"),
                    Set.of("해변", "바다", "산", "숲", "공원", "호수", "폭포", "정원", "자연", "목장", "계곡")
            )
    );

    private static final Map<String, CompanionProfile> COMPANION_PROFILES = Map.of(
            "family", new CompanionProfile(
                    "가족",
                    Set.of("attraction", "culture", "restaurant"),
                    Set.of("공원", "산책", "목장", "체험", "키즈", "가족", "박물관")
            ),
            "couple", new CompanionProfile(
                    "커플",
                    Set.of("cafe", "restaurant", "attraction"),
                    Set.of("바다", "해변", "전망", "감성", "사진", "데이트", "브런치", "경포", "안목")
            ),
            "solo", new CompanionProfile(
                    "혼자",
                    Set.of("cafe", "culture"),
                    Set.of("조용", "산책", "서점", "공원", "독서", "전시")
            ),
            "friends", new CompanionProfile(
                    "친구",
                    Set.of("restaurant", "cafe", "attraction"),
                    Set.of("체험", "레저", "스포츠", "맛집", "사진", "게임", "술집")
            )
    );

    public Recommendation score(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            int radiusMeters,
            List<String> travelTypes,
            String companion
    ) {
        List<TravelProfile> selectedProfiles = selectedTravelProfiles(travelTypes);
        CompanionProfile companionProfile = COMPANION_PROFILES.get(normalize(companion));
        if (selectedProfiles.isEmpty() && companionProfile == null) {
            return new Recommendation(null, List.of());
        }

        String normalizedCategory = normalize(category);
        String searchableText = normalize(join(place.name(), place.categoryName()));
        int distanceScore = distanceScore(distanceMeters, radiusMeters);
        int travelTypeScore = travelTypeScore(selectedProfiles, normalizedCategory, searchableText);
        int companionScore = companionScore(companionProfile, normalizedCategory, searchableText);
        int completenessScore = completenessScore(place);
        int total = clamp(
                distanceScore + travelTypeScore + companionScore + completenessScore,
                0,
                100
        );

        return new Recommendation(
                total,
                reasons(
                        selectedProfiles,
                        normalizedCategory,
                        searchableText,
                        companionProfile,
                        distanceMeters,
                        radiusMeters,
                        completenessScore
                )
        );
    }

    private static List<TravelProfile> selectedTravelProfiles(List<String> travelTypes) {
        if (travelTypes == null) {
            return List.of();
        }
        return travelTypes.stream()
                .map(type -> TRAVEL_PROFILES.get(normalize(type)))
                .filter(profile -> profile != null)
                .distinct()
                .toList();
    }

    private static int distanceScore(int distanceMeters, int radiusMeters) {
        if (radiusMeters <= 0 || distanceMeters < 0) {
            return 0;
        }
        double ratio = Math.min(1.0, (double) distanceMeters / radiusMeters);
        return (int) Math.round(DISTANCE_WEIGHT * (1.0 - ratio));
    }

    private static int travelTypeScore(
            List<TravelProfile> profiles,
            String category,
            String text
    ) {
        if (profiles.isEmpty()) {
            return 0;
        }
        double averageFit = profiles.stream()
                .mapToInt(profile -> fitScore(profile.categories(), profile.keywords(), category, text))
                .average()
                .orElse(0);
        return scale(averageFit, TRAVEL_TYPE_WEIGHT);
    }

    private static int companionScore(
            CompanionProfile profile,
            String category,
            String text
    ) {
        if (profile == null) {
            return 0;
        }
        return scale(
                fitScore(profile.categories(), profile.keywords(), category, text),
                COMPANION_WEIGHT
        );
    }

    private static int fitScore(Set<String> categories, Set<String> keywords, String category, String text) {
        boolean categoryMatch = categories.contains(category);
        boolean keywordMatch = keywords.stream().anyMatch(text::contains);
        if (categoryMatch && keywordMatch) {
            return 100;
        }
        if (categoryMatch) {
            return 50;
        }
        return keywordMatch ? 75 : 0;
    }

    private static int completenessScore(KakaoLocalClient.NearbyPlace place) {
        int score = 0;
        if (hasText(place.name())) score += 3;
        if (hasText(place.categoryName()) || hasText(place.categoryCode())) score += 2;
        if (hasText(place.address()) || hasText(place.roadAddress())) score += 2;
        if (hasText(place.placeUrl())) score += 2;
        if (Double.isFinite(place.latitude()) && Double.isFinite(place.longitude())) score += 1;
        return score;
    }

    private static List<String> reasons(
            List<TravelProfile> selectedProfiles,
            String category,
            String text,
            CompanionProfile companion,
            int distanceMeters,
            int radiusMeters,
            int completenessScore
    ) {
        List<String> reasons = new ArrayList<>();
        for (TravelProfile profile : selectedProfiles) {
            if (fitScore(profile.categories(), profile.keywords(), category, text) > 0) {
                reasons.add(profile.label() + " 취향에 맞는 장소예요");
            }
        }
        if (companion != null
                && fitScore(companion.categories(), companion.keywords(), category, text) > 0) {
            reasons.add(companion.label() + "과 잘 어울리는 장소예요");
        }
        if (distanceMeters >= 0 && radiusMeters > 0 && distanceMeters <= radiusMeters) {
            reasons.add("관광지에서 가까워요");
        }
        if (completenessScore >= 8) {
            reasons.add("장소 정보가 충분해요");
        }
        if (reasons.isEmpty()) {
            reasons.add("선택 조건과 일치하는 정보가 없어 거리·정보 기준으로 계산했어요");
        }
        return reasons.stream().limit(MAX_REASON_COUNT).toList();
    }

    private static int scale(double fit, int weight) {
        return (int) Math.round(fit * weight / 100.0);
    }

    private static String join(String first, String second) {
        return (first == null ? "" : first) + " " + (second == null ? "" : second);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    public record Recommendation(Integer score, List<String> reasons) {
        public Recommendation {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private record TravelProfile(String label, Set<String> categories, Set<String> keywords) {
    }

    private record CompanionProfile(String label, Set<String> categories, Set<String> keywords) {
    }
}
