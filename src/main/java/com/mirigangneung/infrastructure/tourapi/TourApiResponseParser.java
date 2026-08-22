package com.mirigangneung.infrastructure.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mirigangneung.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Parses the common response envelope returned by Korean Tourism APIs. */
public final class TourApiResponseParser {
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    public TourApiResponseParser() {
        this(new ObjectMapper(), new XmlMapper());
    }

    TourApiResponseParser(ObjectMapper jsonMapper, XmlMapper xmlMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
    }

    public List<JsonNode> parseItems(String body) {
        if (body == null || body.isBlank()) {
            throw invalidResponse();
        }

        try {
            JsonNode root = body.trim().startsWith("<")
                    ? xmlMapper.readTree(body)
                    : jsonMapper.readTree(body);
            JsonNode response = root.has("response") ? root.path("response") : root;
            JsonNode header = response.path("header");
            String resultCode = text(header, "resultCode");
            if (resultCode == null) {
                throw invalidResponse();
            }
            if (!SUCCESS_CODES.contains(resultCode)) {
                throw new ApiException("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY,
                        "관광공사 API를 사용할 수 없습니다.");
            }

            JsonNode responseBody = response.path("body");
            if (responseBody.isMissingNode() || responseBody.isNull()) {
                throw invalidResponse();
            }
            JsonNode items = responseBody.path("items");
            if (items.isMissingNode() || items.isNull() || (items.isTextual() && items.asText().isBlank())) {
                return List.of();
            }

            JsonNode itemNode = items.has("item") ? items.path("item") : items;
            if (itemNode.isMissingNode() || itemNode.isNull() || (itemNode.isTextual() && itemNode.asText().isBlank())) {
                return List.of();
            }
            if (itemNode.isArray()) {
                List<JsonNode> result = new ArrayList<>();
                itemNode.forEach(result::add);
                return result;
            }
            if (itemNode.isObject()) {
                return List.of(itemNode);
            }
            return List.of();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static ApiException invalidResponse() {
        return new ApiException("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY,
                "관광공사 응답을 해석할 수 없습니다.");
    }
}
