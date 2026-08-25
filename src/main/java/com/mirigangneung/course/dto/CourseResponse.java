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
        this(courseId, title, duration, stops, totalDistanceMeters, totalTravelMinutes, List.of());
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
        return from(course, stops, 0, 0, List.of());
    }

    public static CourseResponse from(
            Course course,
            List<CourseStop> stops,
            int totalDistanceMeters,
            int totalTravelMinutes,
            List<RouteSegmentResponse> routeSegments
    ) {
        return new CourseResponse(
                course.getId().toString(),
                course.getTitle(),
                course.getDurationType(),
                stops.stream().map(CourseResponse::stop).toList(),
                totalDistanceMeters,
                totalTravelMinutes,
                routeSegments
        );
    }

    private static StopResponse stop(CourseStop stop) {
        return new StopResponse(
                stop.getId() == null ? null : stop.getId().toString(),
                stop.getSequence(),
                stop.getPlaceId(),
                stop.getExternalPlaceId(),
                stop.getDisplayName(),
                stop.getThumbnailUrl(),
                stop.getArrivalTime(),
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
