package com.mirigangneung.course.controller;

import com.mirigangneung.course.dto.AddExternalStopRequest;
import com.mirigangneung.course.dto.CourseResponse;
import com.mirigangneung.course.dto.CreateCourseRequest;
import com.mirigangneung.course.dto.NearbyPlacesResponse;
import com.mirigangneung.course.dto.ShareResponse;
import com.mirigangneung.course.dto.StopOrderRequest;
import com.mirigangneung.course.service.CoursePlaceService;
import com.mirigangneung.course.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CourseController {
    private final CourseService courseService;
    private final CoursePlaceService coursePlaceService;

    public CourseController(CourseService courseService, CoursePlaceService coursePlaceService) {
        this.courseService = courseService;
        this.coursePlaceService = coursePlaceService;
    }

    @PostMapping("/courses")
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest request) {
        return courseService.create(request);
    }

    @GetMapping("/courses/{id}")
    public CourseResponse get(@PathVariable String id) {
        return courseService.get(id);
    }

    @DeleteMapping("/courses/{id}")
    public void delete(@PathVariable String id) {
        courseService.delete(id);
    }

    @PostMapping("/courses/{id}/share")
    public ShareResponse share(@PathVariable String id) {
        return courseService.share(id);
    }

    @DeleteMapping("/courses/{id}/share")
    public void revoke(@PathVariable String id) {
        courseService.revoke(id);
    }

    @GetMapping("/share/courses/{token}")
    public CourseResponse shared(@PathVariable String token) {
        return courseService.shared(token);
    }

    @GetMapping("/courses/{id}/nearby-places")
    public NearbyPlacesResponse nearby(
            @PathVariable String id,
            @RequestParam String category
    ) {
        return coursePlaceService.nearby(id, category);
    }

    @PostMapping("/courses/{id}/stops/external")
    public CourseResponse addExternalStop(
            @PathVariable String id,
            @Valid @RequestBody AddExternalStopRequest request
    ) {
        return coursePlaceService.addExternalStop(id, request);
    }

    @DeleteMapping("/courses/{id}/stops/{stopId}")
    public CourseResponse deleteStop(@PathVariable String id, @PathVariable String stopId) {
        return coursePlaceService.deleteStop(id, stopId);
    }

    @PutMapping("/courses/{id}/stops/order")
    public CourseResponse reorderStops(
            @PathVariable String id,
            @Valid @RequestBody StopOrderRequest request
    ) {
        return coursePlaceService.reorderStops(id, request);
    }
}
