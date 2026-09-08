package com.mirigangneung.course.recommendation;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calculates a deterministic score for a Kakao nearby-place candidate.
 *
 * <p>The scorer intentionally uses only fields already returned by Kakao
 * Local. It does not infer ratings, reviews, popularity, or any other
 * information that the provider does not return.</p>
 */
public final class NearbyPlaceRecommendationScorer {
    private static final int DETAIL_FIT_SCORE = 55;
    private static final int RELATED_DETAIL_FIT_SCORE = 42;
    private static final int ADDRESS_ONLY_DETAIL_FIT_SCORE = 44;
    private static final int ADDRESS_ONLY_RELATED_FIT_SCORE = 34;
    private static final int BROAD_FIT_SCORE = 32;
    private static final int ADDRESS_ONLY_BROAD_FIT_SCORE = 24;
    private static final int CATEGORY_ONLY_SCORE = 8;
    private static final int EXCLUSION_MISMATCH_SCORE = 8;
    private static final int DETAIL_NEUTRAL_SCORE = 20;
    private static final int DISTANCE_WEIGHT = 20;
    private static final int COMPANION_WEIGHT = 10;
    private static final int COMPLETENESS_WEIGHT = 10;
    private static final int FRANCHISE_PENALTY = 8;
    private static final int MAX_REASON_COUNT = 3;

    public boolean hasApplicableDetailPreference(
            String category,
            List<String> travelTypes,
            List<String> detailTypes
    ) {
        if (detailTypes == null || detailTypes.isEmpty()) {
            return false;
        }
        String normalizedCategory = normalize(category);
        return selectedTravelProfiles(travelTypes).stream()
                .filter(profile -> profile.categories().contains(normalizedCategory))
                .anyMatch(profile -> !selectedDetailPreferences(profile, detailTypes).isEmpty());
    }

    public boolean hasExactDetailPreferenceMatch(
            KakaoLocalClient.NearbyPlace place,
            String category,
            List<String> travelTypes,
            List<String> detailTypes
    ) {
        if (!hasApplicableDetailPreference(category, travelTypes, detailTypes)) {
            return false;
        }
        String normalizedCategory = normalize(category);
        SearchableText text = searchableText(place);
        return selectedTravelProfiles(travelTypes).stream()
                .filter(profile -> profile.categories().contains(normalizedCategory))
                .map(profile -> selectedDetailPreferences(profile, detailTypes))
                .flatMap(List::stream)
                .anyMatch(preference -> preference.keywords().stream().anyMatch(text::containsDetailPrimary));
    }

