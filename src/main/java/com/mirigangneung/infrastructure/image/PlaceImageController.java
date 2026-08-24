package com.mirigangneung.infrastructure.image;

import com.mirigangneung.common.error.ApiException;
import java.io.IOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceImageController {
    private final PlaceImageStorage storage;
    private final ImageCacheProperties properties;

    public PlaceImageController(PlaceImageStorage storage, ImageCacheProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @GetMapping("/media/images/{storageKey}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String storageKey) {
        try {
            PlaceImageStorage.StoredAsset asset = storage.open(storageKey)
                    .orElseThrow(() -> new ApiException(
                            "IMAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
            MediaType contentType = MediaTypeFactory.getMediaType(storageKey)
                    .orElseGet(() -> parseContentType(asset.contentType()));
            String cacheControl = "public, max-age=" + properties.browserTtl().toSeconds() + ", immutable";
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(asset.byteSize())
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                    .body(new InputStreamResource(asset.input()));
        } catch (IOException exception) {
            throw new ApiException("IMAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
    }

    private static MediaType parseContentType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
