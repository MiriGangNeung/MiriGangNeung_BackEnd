package com.mirigangneung.course.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddExternalStopRequest(
        @NotBlank String externalPlaceId,
        @NotBlank String name,
        @NotBlank String category,
        String categoryName,
        String address,
        String roadAddress,
        String phone,
        String placeUrl,
        @NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double longitude,
        @NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double latitude
) {
}
