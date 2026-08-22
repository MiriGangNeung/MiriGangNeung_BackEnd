package com.mirigangneung.infrastructure.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.mirigangneung.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class KoreanTourPhotoGalleryClient implements PhotoGalleryApiClient {
    private static final Logger log = LoggerFactory.getLogger(KoreanTourPhotoGalleryClient.class);
    private static final String ENDPOINT = "gallerySearchList1";
    private static final String KTO_HTTP_HOST = "http://tong.visitkorea.or.kr";

    private final PhotoGalleryProperties props;
    private final RestClient client;
    private final TourApiResponseParser parser;

    @Autowired
    public KoreanTourPhotoGalleryClient(PhotoGalleryProperties props) {
        this(props, buildClient(props), new TourApiResponseParser());
    }

    KoreanTourPhotoGalleryClient(PhotoGalleryProperties props, RestClient client, TourApiResponseParser parser) {
        this.props = props;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public List<PhotoGalleryPhoto> search(String keyword, int page, int size) {
        if (!hasServiceKey() || !hasText(keyword)) {
            return List.of();
        }

        URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                .pathSegment(ENDPOINT)
                .queryParam("serviceKey", URLEncoder.encode(decodedServiceKey(), StandardCharsets.UTF_8))
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "MiriGangNeung")
                .queryParam("_type", "json")
                .queryParam("arrange", "C")
                .queryParam("keyword", URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8))
                .queryParam("pageNo", Math.max(0, page) + 1)
                .queryParam("numOfRows", size)
                .build(true)
                .toUri();

        return call(uri).stream()
                .map(this::mapPhoto)
                .filter(photo -> hasText(photo.originalImageUrl()) || hasText(photo.thumbnailUrl()))
                .toList();
    }

    private List<JsonNode> call(URI uri) {
        try {
            String body = client.get().uri(uri).retrieve().body(String.class);
            return parser.parseItems(body);
        } catch (ApiException e) {
            log.warn("Tour photo gallery API response rejected: endpoint={}, code={}", ENDPOINT, e.getCode());
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("Tour photo gallery API HTTP request failed: endpoint={}, status={}",
                    ENDPOINT, e.getStatusCode().value());
            throw externalApiError();
        } catch (RestClientException e) {
            log.warn("Tour photo gallery API request failed: endpoint={}, exception={}",
                    ENDPOINT, e.getClass().getSimpleName());
            throw externalApiError();
        } catch (Exception e) {
            log.warn("Tour photo gallery API request could not be prepared: endpoint={}, exception={}",
                    ENDPOINT, e.getClass().getSimpleName());
            throw externalApiError();
        }
    }

    private PhotoGalleryPhoto mapPhoto(JsonNode node) {
        String imageUrl = normalizeImageUrl(text(node, "galWebImageUrl"));
        return new PhotoGalleryPhoto(
                text(node, "galContentId"),
                text(node, "galTitle"),
                text(node, "galPhotographyLocation"),
                text(node, "galPhotographyMonth"),
                keywords(text(node, "galSearchKeyword")),
                imageUrl,
                imageUrl,
                text(node, "galPhotographer"));
    }

    private static List<String> keywords(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(KoreanTourPhotoGalleryClient::hasText)
                .distinct()
                .toList();
    }

    private static String normalizeImageUrl(String value) {
        if (value == null) {
            return null;
        }
        if (value.equals(KTO_HTTP_HOST)) {
            return "https://tong.visitkorea.or.kr";
        }
        if (value.startsWith(KTO_HTTP_HOST + "/")) {
            return "https://tong.visitkorea.or.kr" + value.substring(KTO_HTTP_HOST.length());
        }
        return value;
    }

    private boolean hasServiceKey() {
        return props.key() != null && !props.key().isBlank();
    }

    private String decodedServiceKey() {
        return URLDecoder.decode(props.key(), StandardCharsets.UTF_8);
    }

    private static RestClient buildClient(PhotoGalleryProperties props) {
        Duration timeout = props.timeout() == null ? Duration.ofSeconds(5) : props.timeout();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static ApiException externalApiError() {
        return new ApiException("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY,
                "관광공사 API를 사용할 수 없습니다.");
    }

    private static String text(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(key).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
