package com.mirigangneung.course.dto; import jakarta.validation.constraints.*; import java.time.*; import java.util.*;
public record CreateCourseRequest(@NotEmpty @Size(max=20)List<String> placeIds,@NotBlank String onePickId,@NotEmpty @Size(max=2)List<String> types,@NotBlank String companion,@NotBlank String duration,LocalDate startDate,LocalDate endDate){}
