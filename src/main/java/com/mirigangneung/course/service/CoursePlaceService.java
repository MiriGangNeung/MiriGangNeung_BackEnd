package com.mirigangneung.course.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseExternalPlace;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.AddExternalStopRequest;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.dto.NearbyPlaceResponse;
import com.mirigangneung.course.dto.NearbyPlacesResponse;
import com.mirigangneung.course.dto.StopOrderRequest;
import com.mirigangneung.course.recommendation.NearbyPlaceRecommendationScorer;
import com.mirigangneung.course.repository.CourseExternalPlaceRepository;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;
import com.mirigangneung.infrastructure.kakao.KakaoLocalProperties;
import com.mirigangneung.place.service.PlaceNameNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Service
public class CoursePlaceService {
    private static final int MAX_KAKAO_PAGES = 3;
    private static final Map<String, String> KAKAO_CATEGORY_CODES = Map.of(
            "restaurant", "FD6",
            "cafe", "CE7",
            "attraction", "AT4",
            "culture", "CT1"
    );

    private final CourseRepository courses;
    private final CourseStopRepository stops;
    private final CourseExternalPlaceRepository externalPlaces;
    private final KakaoLocalClient localClient;
    private final CourseRouteCalculator routeCalculator;
    private final KakaoLocalProperties localProperties;
    private final NearbyPlaceRecommendationScorer recommendationScorer;

    public CoursePlaceService(
            CourseRepository courses,
            CourseStopRepository stops,
            CourseExternalPlaceRepository externalPlaces,
            KakaoLocalClient localClient,
            CourseRouteCalculator routeCalculator,
            KakaoLocalProperties localProperties
    ) {
        this.courses = courses;
        this.stops = stops;
        this.externalPlaces = externalPlaces;
        this.localClient = localClient;
        this.routeCalculator = routeCalculator;
        this.localProperties = localProperties;
        this.recommendationScorer = new NearbyPlaceRecommendationScorer();
    }

    @Transactional(readOnly = true)
    public NearbyPlacesResponse nearby(String courseId, String category) {
        return nearby(courseId, category, null, "recommended");
    }

    @Transactional(readOnly = true)
    public NearbyPlacesResponse nearby(String courseId, String category, String stopId) {
        return nearby(courseId, category, stopId, "recommended");
    }

    @Transactional(readOnly = true)
    public NearbyPlacesResponse nearby(String courseId, String category, String stopId, String sort) {
        Course course = findCourse(courseId);
        String normalizedCategory = normalizeCategory(category);
        String normalizedSort = normalizeSort(sort);
        String categoryCode = categoryCode(normalizedCategory);
        int radiusMeters = localProperties.radiusMeters();
        int pageSize = Math.max(1, Math.min(localProperties.pageSize(), 15));
        Set<String> existingTourismNames = tourismStops(course, null).stream()
                .map(CourseStop::getDisplayName)
                .map(PlaceNameNormalizer::normalize)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        Map<String, NearbyCandidate> merged = new LinkedHashMap<>();
        for (CourseStop tourismStop : tourismStops(course, stopId)) {
            for (int page = 0; page < MAX_KAKAO_PAGES; page++) {
                List<KakaoLocalClient.NearbyPlace> pageResults = localClient.searchByCategory(
                        tourismStop.getLongitude(),
                        tourismStop.getLatitude(),
                        categoryCode,
                        radiusMeters,
                        page,
                        pageSize
                );
                for (KakaoLocalClient.NearbyPlace place : pageResults) {
                    if (isExistingTourismPlace(normalizedCategory, place, existingTourismNames)) {
                        continue;
                    }
                    int distance = distanceMeters(
                            tourismStop.getLatitude(),
                            tourismStop.getLongitude(),
                            place.latitude(),
                            place.longitude()
                    );
                    NearbyCandidate candidate = new NearbyCandidate(place, tourismStop, distance);
                    NearbyCandidate previous = merged.get(place.externalPlaceId());
                    if (previous == null || candidate.distanceMeters() < previous.distanceMeters()) {
                        merged.put(place.externalPlaceId(), candidate);
                    }
                }
                if (pageResults.size() < pageSize) {
                    break;
                }
            }
        }

        List<ScoredNearbyCandidate> scored = merged.values().stream()
                .map(candidate -> new ScoredNearbyCandidate(
                        candidate,
                        recommendationScorer.score(
                                candidate.place(),
                                normalizedCategory,
                                candidate.distanceMeters(),
                                radiusMeters,
                                course.getTravelTypes(),
                                course.getCompanion()
                        )
                ))
                .sorted(comparatorFor(normalizedSort))
                .toList();

        List<NearbyPlaceResponse> response = scored.stream()
                .map(candidate -> NearbyPlaceResponse.from(
                        candidate.candidate().place(),
                        normalizedCategory,
                        candidate.candidate().distanceMeters(),
                        stopId(candidate.candidate().stop()),
                        candidate.candidate().stop().getDisplayName(),
                        candidate.recommendation().score(),
                        candidate.recommendation().reasons()
                ))
                .toList();
        return new NearbyPlacesResponse(normalizedCategory, response);
    }

