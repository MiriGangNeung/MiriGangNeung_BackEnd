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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KoreanTourApiClient implements TourApiClient {
    private static final Logger log = LoggerFactory.getLogger(KoreanTourApiClient.class);
    private static final DateTimeFormatter SOURCE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TourApiProperties props;
    private final RestClient client;
    private final TourApiResponseParser parser;

    @Autowired
    public KoreanTourApiClient(TourApiProperties props) {
        this(props, buildClient(props), new TourApiResponseParser());
    }

    KoreanTourApiClient(TourApiProperties props, RestClient client, TourApiResponseParser parser) {
        this.props = props;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public List<TourPlace> search(String keyword, String category, int page, int size) {
        if (!hasServiceKey()) {
            return List.of();
        }

        String endpoint = hasText(keyword) ? "searchKeyword2" : "areaBasedList2";
        Map<String, Object> params = regionalParams(category, page, size);
        if (hasText(keyword)) {
            params.put("keyword", keyword.trim());
        }
        return call(endpoint, params).stream().map(this::mapPlace).toList();
    }

    @Override
    public java.util.Optional<TourPlace> find(String contentId) {
        if (!hasServiceKey()) {
            return java.util.Optional.empty();
        }

        List<TourPlace> places = call("detailCommon2", Map.of(
                "contentId", contentId,
                "defaultYN", "Y",
                "firstImageYN", "Y",
                "overviewYN", "Y"))
                .stream()
                .map(this::mapPlace)
                .toList();
        if (places.isEmpty()) {
            return java.util.Optional.empty();
        }

        TourPlace place = places.get(0);
        List<TourApiClient.TourImage> detailImages = call("detailImage2", Map.of(
                        "contentId", contentId,
                        "imageYN", "Y"))
                .stream()
                .map(this::mapImage)
                .filter(image -> hasText(image.imageUrl()))
                .toList();
        return java.util.Optional.of(place.withImages(mergeImages(place.images(), detailImages)));
    }

    @Override
    public List<TourPlace> nearby(String contentId, double latitude, double longitude) {
        if (!hasServiceKey()) {
            return List.of();
        }
        return call("locationBasedList2", Map.of(
                        "contentTypeId", "12",
                        "mapX", longitude,
                        "mapY", latitude,
                        "radius", "20000",
                        "arrange", "E",
                        "numOfRows", 20,
                        "pageNo", 1))
                .stream()
                .map(this::mapPlace)
                .toList();
    }

    @Override
    public List<TourPlace> related(String contentId) {
        // detailInfo2 is repeated information for one place, not the related-tourism API.
        return List.of();
    }

    @Override
    public java.util.Optional<TourPlaceIntro> intro(String contentId, String contentTypeId) {
        if (!hasServiceKey()) {
            return java.util.Optional.empty();
        }
        return call("detailIntro2", Map.of(
                        "contentId", contentId,
                        "contentTypeId", contentTypeId))
                .stream()
                .findFirst()
                .map(node -> new TourPlaceIntro(
                        contentId,
                        firstNonBlank(text(node, "contenttypeid"), contentTypeId),
                        firstNonBlank(text(node, "usetime"), text(node, "usetimeculture"), text(node, "usetimeleports")),
                        firstNonBlank(text(node, "restdate"), text(node, "restdateculture"), text(node, "restdateleports")),
                        firstNonBlank(text(node, "parking"), text(node, "parkingleports")),
                        firstNonBlank(text(node, "infocenter"), text(node, "infocenterculture"), text(node, "infocenterfood"))));
    }

    private List<JsonNode> call(String path, Map<String, Object> params) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(props.baseUrl())
                    .pathSegment(path)
                    .queryParam("serviceKey", decodedServiceKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "MiriGangNeung")
                    .queryParam("_type", "json");
            params.forEach(uriBuilder::queryParam);
            URI uri = uriBuilder.build().encode().toUri();
            String body = client.get().uri(uri).retrieve().body(String.class);
            return parser.parseItems(body);
        } catch (ApiException e) {
            log.warn("Tour API response rejected: path={}, code={}", path, e.getCode());
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("Tour API HTTP request failed: path={}, status={}", path, e.getStatusCode().value());
            throw externalApiError();
        } catch (RestClientException e) {
            log.warn("Tour API request failed: path={}, reason={}", path, e.getMessage());
            throw externalApiError();
        } catch (Exception e) {
            log.warn("Tour API request could not be prepared: path={}, reason={}", path, e.getMessage());
            throw externalApiError();
        }
    }

    private Map<String, Object> regionalParams(String category, int page, int size) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("contentTypeId", TourCategoryMapper.toContentTypeId(category));
        params.put("lDongRegnCd", "51");
        params.put("lDongSignguCd", "150");
        params.put("pageNo", Math.max(0, page) + 1);
        params.put("numOfRows", size);
        params.put("arrange", "A");
        return params;
    }

    private TourPlace mapPlace(JsonNode node) {
        String contentTypeId = text(node, "contenttypeid");
        String thumbnailUrl = firstNonBlank(text(node, "firstimage"), text(node, "firstimage2"));
        List<TourImage> images = hasText(thumbnailUrl)
                ? List.of(new TourImage(thumbnailUrl, text(node, "title"), text(node, "cpyrhtDivCd"), 0))
                : List.of();
        return new TourPlace(
                text(node, "contentid"),
                text(node, "title"),
                text(node, "addr1"),
                TourCategoryMapper.toInternalCategory(contentTypeId),
                text(node, "overview"),
                number(node, "mapy"),
                number(node, "mapx"),
                thumbnailUrl,
                images,
                sourceUpdatedAt(text(node, "modifiedtime")));
    }

    private TourImage mapImage(JsonNode node) {
        return new TourImage(
                firstNonBlank(text(node, "originimgurl"), text(node, "smallimageurl")),
                firstNonBlank(text(node, "imgname"), text(node, "title")),
                text(node, "cpyrhtDivCd"),
                integer(node, "serialnum"));
    }

    private List<TourImage> mergeImages(List<TourImage> primary, List<TourImage> additional) {
        Map<String, TourImage> byUrl = new LinkedHashMap<>();
        primary.forEach(image -> addImage(byUrl, image));
        additional.forEach(image -> addImage(byUrl, image));
        return new ArrayList<>(byUrl.values());
    }

    private void addImage(Map<String, TourImage> byUrl, TourImage image) {
        if (image != null && hasText(image.imageUrl())) {
            byUrl.putIfAbsent(image.imageUrl(), image);
        }
    }

    private boolean hasServiceKey() {
        return props.key() != null && !props.key().isBlank();
    }

    private String decodedServiceKey() {
        return URLDecoder.decode(props.key(), StandardCharsets.UTF_8);
    }

    private static RestClient buildClient(TourApiProperties props) {
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

    private static Double number(JsonNode node, String key) {
        String value = text(node, key);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int integer(JsonNode node, String key) {
        String value = text(node, key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static OffsetDateTime sourceUpdatedAt(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.of(
                    java.time.LocalDateTime.parse(value, SOURCE_DATE_FORMAT),
                    ZoneOffset.ofHours(9));
        } catch (DateTimeParseException e) {
            return null;
        }
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
