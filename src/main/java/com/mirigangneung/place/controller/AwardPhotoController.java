package com.mirigangneung.place.controller;

import com.mirigangneung.place.dto.AwardPhotoPageResponse;
import com.mirigangneung.place.service.AwardPhotoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/award-photos")
@Validated
public class AwardPhotoController {
    private final AwardPhotoService service;

    public AwardPhotoController(AwardPhotoService service) {
        this.service = service;
    }

    @GetMapping
    public AwardPhotoPageResponse list(
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        return service.search(region, page, size);
    }
}