    @Transactional
    public CourseResponse addExternalStop(String courseId, AddExternalStopRequest request) {
        Course course = findCourse(courseId);
        String category = normalizeCategory(request.category());
        if (isTourismCategory(category) && existingTourismNames(course).contains(
                PlaceNameNormalizer.normalize(request.name()))) {
            throw new ApiException(
                    "PLACE_ALREADY_IN_COURSE",
                    HttpStatus.CONFLICT,
                    "이미 코스에 추가된 장소입니다."
            );
        }
        if (stops.existsByCourseAndExternalPlace_ExternalPlaceId(course, request.externalPlaceId())) {
            throw new ApiException(
                    "PLACE_ALREADY_IN_COURSE",
                    HttpStatus.CONFLICT,
                    "이미 코스에 추가된 장소입니다."
            );
        }

        CourseExternalPlace snapshot = externalPlaces.save(new CourseExternalPlace(
                "KAKAO",
                request.externalPlaceId().trim(),
                request.name().trim(),
                trimToEmpty(request.categoryName()),
                category,
                trimToEmpty(request.address()),
                trimToEmpty(request.roadAddress()),
                trimToEmpty(request.phone()),
                trimToEmpty(request.placeUrl()),
                request.latitude(),
                request.longitude()
        ));
        List<CourseStop> currentStops = stops.findByCourseOrderBySequenceAsc(course);
        stops.save(new CourseStop(course, snapshot, currentStops.size() + 1, false));
        return response(course);
    }

    @Transactional
    public CourseResponse deleteStop(String courseId, String stopId) {
        Course course = findCourse(courseId);
        CourseStop stop = findStop(course, stopId);
        if (stop.isOnePick()) {
            throw new ApiException(
                    "ONE_PICK_CANNOT_BE_DELETED",
                    HttpStatus.BAD_REQUEST,
                    "원픽 장소는 삭제할 수 없습니다."
            );
        }

        CourseExternalPlace externalPlace = stop.getExternalPlace();
        stops.delete(stop);
        stops.flush();
        if (externalPlace != null) {
            externalPlaces.delete(externalPlace);
        }
        compactSequences(course);
        return response(course);
    }

    @Transactional
    public CourseResponse reorderStops(String courseId, StopOrderRequest request) {
        Course course = findCourse(courseId);
        List<CourseStop> currentStops = stops.findByCourseOrderBySequenceAsc(course);
        Map<UUID, CourseStop> byId = new LinkedHashMap<>();
        currentStops.forEach(stop -> byId.put(stop.getId(), stop));

        List<UUID> requestedIds = parseStopIds(request.stopIds());
        if (requestedIds.size() != byId.size()
                || requestedIds.stream().distinct().count() != requestedIds.size()
                || !byId.keySet().equals(new java.util.HashSet<>(requestedIds))) {
            throw new ApiException(
                    "INVALID_STOP_ORDER",
                    HttpStatus.BAD_REQUEST,
                    "코스의 모든 장소를 한 번씩 포함해야 합니다."
            );
        }

        List<CourseStop> reordered = new ArrayList<>();
        for (int index = 0; index < requestedIds.size(); index++) {
            CourseStop stop = byId.get(requestedIds.get(index));
            stop.changeSequence(index + 1);
            reordered.add(stop);
        }
        stops.saveAll(reordered);
        return response(course);
    }

