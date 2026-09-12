package com.mirigangneung.composition.controller;

import com.mirigangneung.composition.dto.CompositionModelResponse;
import com.mirigangneung.composition.service.CompositionModelCatalog;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/composition-models")
public class CompositionModelController {
    private final CompositionModelCatalog catalog;

    public CompositionModelController(CompositionModelCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<CompositionModelResponse> list() {
        return catalog.list().stream()
                .map(model -> new CompositionModelResponse(
                        model.id(),
                        model.name(),
                        "/api/v1/composition-models/" + model.id() + "/image",
                        model.description()))
                .toList();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<InputStreamResource> image(@PathVariable String id) {
        CompositionModelCatalog.Asset asset = catalog.load(id);
        InputStream input = asset.input();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentLength(asset.size())
                .body(new InputStreamResource(input));
    }
}
