package com.mirigangneung.course.service;

import com.mirigangneung.course.domain.CourseStop;
import com.mirigangneung.course.dto.CourseResponse;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Calculates display-only arrival times from the current stop order and route segments. */
public class CourseScheduleCalculator {
    private static final LocalTime START_TIME = LocalTime.of(9, 0);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public List<String> calculate(
            List<CourseStop> stops,
            List<CourseResponse.RouteSegmentResponse> segments
    ) {
        Map<String, CourseResponse.RouteSegmentResponse> segmentsByFromStop = segments.stream()
                .filter(segment -> segment.fromStopId() != null)
                .collect(Collectors.toMap(
                        CourseResponse.RouteSegmentResponse::fromStopId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        LocalTime current = START_TIME;
        java.util.ArrayList<String> arrivalTimes = new java.util.ArrayList<>(stops.size());
        for (int index = 0; index < stops.size(); index++) {
            arrivalTimes.add(current.format(FORMATTER));
            if (index == stops.size() - 1) {
                continue;
            }

            CourseStop stop = stops.get(index);
            int travelMinutes = 0;
            String stopId = stop.getId() == null ? null : stop.getId().toString();
            CourseResponse.RouteSegmentResponse segment = segmentsByFromStop.get(stopId);
            if (segment != null) {
                travelMinutes = (Math.max(segment.durationSeconds(), 0) + 59) / 60;
            }
            current = current.plusMinutes(Math.max(stop.getStayMinutes(), 0) + travelMinutes);
        }
        return List.copyOf(arrivalTimes);
    }
}
