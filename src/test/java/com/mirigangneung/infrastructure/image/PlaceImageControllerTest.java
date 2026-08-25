package com.mirigangneung.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirigangneung.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlaceImageControllerTest {

    private final PlaceImageStorage storage = mock(PlaceImageStorage.class);
    private final ImageCacheProperties properties = new ImageCacheProperties(
            true,
            "/tmp/miri-images",
            "http://localhost:8080/media/images",
            Duration.ofSeconds(3),
            2_000_000,
            640,
            Duration.ofDays(365));
    private final PlaceImageController controller = new PlaceImageController(storage, properties);

    @Test
    void streamsImageWithImmutableCacheHeaders() throws Exception {
        when(storage.open("place-thumbnail.jpg"))
                .thenReturn(Optional.of(new PlaceImageStorage.StoredAsset(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "image/jpeg",
                        3L)));

        ResponseEntity<InputStreamResource> response = controller.get("place-thumbnail.jpg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/jpeg");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3L);
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
        assertThat(response.getBody().getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void returnsNotFoundForMissingOrUnsafeStorageKeys() throws Exception {
        when(storage.open("missing.jpg")).thenReturn(Optional.empty());
        when(storage.open("../outside.jpg")).thenReturn(Optional.empty());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> controller.get("missing.jpg")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("IMAGE_NOT_FOUND"));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> controller.get("../outside.jpg")))
                .isInstanceOf(ApiException.class);
    }
}
