package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.infrastructure.kakao.KakaoRouteClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CourseRouteCalculator {
    private final KakaoRouteClient routeClient;

    public CourseRouteCalculator(KakaoRouteClient routeClient) {
        this.routeClient = routeClient;
    }

    public Result calculate(List<CourseStop> stops) {
        if (stops.size() < 2) {
            return new Result("READY", 0, 0, List.of());
        }

        int totalDistance = 0;
        int totalDurationSeconds = 0;
        List<CourseResponse.RouteSegmentResponse> segments = new ArrayList<>();

        for (int index = 1; index < stops.size(); index++) {
            CourseStop from = stops.get(index - 1);
            CourseStop to = stops.get(index);
            if (hasNoCoordinates(from) || hasNoCoordinates(to)) {
                return unavailable();
            }

            KakaoRouteClient.RouteResult route;
            try {
                route = routeClient.walking(
                        from.getLatitude(),
                        from.getLongitude(),
                        to.getLatitude(),
                        to.getLongitude()
                );
            } catch (RuntimeException exception) {
                return unavailable();
            }

            if (route.distanceMeters() <= 0 && route.durationSeconds() <= 0 && route.polyline().isEmpty()) {
                return unavailable();
            }

            totalDistance += Math.max(route.distanceMeters(), 0);
            totalDurationSeconds += Math.max(route.durationSeconds(), 0);
            segments.add(new CourseResponse.RouteSegmentResponse(
                    stopId(from),
                    stopId(to),
                    Math.max(route.distanceMeters(), 0),
                    Math.max(route.durationSeconds(), 0),
                    route.polyline()
            ));
        }

        return new Result(
                "READY",
                totalDistance,
                (totalDurationSeconds + 59) / 60,
                List.copyOf(segments)
        );
    }

    private static boolean hasNoCoordinates(CourseStop stop) {
        return stop.getLatitude() == null || stop.getLongitude() == null;
    }

    private static String stopId(CourseStop stop) {
        return stop.getId() == null ? null : stop.getId().toString();
    }

    private static Result unavailable() {
        return new Result("UNAVAILABLE", 0, 0, List.of());
    }

    public record Result(
            String status,
            int totalDistanceMeters,
            int totalTravelMinutes,
            List<CourseResponse.RouteSegmentResponse> segments
    ) {
    }
}
