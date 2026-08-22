package com.mirigangneung.place.controller;

import com.mirigangneung.place.dto.TourismPhotoPageResponse;
import com.mirigangneung.place.service.TourismPhotoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tourism-photos")
@Validated
public class TourismPhotoController {
    private final TourismPhotoService service;

    public TourismPhotoController(TourismPhotoService service) {
        this.service = service;
    }

    @GetMapping
    public TourismPhotoPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        return service.search(page, size);
    }
}
