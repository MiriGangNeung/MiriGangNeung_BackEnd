package com.mirigangneung.course.dto;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;

import java.util.List;

public record NearbyPlaceResponse(
        String externalPlaceId,
        String name,
        String category,
        String categoryName,
        String address,
        String roadAddress,
        String phone,
        String placeUrl,
        double latitude,
        double longitude,
        int distanceMeters,
        String nearestStopId,
        String nearestStopName,
        Integer recommendationScore,
        List<String> recommendationReasons
) {
    public NearbyPlaceResponse {
        recommendationReasons = recommendationReasons == null
                ? List.of()
                : List.copyOf(recommendationReasons);
    }

    public static NearbyPlaceResponse from(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            String nearestStopId,
            String nearestStopName
    ) {
        return from(
                place,
                category,
                distanceMeters,
                nearestStopId,
                nearestStopName,
                null,
                List.of()
        );
    }

    public static NearbyPlaceResponse from(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            String nearestStopId,
            String nearestStopName,
            Integer recommendationScore,
            List<String> recommendationReasons
    ) {
        return new NearbyPlaceResponse(
                place.externalPlaceId(),
                place.name(),
                category,
                place.categoryName(),
                place.address(),
                place.roadAddress(),
                place.phone(),
                place.placeUrl(),
                place.latitude(),
                place.longitude(),
                distanceMeters,
                nearestStopId,
                nearestStopName,
                recommendationScore,
                recommendationReasons
        );
    }
}
