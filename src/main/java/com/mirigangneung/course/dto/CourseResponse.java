package com.mirigangneung.course.dto;

import com.mirigangneung.course.domain.Course;
import com.mirigangneung.course.domain.CourseStop;

import java.util.List;

public record CourseResponse(
        String courseId,
        String title,
        String duration,
        List<StopResponse> stops,
        int totalDistanceMeters,
        int totalTravelMinutes,
        String routeStatus,
        List<RouteSegmentResponse> routeSegments
) {
    public CourseResponse(
            String courseId,
            String title,
            String duration,
            List<StopResponse> stops,
            int totalDistanceMeters,
            int totalTravelMinutes
    ) {
        this(courseId, title, duration, stops, totalDistanceMeters, totalTravelMinutes, "UNAVAILABLE", List.of());
    }

    public CourseResponse(
            String courseId,
            String title,
            String duration,
            List<StopResponse> stops,
            int totalDistanceMeters,
            int totalTravelMinutes,
            List<RouteSegmentResponse> routeSegments
    ) {
        this(
                courseId,
                title,
                duration,
                stops,
                totalDistanceMeters,
                totalTravelMinutes,
                routeSegments.isEmpty() ? "UNAVAILABLE" : "READY",
                routeSegments
        );
    }

    public record StopResponse(
            String stopId,
            int sequence,
            String placeId,
            String externalPlaceId,
            String name,
            String thumbnailUrl,
            String arrivalTime,
            int stayMinutes,
            String crowdLevel,
            boolean isOnePick,
            String note,
            Double latitude,
            Double longitude,
            boolean external,
            String category,
            String categoryName,
            String address,
            String phone,
            String placeUrl
    ) {
        /** Compatibility constructor for the original tourism-only response shape. */
        public StopResponse(
                int sequence,
                String placeId,
                String name,
                String thumbnailUrl,
                String arrivalTime,
                int stayMinutes,
                String crowdLevel,
                boolean isOnePick,
                String note,
                Double latitude,
                Double longitude
        ) {
            this(
                    null,
                    sequence,
                    placeId,
                    null,
                    name,
                    thumbnailUrl,
                    arrivalTime,
                    stayMinutes,
                    crowdLevel,
                    isOnePick,
                    note,
                    latitude,
                    longitude,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record RouteSegmentResponse(
            String fromStopId,
            String toStopId,
            int distanceMeters,
            int durationSeconds,
            List<List<Double>> polyline
    ) {
    }

    public static CourseResponse from(Course course, List<CourseStop> stops) {
        return from(course, stops, 0, 0, "UNAVAILABLE", List.of(),
                stops.stream().map(CourseStop::getArrivalTime).toList());
    }

    public static CourseResponse from(
            Course course,
            List<CourseStop> stops,
            int totalDistanceMeters,
            int totalTravelMinutes,
            List<RouteSegmentResponse> routeSegments
    ) {
        return from(
                course,
                stops,
                totalDistanceMeters,
                totalTravelMinutes,
                routeSegments.isEmpty() ? "UNAVAILABLE" : "READY",
                routeSegments,
                stops.stream().map(CourseStop::getArrivalTime).toList()
        );
    }

    public static CourseResponse from(
            Course course,
            List<CourseStop> stops,
            int totalDistanceMeters,
            int totalTravelMinutes,
            String routeStatus,
            List<RouteSegmentResponse> routeSegments,
            List<String> arrivalTimes
    ) {
        return new CourseResponse(
                course.getId().toString(),
                course.getTitle(),
                course.getDurationType(),
                stops.stream().map((stop) -> stop(stop, arrivalTimes)).toList(),
                totalDistanceMeters,
                totalTravelMinutes,
                routeStatus,
                routeSegments
        );
    }

    private static StopResponse stop(CourseStop stop) {
        return stop(stop, stop.getArrivalTime());
    }

    private static StopResponse stop(CourseStop stop, List<String> arrivalTimes) {
        int index = Math.max(0, stop.getSequence() - 1);
        String arrivalTime = index < arrivalTimes.size() ? arrivalTimes.get(index) : stop.getArrivalTime();
        return stop(stop, arrivalTime);
    }

    private static StopResponse stop(CourseStop stop, String arrivalTime) {
        return new StopResponse(
                stop.getId() == null ? null : stop.getId().toString(),
                stop.getSequence(),
                stop.getPlaceId(),
                stop.getExternalPlaceId(),
                stop.getDisplayName(),
                stop.getThumbnailUrl(),
                arrivalTime,
                stop.getStayMinutes(),
                stop.getCrowdLevel(),
                stop.isOnePick(),
                stop.getNote(),
                stop.getLatitude(),
                stop.getLongitude(),
                stop.isExternal(),
                stop.getCategory(),
                stop.getCategoryName(),
                stop.getAddress(),
                stop.getPhone(),
                stop.getPlaceUrl()
        );
    }
}
