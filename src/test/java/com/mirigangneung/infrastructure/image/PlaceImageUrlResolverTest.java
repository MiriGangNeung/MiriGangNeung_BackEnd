package com.mirigangneung.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlaceImageUrlResolverTest {

    private final PlaceImageUrlResolver resolver = new PlaceImageUrlResolver(
            new ImageCacheProperties(
                    true,
                    "/tmp/miri-images",
                    "http://localhost:8080/media/images/",
                    Duration.ofSeconds(3),
                    2_000_000,
                    640,
                    Duration.ofDays(365)));

    @Test
    void resolvesThumbnailAndOriginalFromStorageKeys() {
        Place place = new Place("1", "경포해변", "강릉", "nature", null, 37.8, 128.9, null, "KTO");
        PlaceImage image = new PlaceImage(
                place,
                "https://tour.example/original.jpg",
                "대표",
                "KTO",
                0,
                "Type1",
                "place-original.jpg",
                "place-thumbnail.jpg",
                "image/jpeg",
                1000L,
                100L);

        assertThat(resolver.thumbnailUrl(image)).isEqualTo("http://localhost:8080/media/images/place-thumbnail.jpg");
        assertThat(resolver.originalUrl(image)).isEqualTo("http://localhost:8080/media/images/place-original.jpg");
        assertThat(resolver.sourceUrl(image)).isEqualTo("https://tour.example/original.jpg");
    }

    @Test
    void fallsBackToSourceUrlForLegacyRows() {
        Place place = new Place("1", "경포해변", "강릉", "nature", null, 37.8, 128.9, null, "KTO");
        PlaceImage image = new PlaceImage(place, "https://tour.example/original.jpg", "대표", "KTO", 0, "Type1");

        assertThat(resolver.thumbnailUrl(image)).isEqualTo("https://tour.example/original.jpg");
        assertThat(resolver.originalUrl(image)).isEqualTo("https://tour.example/original.jpg");
    }

    @Test
    void ignoresUnsafeStorageKeysAndFallsBackToTheSourceUrl() {
        Place place = new Place("1", "경포해변", "강릉", "nature", null, 37.8, 128.9, null, "KTO");
        PlaceImage image = new PlaceImage(
                place,
                "https://tour.example/original.jpg",
                "대표",
                "KTO",
                0,
                "Type1",
                "../outside.jpg",
                "../outside-thumbnail.jpg",
                "image/jpeg",
                1000L,
                100L);

        assertThat(resolver.thumbnailUrl(image)).isEqualTo("https://tour.example/original.jpg");
        assertThat(resolver.originalUrl(image)).isEqualTo("https://tour.example/original.jpg");
    }
}
