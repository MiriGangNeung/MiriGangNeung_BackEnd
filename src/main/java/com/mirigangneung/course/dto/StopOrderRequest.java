package com.mirigangneung.course.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StopOrderRequest(@NotEmpty List<String> stopIds) {
}
