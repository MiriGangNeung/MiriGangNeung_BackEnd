package com.mirigangneung.infrastructure.kakao;

import java.util.List;

public interface KakaoLocalClient {
    List<NearbyPlace> searchByCategory(
            double longitude,
            double latitude,
            String categoryCode,
            int radiusMeters,
            int page,
            int size
    );

    List<NearbyPlace> searchByKeyword(
            String query,
            double longitude,
            double latitude,
            int radiusMeters,
            int page,
            int size
    );

    SearchPage searchByCategoryInRect(
            String rect,
            String categoryCode,
            int page,
            int size
    );

    SearchPage searchByKeywordInRect(
            String query,
            String rect,
            String categoryCode,
            int page,
            int size
    );

    record SearchPage(List<NearbyPlace> places, int page, boolean isEnd) {
        public SearchPage {
            places = places == null ? List.of() : List.copyOf(places);
        }
    }

    record NearbyPlace(
            String externalPlaceId,
            String name,
            String categoryName,
            String categoryCode,
            String address,
            String roadAddress,
            String phone,
            String placeUrl,
            double latitude,
            double longitude,
            Integer distanceMeters
    ) {
    }
}
