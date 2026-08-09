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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class KoreanTourAwardPhotoClient implements AwardPhotoApiClient {
    private static final Logger log = LoggerFactory.getLogger(KoreanTourAwardPhotoClient.class);
    private static final String ENDPOINT = "phokoAwrdSyncList";

    private final AwardPhotoProperties props;
    private final RestClient client;
    private final TourApiResponseParser parser;

    @Autowired
    public KoreanTourAwardPhotoClient(AwardPhotoProperties props) {
        this(props, buildClient(props), new TourApiResponseParser());
    }

    KoreanTourAwardPhotoClient(AwardPhotoProperties props, RestClient client, TourApiResponseParser parser) {
        this.props = props;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public List<AwardPhoto> search(String regionCode, int page, int size) {
        if (!hasServiceKey()) {
            return List.of();
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(props.baseUrl())
                .pathSegment(ENDPOINT)
                .queryParam("serviceKey", decodedServiceKey())
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "MiriGangNeung")
                .queryParam("_type", "json")
                .queryParam("arrange", "C")
                .queryParam("showflag", "1")
                .queryParam("pageNo", Math.max(0, page) + 1)
                .queryParam("numOfRows", size);
        if (hasText(regionCode)) {
            uriBuilder.queryParam("lDongRegnCd", regionCode.trim());
        }

        return call(uriBuilder.build().encode().toUri()).stream()
                .map(this::mapPhoto)
                .filter(photo -> hasText(photo.originalImageUrl()) || hasText(photo.thumbnailUrl()))
                .toList();
    }

    private List<JsonNode> call(URI uri) {
        try {
            String body = client.get().uri(uri).retrieve().body(String.class);
            return parser.parseItems(body);
        } catch (ApiException e) {
            log.warn("Tour award API response rejected: endpoint={}, code={}", ENDPOINT, e.getCode());
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("Tour award API HTTP request failed: endpoint={}, status={}", ENDPOINT, e.getStatusCode().value());
            throw externalApiError();
        } catch (RestClientException e) {
            log.warn("Tour award API request failed: endpoint={}, exception={}", ENDPOINT, e.getClass().getSimpleName());
            throw externalApiError();
        } catch (Exception e) {
            log.warn("Tour award API request could not be prepared: endpoint={}, exception={}", ENDPOINT, e.getClass().getSimpleName());
            throw externalApiError();
        }
    }

    private AwardPhoto mapPhoto(JsonNode node) {
        return new AwardPhoto(
                text(node, "contentId"),
                text(node, "koTitle"),
                text(node, "koFilmst"),
                text(node, "koWnprzDiz"),
                keywords(firstNonBlank(text(node, "koKeyWord"), text(node, "koKeyword"))),
                text(node, "orgImage"),
                text(node, "thumbImage"),
                text(node, "koCmanNm"),
                text(node, "cpyrhtDivCd"));
    }

    private static List<String> keywords(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(KoreanTourAwardPhotoClient::hasText)
                .distinct()
                .toList();
    }

    private boolean hasServiceKey() {
        return props.key() != null && !props.key().isBlank();
    }

    private String decodedServiceKey() {
        return URLDecoder.decode(props.key(), StandardCharsets.UTF_8);
    }

    private static RestClient buildClient(AwardPhotoProperties props) {
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
