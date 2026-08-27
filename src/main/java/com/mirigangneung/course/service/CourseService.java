package com.mirigangneung.course.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.course.dto.ShareResponse;
import com.mirigangneung.course.recommendation.CourseRecommendationEngine;
import com.mirigangneung.course.repository.CourseRepository;
import com.mirigangneung.course.repository.CourseStopRepository;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.service.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class CourseService {
    private final CourseRepository courses;
    private final CourseStopRepository stops;
    private final PlaceService places;
    private final CourseRecommendationEngine engine;
    private final CourseRouteCalculator routeCalculator;

    public CourseService(
            CourseRepository courses,
            CourseStopRepository stops,
            PlaceService places,
            CourseRecommendationEngine engine,
            CourseRouteCalculator routeCalculator
    ) {
        this.courses = courses;
        this.stops = stops;
        this.places = places;
        this.engine = engine;
        this.routeCalculator = routeCalculator;
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        validateDuration(request);
        List<Place> selected = request.placeIds().stream().map(places::find).toList();
        Place onePick = places.find(request.onePickId());
        if (selected.stream().noneMatch(place -> place.getId().equals(onePick.getId()))) {
            throw new ApiException(
                    "INVALID_REQUEST",
                    HttpStatus.BAD_REQUEST,
                    "onePickId는 placeIds에 포함되어야 합니다."
            );
        }

        Course course = courses.save(new Course(
                request.duration(),
                request.startDate(),
                request.endDate(),
                request.types(),
                request.companion()
        ));
        List<Place> recommended = engine.recommend(
                selected,
                onePick,
                request.types(),
                request.companion(),
                request.duration()
        );
        int sequence = 1;
        for (Place place : recommended) {
            stops.save(new CourseStop(course, place, sequence++, place.getId().equals(onePick.getId())));
        }
        return response(course);
    }

    @Transactional(readOnly = true)
    public CourseResponse get(String id) {
        return response(find(id));
    }

    @Transactional
    public void delete(String id) {
        courses.delete(find(id));
    }

    @Transactional
    public ShareResponse share(String id) {
        Course course = find(id);
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        course.share(hash(token), OffsetDateTime.now().plusDays(7));
        return new ShareResponse(token, "/share/courses/" + token, course.getShareExpiresAt());
    }

    @Transactional(readOnly = true)
    public CourseResponse shared(String token) {
        Course course = courses.findByShareTokenHash(hash(token))
                .orElseThrow(this::notFound);
        if (course.getShareExpiresAt() == null || course.getShareExpiresAt().isBefore(OffsetDateTime.now())) {
            throw notFound();
        }
        return response(course);
    }

    @Transactional
    public void revoke(String id) {
        find(id).revokeShare();
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

    private Course find(String id) {
        try {
            return courses.findById(UUID.fromString(id)).orElseThrow(this::notFound);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private void validateDuration(CreateCourseRequest request) {
        if ("custom".equals(request.duration())
                && (request.startDate() == null
                || request.endDate() == null
                || request.endDate().isBefore(request.startDate()))) {
            throw new ApiException(
                    "INVALID_REQUEST",
                    HttpStatus.BAD_REQUEST,
                    "custom 기간이 올바르지 않습니다."
            );
        }
    }

    private ApiException notFound() {
        return new ApiException("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
