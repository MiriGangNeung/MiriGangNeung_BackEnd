package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.PhotoGalleryApiClient;
import com.mirigangneung.place.domain.Place;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourismPhotoMatcherTest {

    @Test
    void loadsAllGalleryPagesAndReturnsAtMostFiveUniqueImagesPerNormalizedPlace() {
        PhotoGalleryApiClient client = mock(PhotoGalleryApiClient.class);
        TourismPhotoMatcher matcher = new TourismPhotoMatcher(
                client,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1));
        Place place = withId(new Place("100", "강릉 선교장", "강릉시", "culture", "설명",
                37.8, 128.9, null, "KTO"));

        List<PhotoGalleryApiClient.PhotoGalleryPhoto> firstPage = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            firstPage.add(photo("gallery-" + index, "강릉 선교장", "강원도 강릉시",
                    "https://img.test/" + (index % 6) + ".jpg"));
        }
        when(client.search("강릉", 0, 2_000)).thenReturn(firstPage);
        when(client.search("강릉", 1, 2_000)).thenReturn(List.of(
                photo("gallery-last", "강릉 선교장", "강원도 강릉시", "https://img.test/last.jpg")));

        var firstResult = matcher.findImageUrls(List.of(place));
        var secondResult = matcher.findImageUrls(List.of(place));

        assertThat(firstResult.get(place.getId())).containsExactly(
                "https://img.test/0.jpg",
                "https://img.test/1.jpg",
                "https://img.test/2.jpg",
                "https://img.test/3.jpg",
                "https://img.test/4.jpg");
        assertThat(secondResult).isEqualTo(firstResult);
        verify(client, times(1)).search("강릉", 0, 2_000);
        verify(client, times(1)).search("강릉", 1, 2_000);
    }

    @Test
    void excludesPhotosOutsideGangneungAndDoesNotFuzzyMatchNearbyPlaces() {
        PhotoGalleryApiClient client = mock(PhotoGalleryApiClient.class);
        TourismPhotoMatcher matcher = new TourismPhotoMatcher(
                client,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1));
        Place gyeongpodae = withId(new Place("100", "경포대", "강릉시", "culture", "설명",
                37.8, 128.9, null, "KTO"));
        when(client.search("강릉", 0, 2_000)).thenReturn(List.of(
                photo("gallery-1", "경포해변", "강원도 강릉시", "https://img.test/beach.jpg"),
                photo("gallery-2", "경포대", "강원도 속초시", "https://img.test/sokcho.jpg")));

        assertThat(matcher.findImageUrls(List.of(gyeongpodae)).get(gyeongpodae.getId())).isNull();
    }

    private static PhotoGalleryApiClient.PhotoGalleryPhoto photo(
            String id, String title, String location, String imageUrl) {
        return new PhotoGalleryApiClient.PhotoGalleryPhoto(
                id, title, location, "202608", List.of("강릉"), imageUrl, imageUrl, "작가");
    }

    private static Place withId(Place place) {
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        return place;
    }
}
