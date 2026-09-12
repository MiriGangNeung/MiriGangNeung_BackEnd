package com.mirigangneung.composition.controller;

import com.mirigangneung.composition.dto.CompositionStatusResponse;
import com.mirigangneung.composition.service.CompositionService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/compositions")
public class CompositionController {
    private final CompositionService service;

    public CompositionController(CompositionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompositionStatusResponse create(
            @RequestPart(name = "photo", required = false) MultipartFile photo,
            @RequestPart(name = "onePickId") String onePickId,
            @RequestPart(name = "aspectRatio", required = false) String aspectRatio,
            @RequestPart(name = "backgroundImageUrl", required = false) String backgroundImageUrl,
            @RequestPart(name = "sessionId", required = false) String sessionId,
            @RequestPart(name = "modelPresetId", required = false) String modelPresetId) {
        return service.create(photo, modelPresetId, onePickId, aspectRatio, backgroundImageUrl, sessionId);
    }

    @GetMapping("/{id}")
    public CompositionStatusResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/retry")
    public CompositionStatusResponse retry(@PathVariable String id) {
        return service.retry(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) {
        CompositionService.CompositionDownload download = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.filename() + "\"")
                .body(new InputStreamResource(download.input()));
    }
}