    private void compactSequences(Course course) {
        List<CourseStop> remaining = stops.findByCourseOrderBySequenceAsc(course);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).changeSequence(index + 1);
        }
        stops.saveAll(remaining);
    }

    private CourseResponse response(Course course) {
        List<CourseStop> courseStops = stops.findByCourseOrderBySequenceAsc(course);
        CourseRouteCalculator.Result route = routeCalculator.calculate(courseStops);
        return CourseResponse.from(
                course,
                courseStops,
                route.totalDistanceMeters(),
                route.totalTravelMinutes(),
                route.status(),
                route.segments()
        );
    }

    private CourseStop findStop(Course course, String stopId) {
        try {
            return stops.findByCourseAndId(course, UUID.fromString(stopId))
                    .orElseThrow(this::stopNotFound);
        } catch (IllegalArgumentException exception) {
            throw stopNotFound();
        }
    }

    private Course findCourse(String courseId) {
        try {
            return courses.findById(UUID.fromString(courseId)).orElseThrow(this::courseNotFound);
        } catch (IllegalArgumentException exception) {
            throw courseNotFound();
        }
    }

    private List<CourseStop> tourismStops(Course course, String selectedStopId) {
        List<CourseStop> tourismStops = stops.findByCourseOrderBySequenceAsc(course).stream()
                .filter(stop -> stop.getPlace() != null)
                .filter(stop -> stop.getLatitude() != null && stop.getLongitude() != null)
                .toList();
        if (selectedStopId == null || selectedStopId.isBlank()) {
            return tourismStops;
        }
        String normalizedStopId = selectedStopId.trim();
        return tourismStops.stream()
                .filter(stop -> normalizedStopId.equals(stopId(stop)))
                .findFirst()
                .map(List::of)
                .orElseThrow(this::stopNotFound);
    }

    private Set<String> existingTourismNames(Course course) {
        return tourismStops(course, null).stream()
                .map(CourseStop::getDisplayName)
                .map(PlaceNameNormalizer::normalize)
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static boolean isExistingTourismPlace(
            String category,
            KakaoLocalClient.NearbyPlace place,
            Set<String> existingTourismNames) {
        return isTourismCategory(category)
                && existingTourismNames.contains(PlaceNameNormalizer.normalize(place.name()));
    }

    private static boolean isTourismCategory(String category) {
        return "attraction".equals(category) || "culture".equals(category);
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase();
        if (!KAKAO_CATEGORY_CODES.containsKey(normalized)) {
            throw new ApiException(
                    "INVALID_CATEGORY",
                    HttpStatus.BAD_REQUEST,
                    "category는 restaurant, cafe, attraction 또는 culture여야 합니다."
            );
        }
        return normalized;
    }

    private static String categoryCode(String category) {
        return KAKAO_CATEGORY_CODES.get(category);
    }

    private static Comparator<ScoredNearbyCandidate> comparatorFor(String sort) {
        if ("distance".equals(sort)) {
            return Comparator.comparingInt((ScoredNearbyCandidate candidate) -> candidate.candidate().distanceMeters())
                    .thenComparing(candidate -> candidate.candidate().place().name());
        }
        return (first, second) -> {
            Integer firstScore = first.recommendation().score();
            Integer secondScore = second.recommendation().score();
            if (firstScore == null && secondScore != null) return 1;
            if (firstScore != null && secondScore == null) return -1;
            if (firstScore != null && secondScore != null) {
                int scoreComparison = Integer.compare(secondScore, firstScore);
                if (scoreComparison != 0) return scoreComparison;
            }
            int distanceComparison = Integer.compare(
                    first.candidate().distanceMeters(),
                    second.candidate().distanceMeters()
            );
            if (distanceComparison != 0) return distanceComparison;
            return first.candidate().place().name().compareTo(second.candidate().place().name());
        };
    }

    private static String normalizeSort(String sort) {
        String normalized = sort == null || sort.isBlank()
                ? "recommended"
                : sort.trim().toLowerCase(Locale.ROOT);
        if (!"recommended".equals(normalized) && !"distance".equals(normalized)) {
            throw new ApiException(
                    "INVALID_SORT",
                    HttpStatus.BAD_REQUEST,
                    "sort는 recommended 또는 distance여야 합니다."
            );
        }
        return normalized;
    }

    private static int distanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadius = 6_371_000;
        double latitudeDistance = Math.toRadians(latitude2 - latitude1);
        double longitudeDistance = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return (int) Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static String stopId(CourseStop stop) {
        return stop.getId() == null ? null : stop.getId().toString();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<UUID> parseStopIds(List<String> stopIds) {
        try {
            return stopIds.stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    "INVALID_STOP_ORDER",
                    HttpStatus.BAD_REQUEST,
                    "stopIds 형식이 올바르지 않습니다."
            );
        }
    }

    private ApiException courseNotFound() {
        return new ApiException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.");
    }

    private ApiException stopNotFound() {
        return new ApiException("COURSE_STOP_NOT_FOUND", HttpStatus.NOT_FOUND, "코스 장소를 찾을 수 없습니다.");
    }

    private record NearbyCandidate(
            KakaoLocalClient.NearbyPlace place,
            CourseStop stop,
            int distanceMeters
    ) {
    }

    private record ScoredNearbyCandidate(
            NearbyCandidate candidate,
            NearbyPlaceRecommendationScorer.Recommendation recommendation
    ) {
    }
}
