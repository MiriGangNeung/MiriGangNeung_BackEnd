package com.mirigangneung.course.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCourseRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsThreeTravelTypes() {
        CreateCourseRequest request = new CreateCourseRequest(
                List.of("place-1"),
                "place-1",
                List.of("food", "rest", "culture"),
                "couple",
                "day",
                null,
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsFrontendPreferencePayloadWithDetailTypes() throws Exception {
        String payload = """
                {
                  "placeIds": ["place-1"],
                  "onePickId": "place-1",
                  "types": ["food", "rest", "culture"],
                  "detailTypes": ["food:korean", "rest:coffee"],
                  "companion": "couple",
                  "duration": "day"
                }
                """;

        CreateCourseRequest request = new ObjectMapper().readValue(payload, CreateCourseRequest.class);

        assertThat(request.types()).containsExactly("food", "rest", "culture");
        assertThat(request.detailTypes()).containsExactly("food:korean", "rest:coffee");
    }
}
