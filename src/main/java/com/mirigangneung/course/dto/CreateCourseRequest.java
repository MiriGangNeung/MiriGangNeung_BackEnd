package com.mirigangneung.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateCourseRequest(
        @NotEmpty @Size(max = 20) List<String> placeIds,
        @NotBlank String onePickId,
        @NotEmpty @Size(max = 4) List<String> types,
        @Size(max = 9) List<@NotBlank @Size(max = 32) String> detailTypes,
        @NotBlank String companion,
        @NotBlank String duration,
        LocalDate startDate,
        LocalDate endDate
) {
    public CreateCourseRequest(
            List<String> placeIds,
            String onePickId,
            List<String> types,
            String companion,
            String duration,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this(placeIds, onePickId, types, List.of(), companion, duration, startDate, endDate);
    }
}
