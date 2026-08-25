package com.mirigangneung.course.dto;

import com.mirigangneung.infrastructure.kakao.KakaoLocalClient;

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
        String nearestStopName
) {
    public static NearbyPlaceResponse from(
            KakaoLocalClient.NearbyPlace place,
            String category,
            int distanceMeters,
            String nearestStopId,
            String nearestStopName
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
                nearestStopName
        );
    }
}
