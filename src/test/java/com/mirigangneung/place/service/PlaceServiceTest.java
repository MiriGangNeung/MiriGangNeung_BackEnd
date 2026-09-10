package com.mirigangneung.place.service;

import com.mirigangneung.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.redis.RedisCache;
import com.mirigangneung.infrastructure.tourapi.TourApiClient;
import com.mirigangneung.infrastructure.tourapi.TourApiCacheProperties;
import com.mirigangneung.infrastructure.image.ImageCacheProperties;
import com.mirigangneung.infrastructure.image.PlaceImageUrlResolver;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.dto.PlaceDetailResponse;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlaceServiceTest {
    private PlaceRepository places;
    private PlaceImageRepository images;
    private TourApiClient tour;
    private PlaceService service;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        images = mock(PlaceImageRepository.class);
        tour = mock(TourApiClient.class);
        service = new PlaceService(places, images, tour);
        when(places.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void exposesAtMostFiveType1ImageUrlsForPlaceList() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, "https://img.test/0.jpg", "KTO"));
        List<PlaceImage> imageEntities = List.of(
                new PlaceImage(place, "https://img.test/0.jpg", "0", "KTO", 0, "Type3"),
                new PlaceImage(place, "https://img.test/1.jpg", "1", "KTO", 1, "Type1"),
                new PlaceImage(place, "https://img.test/2.jpg", "2", "KTO", 2, "Type1"),
                new PlaceImage(place, "https://img.test/3.jpg", "3", "KTO", 3, "Type1"),
                new PlaceImage(place, "https://img.test/4.jpg", "4", "KTO", 4, "Type1"),
                new PlaceImage(place, "https://img.test/5.jpg", "5", "KTO", 5, "Type1"));
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(place), PageRequest.of(0, 20), 1));
        when(images.findByPlaceInOrderBySortOrderAsc(List.of(place))).thenReturn(imageEntities);

        var result = service.search(null, null, 0, 20);
        var json = new ObjectMapper().valueToTree(result.content().get(0));

        assertThat(json.path("imageUrls")).hasSize(5);
        assertThat(json.path("thumbnailUrl").asText()).isEqualTo("https://img.test/1.jpg");
        assertThat(json.path("imageUrls").get(0).asText()).isEqualTo("https://img.test/1.jpg");
        assertThat(json.path("imageUrls").get(4).asText()).isEqualTo("https://img.test/5.jpg");
    }

    @Test
    void readsOnlyStoredImagesWithoutCallingTheGalleryAtRequestTime() {
        Place place = withId(new Place("100", "강릉 선교장", "강릉시", "culture", "설명",
                37.8, 128.9, null, "KTO"));
        PlaceImage stored = new PlaceImage(
                place, "https://stored.test/one.jpg", "대표", "KTO", 0, "Type1");
        TourismPhotoMatcher tourismPhotoMatcher = mock(TourismPhotoMatcher.class);
        PlaceService matchingService = new PlaceService(
                places, images, tour, null, new ObjectMapper(),
                new TourApiCacheProperties(Duration.ofMinutes(5), Duration.ofHours(1)));
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(place), PageRequest.of(0, 20), 1));
        when(images.findByPlaceInOrderBySortOrderAsc(List.of(place))).thenReturn(List.of(stored));

        var result = matchingService.search(null, null, 0, 20);

        assertThat(result.content()).singleElement().satisfies(response -> {
            assertThat(response.thumbnailUrl()).isEqualTo("https://stored.test/one.jpg");
            assertThat(response.imageUrls()).containsExactly("https://stored.test/one.jpg");
        });
        verifyNoInteractions(tour, tourismPhotoMatcher);
    }

    @Test
    void filtersByTheSameNormalizedCategoryUsedForPersistence() {
        when(places.findVisibleByCategoryAndName(eq("food"), eq(""), any()))
                .thenReturn(Page.empty());

        service.search("food", "", 0, 20);

        verify(places).findVisibleByCategoryAndName(eq("food"), eq(""), any());
        verify(places, never()).findVisibleByRegionAndName(any(), any(), any());
        verifyNoInteractions(tour);
    }

    @Test
    void returnsStructuredImageMetadataAndLegacyUrls() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, "https://img.test/one.jpg", "KTO"));
        PlaceImage image = new PlaceImage(place, "https://img.test/one.jpg", "대표", "KTO", 0, "Type1");
        PlaceImage forbidden = new PlaceImage(place, "https://img.test/type3.jpg", "변경 금지", "KTO", 1, "Type3");
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of(image, forbidden));

        PlaceDetailResponse result = service.detail(place.getId().toString());

        assertThat(result.imageUrls()).containsExactly("https://img.test/one.jpg");
        assertThat(result.images()).singleElement().satisfies(detail -> {
            assertThat(detail.imageUrl()).isEqualTo("https://img.test/one.jpg");
            assertThat(detail.title()).isEqualTo("대표");
            assertThat(detail.copyrightCode()).isEqualTo("Type1");
        });
    }

    @Test
    void exposesCachedThumbnailsAndOriginalsInTheSameOrder() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        PlaceImage first = new PlaceImage(
                place,
                "https://img.test/one.jpg",
                "대표",
                "KTO",
                0,
                "Type1",
                "place-one-original.jpg",
                "place-one-thumbnail.jpg",
                "image/jpeg",
                1000L,
                100L);
        PlaceImage second = new PlaceImage(
                place,
                "https://img.test/two.jpg",
                "두 번째",
                "KTO",
                1,
                "Type1",
                "place-two-original.jpg",
                "place-two-thumbnail.jpg",
                "image/jpeg",
                2000L,
                200L);
        PlaceImageUrlResolver resolver = new PlaceImageUrlResolver(new ImageCacheProperties(
                true,
                "/tmp/miri-images",
                "http://localhost:8080/media/images",
                Duration.ofSeconds(3),
                2_000_000,
                640,
                Duration.ofDays(365)));
        PlaceService cachedImageService = new PlaceService(
                places,
                images,
                tour,
                null,
                new ObjectMapper(),
                new TourApiCacheProperties(Duration.ofMinutes(5), Duration.ofHours(1)),
                resolver);
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(place), PageRequest.of(0, 20), 1));
        when(images.findByPlaceInOrderBySortOrderAsc(List.of(place))).thenReturn(List.of(first, second));
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of(first, second));

        var result = cachedImageService.search(null, null, 0, 20);
        var detail = cachedImageService.detail(place.getId().toString());

        assertThat(result.content()).singleElement().satisfies(response -> {
            assertThat(response.imageUrls()).containsExactly(
                    "http://localhost:8080/media/images/place-one-thumbnail.jpg",
                    "http://localhost:8080/media/images/place-two-thumbnail.jpg");
            assertThat(response.originalImageUrls()).containsExactly(
                    "http://localhost:8080/media/images/place-one-original.jpg",
                    "http://localhost:8080/media/images/place-two-original.jpg");
        });
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images().get(0)).satisfies(response -> {
            assertThat(response.imageUrl()).endsWith("place-one-thumbnail.jpg");
            assertThat(response.originalImageUrl()).endsWith("place-one-original.jpg");
            assertThat(response.sourceImageUrl()).isEqualTo("https://img.test/one.jpg");
        });
    }

    @Test
    void readsLocalPageWithoutCallingTourSearch() {
        Place local = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        Page<Place> localPage = new PageImpl<>(List.of(local), PageRequest.of(0, 20), 1);
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(localPage);

        var result = service.search(null, null, 0, 20);

        assertThat(result.content()).singleElement().satisfies(place ->
                assertThat(place.name()).isEqualTo("경포대"));
        verifyNoInteractions(tour);
    }

    @Test
    void returnsDetailWithoutImages() {
        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of());

        assertThat(service.detail(place.getId().toString()).images()).isEmpty();
    }

    @Test
    void returnsCachedListWithoutCallingTourApi() throws Exception {
        RedisCache cache = mock(RedisCache.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PlaceService cachedService = cachedService(cache, objectMapper);
        when(cache.get(anyString())).thenReturn(objectMapper.writeValueAsString(
                new com.mirigangneung.place.dto.PlacePageResponse(List.of(), 0, 20, 0, 0)));

        var result = cachedService.search(null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(tour, places, images);
    }

    @Test
    void writesListAndDetailWithDifferentConfiguredTtls() throws Exception {
        RedisCache cache = mock(RedisCache.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Duration listTtl = Duration.ofMinutes(7);
        Duration detailTtl = Duration.ofHours(2);
        TourApiCacheProperties cacheProperties = new TourApiCacheProperties(listTtl, detailTtl);
        PlaceService cachedService = new PlaceService(places, images, tour, cache, objectMapper, cacheProperties);
        when(cache.get(anyString())).thenReturn(null);
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(Page.empty());

        cachedService.search(null, null, 0, 20);

        Place place = withId(new Place("100", "경포대", "강릉시", "nature", "설명",
                37.8, 128.9, null, "KTO"));
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of());
        cachedService.detail(place.getId().toString());

        verify(cache).put(startsWith("place:list:v9:"), anyString(), eq(listTtl));
        verify(cache).put(startsWith("place:detail:v4:"), anyString(), eq(detailTtl));
        verifyNoInteractions(tour);
    }

    @Test
    void treatsMalformedCachedJsonAsMiss() throws Exception {
        RedisCache cache = mock(RedisCache.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PlaceService cachedService = cachedService(cache, objectMapper);
        when(cache.get(anyString())).thenReturn("{not-json");
        when(places.findVisibleByRegionAndName(eq("강릉"), eq(""), any()))
                .thenReturn(Page.empty());

        assertThat(cachedService.search(null, null, 0, 20).content()).isEmpty();

        verifyNoInteractions(tour);
    }

    @Test
    void detailCacheKeyUsesOnlyInternalPlaceIdentifier() throws Exception {
        RedisCache cache = mock(RedisCache.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PlaceDetailResponse cached = new PlaceDetailResponse("place-id", "경포대", "강릉시", "nature",
                "설명", List.of(), 37.8, 128.9, List.of());
        when(cache.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));
        PlaceService cachedService = cachedService(cache, objectMapper);

        assertThat(cachedService.detail("place-id").id()).isEqualTo("place-id");

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(cache).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("place:detail:v4:place-id");
        verifyNoInteractions(places, images, tour);
    }

    @Test
    void returnsNotFoundFromDatabaseWithoutCallingTourDetail() {
        when(places.findByTourContentId("missing-place")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail("missing-place"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("PLACE_NOT_FOUND"));

        verifyNoInteractions(tour);
    }

    private PlaceService cachedService(RedisCache cache, ObjectMapper objectMapper) {
        return new PlaceService(places, images, tour, cache, objectMapper,
                new TourApiCacheProperties(Duration.ofMinutes(5), Duration.ofHours(1)));
    }

    private static Place withId(Place place) {
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        return place;
    }
}