    private static final Map<String, TravelProfile> TRAVEL_PROFILES = Map.of(
            "food", new TravelProfile(
                    "식도락",
                    Set.of("restaurant"),
                    Set.of(
                            "맛집", "식당", "한식", "일식", "중식", "양식", "분식",
                            "고기", "해산물", "횟집", "브런치", "레스토랑", "치킨",
                            "국수", "순두부", "막국수"
                    ),
                    Map.of(
                            "food:korean", new DetailPreference(Set.of(
                                    "한식", "국밥", "순두부", "막국수", "냉면", "삼겹살",
                                    "갈비", "비빔밥", "닭갈비", "칼국수"
                            )),
                            "food:chinese", new DetailPreference(Set.of(
                            "중식", "중국집", "짜장", "짬뽕", "마라", "양꼬치", "딤섬",
                            "중국요리", "중화요리", "반점"
                            )),
                            "food:japanese", new DetailPreference(Set.of(
                                    "일식", "초밥", "스시", "사시미", "돈카츠", "돈까스",
                                    "라멘", "우동", "이자카야", "오마카세"
                            )),
                            "food:western", new DetailPreference(Set.of(
                                    "양식", "파스타", "피자", "스테이크", "레스토랑", "브런치",
                                    "샐러드", "햄버거"
                            ))
                    ),
                    Set.of(),
                    Set.of("분식", "치킨", "패스트푸드", "닭강정")
            ),
            "rest", new TravelProfile(
                    "휴식",
                    Set.of("cafe"),
                    Set.of("커피", "디저트", "베이커리", "로스터리", "다방", "브런치", "바다", "전망"),
                    Map.of(
                            "rest:coffee", new DetailPreference(Set.of(
                                    "커피", "로스터리", "로스팅", "브루잉", "에스프레소",
                                    "라떼", "핸드드립"
                            )),
                            "rest:dessert", new DetailPreference(Set.of(
                                    "디저트", "베이커리", "빵", "케이크", "마카롱", "와플",
                                    "아이스크림", "쿠키", "도넛"
                            ))
                    )
            ),
            "active", new TravelProfile(
                    "액티비티",
                    Set.of("attraction"),
                    Set.of(
                            "체험", "레저", "스포츠", "서핑", "등산", "트레킹", "자전거",
                            "액티비티", "테마파크", "오션"
                    ),
                    Map.of()
            ),
            "culture", new TravelProfile(
                    "문화·예술",
                    Set.of("culture"),
                    Set.of(
                            "문화", "전시", "박물관", "미술관", "기념관", "예술",
                            "역사", "고택", "공연", "도서관"
                    ),
                    Map.of(
                            "culture:art", new DetailPreference(Set.of(
                                    "미술관", "미술", "갤러리", "예술", "아트센터"
                            )),
                            "culture:exhibition", new DetailPreference(Set.of(
                                    "전시", "전시관", "전시장", "전시회", "미술관", "아트센터",
                                    "갤러리", "공연"
                            ), Set.of("박물관", "기념관")),
                            "culture:museum", new DetailPreference(Set.of(
                                    "박물관", "기념관", "역사관", "과학관", "민속관"
                            ), Set.of("전시", "전시관", "미술관", "갤러리"))
                    )
            ),
            "nature", new TravelProfile(
                    "자연·산책",
                    Set.of("attraction"),
                    Set.of(
                            "해변", "해수욕장", "바다", "산", "숲", "공원", "호수", "폭포",
                            "정원", "자연", "목장", "계곡", "산책", "습지"
                    ),
                    Map.of(),
                    Set.of("자전거", "인증센터", "레저", "스포츠", "테마파크", "수영장")
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

    private static final Set<String> FRANCHISE_TOKENS = Set.of(
            "메가mgc커피",
            "메가커피",
            "컴포즈커피",
            "스타벅스",
            "투썸플레이스"
    );

    /**
     * Compatibility overload for courses created before detailed preferences
     * were introduced.
     */
    public Recommendation score(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            int radiusMeters,
            List<String> travelTypes,
            String companion
    ) {
        return score(place, category, distanceMeters, radiusMeters, travelTypes, List.of(), companion);
    }

    public Recommendation score(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            int radiusMeters,
            List<String> travelTypes,
            List<String> detailTypes,
            String companion
    ) {
        List<TravelProfile> selectedProfiles = selectedTravelProfiles(travelTypes);
        CompanionProfile companionProfile = COMPANION_PROFILES.get(normalize(companion));
        if (selectedProfiles.isEmpty() && companionProfile == null) {
            return new Recommendation(null, List.of());
        }

        String normalizedCategory = normalize(category);
        SearchableText searchableText = searchableText(place);
        TravelMatch travelMatch = bestTravelMatch(
                selectedProfiles,
                detailTypes,
                normalizedCategory,
                searchableText
        );
        int distanceScore = distanceScore(distanceMeters, radiusMeters);
        int companionScore = companionScore(companionProfile, normalizedCategory, searchableText.all());
        int completenessScore = completenessScore(place);
        int franchisePenalty = "cafe".equals(normalizedCategory)
                && containsFranchise(searchableText.all())
                ? FRANCHISE_PENALTY
                : 0;
        int total = clamp(
                travelMatch.score()
                        + distanceScore
                        + companionScore
                        + completenessScore
                        - franchisePenalty,
                0,
                100
        );

        return new Recommendation(
                total,
                reasons(
                        travelMatch,
                        companionProfile,
                        normalizedCategory,
                        searchableText.all(),
                        distanceMeters,
                        radiusMeters,
                        completenessScore,
                        franchisePenalty
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

    private static TravelMatch bestTravelMatch(
            List<TravelProfile> profiles,
            List<String> detailTypes,
            String category,
            SearchableText text
    ) {
        TravelMatch best = new TravelMatch(0, null, false, false);
        for (TravelProfile profile : profiles) {
            int score = fitScore(profile, detailTypes, category, text);
            boolean explicitMismatch = hasExplicitMismatch(profile, detailTypes, text);
            boolean detailNeutral = isDetailNeutral(profile, detailTypes, category, text);
            if (score > best.score()) {
                best = new TravelMatch(score, profile, explicitMismatch, detailNeutral);
            }
        }
        return best;
    }

    private static boolean isDetailNeutral(
            TravelProfile profile,
            List<String> detailTypes,
            String category,
            SearchableText text
    ) {
        if (!profile.categories().contains(category)) {
            return false;
        }
        List<DetailPreference> selectedPreferences = selectedDetailPreferences(profile, detailTypes);
        if (selectedPreferences.isEmpty()) {
            return false;
        }
        return selectedPreferences.stream().noneMatch(preference ->
                preference.keywords().stream().anyMatch(text::containsDetailPrimary)
                        || preference.relatedKeywords().stream().anyMatch(text::containsDetailPrimary)
                        || preference.keywords().stream().anyMatch(text::containsAddress)
                        || preference.relatedKeywords().stream().anyMatch(text::containsAddress)
        );
    }

    private static int fitScore(
            TravelProfile profile,
            List<String> detailTypes,
            String category,
            SearchableText text
    ) {
        if (!profile.categories().contains(category)) {
            return 0;
        }

        if (hasExplicitMismatch(profile, detailTypes, text)
                && profile.detailPreferences().isEmpty()) {
            return EXCLUSION_MISMATCH_SCORE;
        }

        List<DetailPreference> selectedPreferences = selectedDetailPreferences(profile, detailTypes);
        if (!selectedPreferences.isEmpty()) {
            long primaryStrongMatches = selectedPreferences.stream()
                    .filter(preference -> preference.keywords().stream().anyMatch(text::containsDetailPrimary))
                    .count();
            if (primaryStrongMatches > 0) {
                return (int) Math.round(
                        DETAIL_FIT_SCORE * (double) primaryStrongMatches / selectedPreferences.size()
                );
            }

            long primaryRelatedMatches = selectedPreferences.stream()
                    .filter(preference -> preference.relatedKeywords().stream().anyMatch(text::containsDetailPrimary))
                    .count();
            if (primaryRelatedMatches > 0) {
                return (int) Math.round(
                        RELATED_DETAIL_FIT_SCORE * (double) primaryRelatedMatches / selectedPreferences.size()
                );
            }

            long addressStrongMatches = selectedPreferences.stream()
                    .filter(preference -> preference.keywords().stream().anyMatch(text::containsAddress))
                    .count();
            if (addressStrongMatches > 0) {
                return (int) Math.round(
                        ADDRESS_ONLY_DETAIL_FIT_SCORE * (double) addressStrongMatches / selectedPreferences.size()
                );
            }

            long addressRelatedMatches = selectedPreferences.stream()
                    .filter(preference -> preference.relatedKeywords().stream().anyMatch(text::containsAddress))
                    .count();
            if (addressRelatedMatches > 0) {
                return (int) Math.round(
                        ADDRESS_ONLY_RELATED_FIT_SCORE * (double) addressRelatedMatches / selectedPreferences.size()
                );
            }

            // A selected detail preference is a ranking signal, not a hard
            // filter. Keep candidates whose cuisine is unknown or different
            // in the same neutral band instead of treating them as unusable.
            return DETAIL_NEUTRAL_SCORE;
        }

        if (profile.keywords().stream().anyMatch(text::containsPrimary)) {
            return BROAD_FIT_SCORE;
        }
        if (profile.keywords().stream().anyMatch(text::containsAddress)) {
            return ADDRESS_ONLY_BROAD_FIT_SCORE;
        }
        return CATEGORY_ONLY_SCORE;
    }

    private static boolean hasExplicitMismatch(
            TravelProfile profile,
            List<String> detailTypes,
            SearchableText text
    ) {
        if (profile.exclusionKeywords().stream().anyMatch(text::containsPrimary)) {
            return true;
        }
        return !profile.detailPreferences().isEmpty()
                && !selectedDetailPreferences(profile, detailTypes).isEmpty()
                && hasExplicitDetailMismatch(profile, detailTypes, text);
    }

    private static boolean hasExplicitDetailMismatch(
            TravelProfile profile,
            List<String> detailTypes,
            SearchableText text
    ) {
        Set<String> selectedIds = selectedDetailIds(profile, detailTypes);
        if (selectedIds.isEmpty() || selectedIds.size() == profile.detailPreferences().size()) {
            return false;
        }
        List<DetailPreference> selectedPreferences = selectedDetailPreferences(profile, detailTypes);
        if (selectedPreferences.stream().anyMatch(preference ->
                preference.keywords().stream().anyMatch(text::containsDetailPrimary)
                        || preference.relatedKeywords().stream().anyMatch(text::containsDetailPrimary)
        )) {
            return false;
        }
        return profile.detailPreferences().entrySet().stream()
                .filter(entry -> !selectedIds.contains(normalize(entry.getKey())))
                .map(Map.Entry::getValue)
                .anyMatch(preference ->
                        preference.keywords().stream().anyMatch(text::containsDetailPrimary)
                                || profile.detailMismatchKeywords().stream().anyMatch(text::containsDetailPrimary)
                );
    }

    private static Set<String> selectedDetailIds(
            TravelProfile profile,
            List<String> detailTypes
    ) {
        if (detailTypes == null || detailTypes.isEmpty() || profile.detailPreferences().isEmpty()) {
            return Set.of();
        }
        Set<String> knownIds = profile.detailPreferences().keySet().stream()
                .map(NearbyPlaceRecommendationScorer::normalize)
                .collect(Collectors.toSet());
        return detailTypes.stream()
                .map(NearbyPlaceRecommendationScorer::normalize)
                .filter(knownIds::contains)
                .collect(Collectors.toSet());
    }

    private static List<DetailPreference> selectedDetailPreferences(
            TravelProfile profile,
            List<String> detailTypes
    ) {
        if (detailTypes == null || detailTypes.isEmpty() || profile.detailPreferences().isEmpty()) {
            return List.of();
        }

        Set<String> selectedIds = selectedDetailIds(profile, detailTypes);
        if (selectedIds.isEmpty() || selectedIds.size() == profile.detailPreferences().size()) {
            return List.of();
        }
        return profile.detailPreferences().entrySet().stream()
                .filter(entry -> selectedIds.contains(normalize(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static int distanceScore(int distanceMeters, int radiusMeters) {
        if (radiusMeters <= 0 || distanceMeters < 0) {
            return 0;
        }
        double ratio = Math.min(1.0, (double) distanceMeters / radiusMeters);
        return (int) Math.round(DISTANCE_WEIGHT * (1.0 - ratio));
    }

    private static int companionScore(
            CompanionProfile profile,
            String category,
            String text
    ) {
        if (profile == null || !profile.categories().contains(category)) {
            return 0;
        }
        return profile.keywords().stream().anyMatch(keyword -> text.contains(normalize(keyword)))
                ? COMPANION_WEIGHT
                : 3;
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
            TravelMatch travelMatch,
            CompanionProfile companion,
            String category,
            String text,
            int distanceMeters,
            int radiusMeters,
            int completenessScore,
            int franchisePenalty
    ) {
        List<String> reasons = new ArrayList<>();
        if (travelMatch.profile() != null
                && travelMatch.score() > 0
                && !travelMatch.explicitMismatch()
                && !travelMatch.detailNeutral()) {
            reasons.add(travelMatch.profile().label() + " 취향에 맞는 장소예요");
        }
        if (travelMatch.explicitMismatch()) {
            reasons.add("선택한 세부 취향과 다른 분류지만 함께 보여드려요");
        } else if (travelMatch.detailNeutral()) {
            reasons.add("세부 취향을 확인할 수 없어 일반 후보로 함께 보여드려요");
        }
        if (companion != null
                && companionScore(companion, category, text) > 0) {
            reasons.add(companion.label() + "과 잘 어울리는 장소예요");
        }
        if (distanceMeters >= 0 && radiusMeters > 0 && distanceMeters <= radiusMeters) {
            reasons.add("관광지에서 가까워요");
        }
        if (completenessScore >= 8) {
            reasons.add("장소 정보가 충분해요");
        }
        if (franchisePenalty > 0) {
            reasons.add("대형 프랜차이즈라 추천 점수를 일부 조정했어요");
        }
        if (reasons.isEmpty()) {
            reasons.add("선택 조건과 일치하는 정보가 없어 거리·정보 기준으로 계산했어요");
        }
        return reasons.stream().limit(MAX_REASON_COUNT).toList();
    }

    private static boolean containsFranchise(String text) {
        return FRANCHISE_TOKENS.stream().anyMatch(token -> text.contains(normalize(token)));
    }

    private static String join(String... values) {
        if (values == null) {
            return "";
        }
        return String.join(" ", Arrays.stream(values)
                .map(value -> value == null ? "" : value)
                .toList());
    }

    private static SearchableText searchableText(KakaoLocalClient.NearbyPlace place) {
        return new SearchableText(
                normalize(place.name()),
                normalize(place.categoryName()),
                normalize(categoryDetailPath(place.categoryName())),
                normalize(join(place.address(), place.roadAddress()))
        );
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

    private static String categoryDetailPath(String categoryName) {
        if (!hasText(categoryName)) {
            return "";
        }
        int separator = categoryName.indexOf('>');
        return separator >= 0 ? categoryName.substring(separator + 1) : categoryName;
    }

    public record Recommendation(Integer score, List<String> reasons) {
        public Recommendation {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private record TravelProfile(
            String label,
            Set<String> categories,
            Set<String> keywords,
            Map<String, DetailPreference> detailPreferences,
            Set<String> exclusionKeywords,
            Set<String> detailMismatchKeywords
    ) {
        private TravelProfile(
                String label,
                Set<String> categories,
                Set<String> keywords,
                Map<String, DetailPreference> detailPreferences
        ) {
            this(label, categories, keywords, detailPreferences, Set.of(), Set.of());
        }

        private TravelProfile(
                String label,
                Set<String> categories,
                Set<String> keywords,
                Map<String, DetailPreference> detailPreferences,
                Set<String> exclusionKeywords
        ) {
            this(label, categories, keywords, detailPreferences, exclusionKeywords, Set.of());
        }
    }

    private record DetailPreference(Set<String> keywords, Set<String> relatedKeywords) {
        private DetailPreference(Set<String> keywords) {
            this(keywords, Set.of());
        }
    }

    private record TravelMatch(
            int score,
            TravelProfile profile,
            boolean explicitMismatch,
            boolean detailNeutral
    ) {
    }

    private record SearchableText(String name, String categoryPath, String categoryDetailPath, String address) {
        private boolean containsPrimary(String keyword) {
            return (name + categoryPath).contains(normalize(keyword));
        }

        private boolean containsDetailPrimary(String keyword) {
            return (name + categoryDetailPath).contains(normalize(keyword));
        }

        private boolean containsAddress(String keyword) {
            return address.contains(normalize(keyword));
        }

        private String all() {
            return name + categoryPath + address;
        }
    }

    private record CompanionProfile(String label, Set<String> categories, Set<String> keywords) {
    }
}
