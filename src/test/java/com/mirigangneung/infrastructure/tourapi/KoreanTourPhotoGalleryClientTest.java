package com.mirigangneung.infrastructure.tourapi;

import com.mirigangneung.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KoreanTourPhotoGalleryClientTest {
    private static final String BASE_URL = "https://example.test/B551011/PhotoGalleryService1";
    private static final String SUCCESS_LIST = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
              {"galContentId":"gallery-1","galTitle":"강릉의 밤","galPhotographyLocation":"강릉시 경포대","galPhotographyMonth":"202405","galSearchKeyword":"강릉, 바다, 야경","galWebImageUrl":"http://tong.visitkorea.or.kr/cms/gallery-original.jpg","galPhotographer":"홍길동"},
              {"galContentId":"gallery-2","galTitle":"이미지 없음","galPhotographyLocation":"강릉시","galPhotographyMonth":"202406","galSearchKeyword":"강릉","galWebImageUrl":"  ","galPhotographer":"촬영자"}
            ]}}}}
            """;

    @Test
    void searchesGalleryPhotosAndMapsFieldsWithHttpsImageUrls() {
        Fixture fixture = fixture("secret%2Bkey");
        fixture.server().expect(once(), request -> assertRequest(request,
                        "/B551011/PhotoGalleryService1/galleryList1",
                        Map.of(
                                "serviceKey", "secret+key",
                                "pageNo", "1",
                                "numOfRows", "100",
                                "arrange", "C")))
                .andRespond(withSuccess(SUCCESS_LIST, MediaType.APPLICATION_JSON));

        List<PhotoGalleryApiClient.PhotoGalleryPhoto> result = fixture.client().search(0, 100);

        assertThat(result).singleElement().satisfies(photo -> {
            assertThat(photo.contentId()).isEqualTo("gallery-1");
            assertThat(photo.title()).isEqualTo("강릉의 밤");
            assertThat(photo.location()).isEqualTo("강릉시 경포대");
            assertThat(photo.photographyMonth()).isEqualTo("202405");
            assertThat(photo.keywords()).containsExactly("강릉", "바다", "야경");
            assertThat(photo.originalImageUrl())
                    .isEqualTo("https://tong.visitkorea.or.kr/cms/gallery-original.jpg");
            assertThat(photo.thumbnailUrl())
                    .isEqualTo("https://tong.visitkorea.or.kr/cms/gallery-original.jpg");
            assertThat(photo.photographer()).isEqualTo("홍길동");
        });
        fixture.server().verify();
    }

    @Test
    void doesNotCallRemoteWhenServiceKeyIsBlank() {
        Fixture fixture = fixture("");

        assertThat(fixture.client().search(0, 100)).isEmpty();

        fixture.server().verify();
    }

    @Test
    void rejectsUpstreamErrorInsteadOfReturningEmpty() {
        Fixture fixture = fixture("secret");
        String errorResponse = """
                {"response":{"header":{"resultCode":"113","resultMsg":"키 오류"},"body":{}}}
                """;
        fixture.server().expect(once(), request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/B551011/PhotoGalleryService1/galleryList1"))
                .andRespond(withSuccess(errorResponse, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().search(0, 100))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TOUR_API_ERROR"));
        fixture.server().verify();
    }

    private Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KoreanTourPhotoGalleryClient client = new KoreanTourPhotoGalleryClient(
                new PhotoGalleryProperties(BASE_URL, key, Duration.ofSeconds(2)),
                builder.build(),
                new TourApiResponseParser());
        return new Fixture(client, server);
    }

    private record Fixture(KoreanTourPhotoGalleryClient client, MockRestServiceServer server) {
    }

    private static void assertRequest(org.springframework.http.client.ClientHttpRequest request,
                                      String expectedPath,
                                      Map<String, String> expectedParams) {
        URI uri = request.getURI();
        assertThat(uri.getPath()).isEqualTo(expectedPath);
        org.springframework.util.MultiValueMap<String, String> params =
                org.springframework.web.util.UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        expectedParams.forEach((name, value) -> assertThat(UriUtils.decode(params.getFirst(name), StandardCharsets.UTF_8))
                .as(name).isEqualTo(value));
        assertThat(uri.getRawQuery()).contains("serviceKey=secret%2Bkey");
        assertThat(params.getFirst("MobileOS")).isEqualTo("ETC");
        assertThat(params.getFirst("MobileApp")).isEqualTo("MiriGangNeung");
        assertThat(params.getFirst("_type")).isEqualTo("json");
    }
}
