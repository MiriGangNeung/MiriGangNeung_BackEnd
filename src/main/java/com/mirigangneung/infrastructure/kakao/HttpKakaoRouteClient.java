package com.mirigangneung.infrastructure.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirigangneung.common.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class HttpKakaoRouteClient implements KakaoRouteClient {
    private final KakaoRouteProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpKakaoRouteClient(KakaoRouteProperties properties, ObjectMapper objectMapper) {
        this(properties, RestClient.builder().baseUrl(properties.baseUrl()).build(), objectMapper);
    }

    public HttpKakaoRouteClient(
            KakaoRouteProperties properties,
            RestClient client,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public RouteResult walking(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        if (properties.key() == null || properties.key().isBlank()) {
            return new RouteResult(0, 0, List.of());
        }

        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/routing/walk")
                            .queryParam("start_x", originLongitude)
                            .queryParam("start_y", originLatitude)
                            .queryParam("end_x", destinationLongitude)
                            .queryParam("end_y", destinationLatitude)
                            .build())
                    .header("Authorization", "KakaoAK " + properties.key())
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    "KAKAO_API_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "Kakao 도보 경로 API를 사용할 수 없습니다."
            );
        }
    }

    private RouteResult parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode currentRoute = root.path("route");
            if (!currentRoute.isMissingNode() && !currentRoute.isNull()) {
                if (root.has("status") && !"OK".equalsIgnoreCase(root.path("status").asText())) {
                    throw routeNotFound();
                }
                return new RouteResult(
                        routeMetric(currentRoute, "totalDistance", "distance"),
                        routeMetric(currentRoute, "totalTime", "time"),
                        readPolyline(currentRoute)
                );
            }

            JsonNode legacyRoute = root.path("routes").path(0);
            if (legacyRoute.isMissingNode() || legacyRoute.path("result_code").asInt(-1) != 0) {
                throw routeNotFound();
            }

            int distance = legacyRoute.path("summary").path("distance").asInt(0);
            int duration = legacyRoute.path("summary").path("duration").asInt(0);
            return new RouteResult(distance, duration, readPolyline(legacyRoute));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    "KAKAO_API_ERROR",
                    HttpStatus.BAD_GATEWAY,
                    "Kakao 도보 경로 응답을 해석하지 못했습니다."
            );
        }
    }

    private ApiException routeNotFound() {
        return new ApiException(
                "KAKAO_API_ERROR",
                HttpStatus.BAD_GATEWAY,
                "Kakao 도보 경로를 찾지 못했습니다."
        );
    }

    private int routeMetric(JsonNode route, String totalField, String legField) {
        int total = route.path("properties").path(totalField).asInt(0);
        if (total > 0) {
            return total;
        }

        int sum = 0;
        for (JsonNode leg : route.path("legs")) {
            sum += leg.path("properties").path(legField).asInt(0);
        }
        return sum;
    }

    private List<List<Double>> readPolyline(JsonNode route) {
        List<List<Double>> points = new ArrayList<>();

        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes");
                for (int index = 0; index + 1 < vertexes.size(); index += 2) {
                    addPoint(points, vertexes.get(index), vertexes.get(index + 1));
                }
            }
        }

        // Keep compatibility with the normalized legs/steps shape used by
        // the existing frontend route proxy and by Kakao response fixtures.
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                for (JsonNode point : step.path("path").path("points")) {
                    if (point.isArray() && point.size() >= 2) {
                        addPoint(points, point.get(0), point.get(1));
                    }
                }
            }
        }
        return points;
    }

    private void addPoint(List<List<Double>> points, JsonNode longitude, JsonNode latitude) {
        double x = longitude.asDouble(Double.NaN);
        double y = latitude.asDouble(Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return;
        }
        List<Double> point = List.of(x, y);
        if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
            points.add(point);
        }
    }
}
