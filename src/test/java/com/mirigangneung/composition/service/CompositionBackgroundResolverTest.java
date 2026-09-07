package com.mirigangneung.composition.service;

import com.mirigangneung.common.error.ApiException;
import com.mirigangneung.infrastructure.image.ImageAssetCacheService;
import com.mirigangneung.infrastructure.image.PlaceImageStorage;
import com.mirigangneung.place.domain.Place;
import com.mirigangneung.place.domain.PlaceImage;
import com.mirigangneung.place.repository.PlaceImageRepository;
import com.mirigangneung.place.repository.PlaceRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompositionBackgroundResolverTest {
    private PlaceRepository places;
    private PlaceImageRepository images;
    private PlaceImageStorage storage;
    private ImageAssetCacheService cache;
    private CompositionBackgroundResolver resolver;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        images = mock(PlaceImageRepository.class);
        storage = mock(PlaceImageStorage.class);
        cache = mock(ImageAssetCacheService.class);
        resolver = new CompositionBackgroundResolver(places, images, storage, cache);
    }

    @Test
    void opensOriginalStorageKeyForSelectedType1Image() throws Exception {
        Place place = place();
        PlaceImage type3 = new PlaceImage(place, "https://img.test/blocked.jpg", "금지", "KTO", 0, "Type3");
        PlaceImage type1 = new PlaceImage(
                place, "https://img.test/editable.jpg", "허용", "KTO", 1, "Type1",
                "place-original.jpg", "place-thumbnail.jpg", "image/jpeg", 3L, 1L);
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of(type3, type1));
        when(storage.open("place-original.jpg")).thenReturn(Optional.of(
                new PlaceImageStorage.StoredAsset(new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/jpeg", 3)));

        var result = resolver.resolve(
                place.getId().toString(), "http://localhost:8080/media/images/place-thumbnail.jpg");

        assertThat(result.place()).isSameAs(place);
        assertThat(result.sourceImageUrl()).isEqualTo("https://img.test/editable.jpg");
        assertThat(result.image().bytes()).containsExactly(1, 2, 3);
        assertThat(result.image().filename()).isEqualTo("place-original.jpg");
        verifyNoInteractions(cache);
    }

    @Test
    void rejectsNonUuidPhotoSourceIdsInsteadOfCoercingThem() {
        assertThatThrownBy(() -> resolver.resolve("kto-gallery:123", null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_ONE_PICK_ID"));
        verifyNoInteractions(places, images, storage, cache);
    }

    @Test
    void refusesType3OnlyPlace() {
        Place place = place();
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(images.findByPlaceOrderBySortOrderAsc(place)).thenReturn(List.of(
                new PlaceImage(place, "https://img.test/blocked.jpg", "금지", "KTO", 0, "Type3")));

        assertThatThrownBy(() -> resolver.resolve(place.getId().toString(), null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("EDITABLE_BACKGROUND_NOT_FOUND"));
    }

    private Place place() {
        Place place = new Place("100", "안목해변", "강릉시", "nature", "바다",
                37.0, 128.0, null, "KTO");
        ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
        return place;
    }
}
