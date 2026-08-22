package com.mirigangneung.place.service;

import com.mirigangneung.infrastructure.tourapi.AwardPhotoApiClient;
import com.mirigangneung.place.controller.AwardPhotoController;
import com.mirigangneung.place.dto.AwardPhotoPageResponse;
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

class AwardPhotoServiceTest {
    private AwardPhotoApiClient client;
    private AwardPhotoService service;

    @BeforeEach
    void setUp() {
        client = mock(AwardPhotoApiClient.class);
        service = new AwardPhotoService(client);
    }

    @Test
    void mapsAwardPhotosWithoutPersistingThem() {
        when(client.search("51", 0, 100)).thenReturn(List.of(
                new AwardPhotoApiClient.AwardPhoto(
                        "award-1", "강릉의 밤", "강릉시 경포대", "금상",
                        List.of("강릉", "야경"), "https://img.test/original.jpg",
                        "https://img.test/thumb.jpg", "홍길동", "Type1"),
                new AwardPhotoApiClient.AwardPhoto(
                        "award-2", "바다의 아침", "강릉시", "입선",
                        List.of("바다"), null, "https://img.test/fallback.jpg",
                        "김작가", "Type1"),
                new AwardPhotoApiClient.AwardPhoto(
                        "award-3", "이미지 없음", "강릉시", "입선",
                        List.of(), null, null, "이작가", "Type1"),
                new AwardPhotoApiClient.AwardPhoto(
                        "award-4", "속초의 밤", "강원특별자치도 속초시", "입선",
                        List.of("속초"), "https://img.test/sokcho.jpg", null,
                        "박작가", "Type1")));

        AwardPhotoPageResponse result = service.search("51", 0, 100);

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting("id")
                .containsExactly("award-1", "award-2");
        assertThat(result.content().get(0)).satisfies(photo -> {
            assertThat(photo.title()).isEqualTo("강릉의 밤");
            assertThat(photo.location()).isEqualTo("강릉시 경포대");
            assertThat(photo.award()).isEqualTo("금상");
            assertThat(photo.keywords()).containsExactly("강릉", "야경");
            assertThat(photo.originalImageUrl()).isEqualTo("https://img.test/original.jpg");
            assertThat(photo.thumbnailUrl()).isEqualTo("https://img.test/thumb.jpg");
            assertThat(photo.photographer()).isEqualTo("홍길동");
            assertThat(photo.copyrightCode()).isEqualTo("Type1");
            assertThat(photo.source()).isEqualTo("KTO_AWARD");
        });
        assertThat(result.content().get(1).originalImageUrl()).isNull();
        assertThat(result.content().get(1).thumbnailUrl()).isEqualTo("https://img.test/fallback.jpg");
        verify(client).search("51", 0, 100);
    }

    @Test
    void controllerDeclaresAwardPhotoDefaultsAndRejectsOversizedPage() throws Exception {
        AwardPhotoController controller = new AwardPhotoController(service);
        Method method = AwardPhotoController.class.getMethod("list", String.class, int.class, int.class);
        RequestParam[] parameters = java.util.Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .toArray(RequestParam[]::new);

        assertThat(parameters[0].defaultValue()).isEqualTo("51");
        assertThat(parameters[1].defaultValue()).isEqualTo("0");
        assertThat(parameters[2].defaultValue()).isEqualTo("100");

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<?> violations = validator.forExecutables().validateParameters(
                controller, method, new Object[]{"51", 0, 101});
        assertThat(violations).isNotEmpty();
    }
}
