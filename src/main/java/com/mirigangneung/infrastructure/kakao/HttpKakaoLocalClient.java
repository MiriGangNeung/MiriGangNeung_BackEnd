package com.mirigangneung.infrastructure.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpKakaoLocalClient implements KakaoLocalClient {
    private final KakaoLocalProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpKakaoLocalClient(
            KakaoLocalProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.client = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public HttpKakaoLocalClient(
            KakaoLocalProperties properties,
            RestClient client,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NearbyPlace> searchByCategory(
            double longitude,
            double latitude,
            String categoryCode,
            int radiusMeters,
            int page,
            int size
    ) {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new ApiException(
                    "KAKAO_API_NOT_CONFIGURED",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Kakao REST API 키가 설정되지 않았습니다."
            );
        }

        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", categoryCode)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radiusMeters)
                            .queryParam("page", page + 1)
                            .queryParam("size", size)
                            .queryParam("sort", "distance")
                            .build())
                    .header("Authorization", "KakaoAK " + properties.key())
                    .retrieve()
                    .body(String.class);
            return parseDocuments(body, categoryCode);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    "KAKAO_API_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "Kakao 주변 장소를 불러오지 못했습니다."
            );
        }
    }

    @Override
    public List<NearbyPlace> searchByKeyword(
            String query,
            double longitude,
            double latitude,
            int radiusMeters,
            int page,
            int size
    ) {
        ensureConfigured();

        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radiusMeters)
                            .queryParam("page", page + 1)
                            .queryParam("size", size)
                            .queryParam("sort", "distance")
                            .build())
                    .header("Authorization", "KakaoAK " + properties.key())
                    .retrieve()
                    .body(String.class);
            return parseDocuments(body, "");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    "KAKAO_API_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "Kakao 장소 검색을 불러오지 못했습니다."
            );
        }
    }

    private void ensureConfigured() {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new ApiException(
                    "KAKAO_API_NOT_CONFIGURED",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Kakao REST API 키가 설정되지 않았습니다."
            );
        }
    }

    private List<NearbyPlace> parseDocuments(String body, String requestedCategoryCode) {
        try {
            JsonNode documents = objectMapper.readTree(body).path("documents");
            if (!documents.isArray()) {
                return List.of();
            }

            List<NearbyPlace> result = new ArrayList<>();
            for (JsonNode document : documents) {
                String externalPlaceId = text(document, "id");
                String name = text(document, "place_name");
                if (externalPlaceId.isBlank() || name.isBlank()) {
                    continue;
                }
                Double longitude = decimal(document, "x");
                Double latitude = decimal(document, "y");
                if (longitude == null || latitude == null) {
                    continue;
                }
                result.add(new NearbyPlace(
                        externalPlaceId,
                        name,
                        text(document, "category_name"),
                        text(document, "category_group_code").isBlank()
                                ? requestedCategoryCode
                                : text(document, "category_group_code"),
                        text(document, "address_name"),
                        text(document, "road_address_name"),
                        text(document, "phone"),
                        text(document, "place_url"),
                        latitude,
                        longitude,
                        integer(document, "distance")
                ));
            }
            return result;
        } catch (Exception exception) {
            throw new ApiException(
                    "KAKAO_API_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "Kakao 주변 장소 응답을 해석하지 못했습니다."
            );
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private static Double decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer integer(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
