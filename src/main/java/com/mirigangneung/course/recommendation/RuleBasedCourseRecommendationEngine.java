package com.mirigangneung.course.recommendation;

import com.mirigangneung.place.domain.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class RuleBasedCourseRecommendationEngine implements CourseRecommendationEngine {
    private final CoursePreferenceScorer preferenceScorer;

    public RuleBasedCourseRecommendationEngine() {
        this(new CoursePreferenceScorer());
    }

    RuleBasedCourseRecommendationEngine(CoursePreferenceScorer preferenceScorer) {
        this.preferenceScorer = preferenceScorer;
    }

    @Override
    public List<Place> recommend(
            List<Place> candidates,
            Place onePick,
            List<String> types,
            String companion,
            String duration
    ) {
        List<Place> result = new ArrayList<>();
        result.add(onePick);

        int additionalPlaceLimit = "night1".equalsIgnoreCase(duration) ? 4 : 3;
        candidates.stream()
                .filter(place -> place != null && !samePlace(place, onePick))
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .sorted(Comparator
                        .comparingInt((Place place) -> preferenceScorer.score(place, types, companion))
                        .reversed()
                        .thenComparingDouble(place -> distance(onePick, place)))
                .limit(additionalPlaceLimit)
                .forEach(result::add);
        return result;
    }

    private boolean samePlace(Place first, Place second) {
        return first == second
                || (first.getId() != null && first.getId().equals(second.getId()))
                || first.getTourContentId().equals(second.getTourContentId());
    }

    private double distance(Place first, Place second) {
        double latitude = first.getLatitude() - second.getLatitude();
        double longitude = first.getLongitude() - second.getLongitude();
        return latitude * latitude + longitude * longitude;
    }
}
