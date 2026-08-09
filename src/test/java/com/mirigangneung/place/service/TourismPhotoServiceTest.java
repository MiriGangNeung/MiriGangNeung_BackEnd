package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.PhotoGalleryApiClient;
import com.mirigangneung.place.controller.TourismPhotoController;
import com.mirigangneung.place.dto.TourismPhotoPageResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourismPhotoServiceTest {
    private PhotoGalleryApiClient client;
    private TourismPhotoService service;

    @BeforeEach
    void setUp() {
        client = mock(PhotoGalleryApiClient.class);
        service = new TourismPhotoService(client);
    }

    @Test
    void mapsGalleryPhotosWithoutPersistingThem() {
        when(client.search(0, 100)).thenReturn(List.of(
                new PhotoGalleryApiClient.PhotoGalleryPhoto(
                        "gallery-1", "강릉의 밤", "강릉시 경포대", "202405",
                        List.of("강릉", "야경"), "https://img.test/original.jpg",
                        "https://img.test/thumb.jpg", "홍길동"),
                new PhotoGalleryApiClient.PhotoGalleryPhoto(
                        "gallery-2", "썸네일만 있음", "강릉시", "202406",
                        List.of("바다"), null, "https://img.test/fallback.jpg", "김작가"),
                new PhotoGalleryApiClient.PhotoGalleryPhoto(
                        "gallery-3", "이미지 없음", "강릉시", "202407",
                        List.of(), null, null, "이작가"),
                new PhotoGalleryApiClient.PhotoGalleryPhoto(
                        "gallery-4", "속초의 밤", "강원특별자치도 속초시", "202408",
                        List.of("속초"), "https://img.test/sokcho.jpg", null, "박작가")));

        TourismPhotoPageResponse result = service.search(0, 100);

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting("id")
                .containsExactly("gallery-1", "gallery-2");
        assertThat(result.content().get(0)).satisfies(photo -> {
            assertThat(photo.title()).isEqualTo("강릉의 밤");
            assertThat(photo.location()).isEqualTo("강릉시 경포대");
            assertThat(photo.photographyMonth()).isEqualTo("202405");
            assertThat(photo.keywords()).containsExactly("강릉", "야경");
            assertThat(photo.originalImageUrl()).isEqualTo("https://img.test/original.jpg");
            assertThat(photo.thumbnailUrl()).isEqualTo("https://img.test/thumb.jpg");
            assertThat(photo.photographer()).isEqualTo("홍길동");
            assertThat(photo.source()).isEqualTo("KTO_PHOTO_GALLERY");
        });
        assertThat(result.content().get(1).originalImageUrl()).isNull();
        assertThat(result.content().get(1).thumbnailUrl()).isEqualTo("https://img.test/fallback.jpg");
        verify(client).search(0, 100);
    }

    @Test
    void returnsAnEmptyPageWhenGalleryBatchIsEmpty() {
        when(client.search(2, 100)).thenReturn(List.of());

        TourismPhotoPageResponse result = service.search(2, 100);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void controllerDeclaresPhotoGalleryDefaultsAndRejectsOversizedPage() throws Exception {
        TourismPhotoController controller = new TourismPhotoController(service);
        Method method = TourismPhotoController.class.getMethod("list", int.class, int.class);
        RequestParam[] parameters = java.util.Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .toArray(RequestParam[]::new);

        assertThat(parameters[0].defaultValue()).isEqualTo("0");
        assertThat(parameters[1].defaultValue()).isEqualTo("100");

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<?> violations = validator.forExecutables().validateParameters(
                controller, method, new Object[]{0, 101});
        assertThat(violations).isNotEmpty();
    }
}
