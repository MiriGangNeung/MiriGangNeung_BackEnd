package com.mirigangneung.place.service;

public record KakaoPlaceMapping(
        String tourContentId,
        String kakaoPlaceId,
        String kakaoPlaceUrl) {

    public KakaoPlaceMapping {
        tourContentId = requireText(tourContentId, "tourContentId");
        kakaoPlaceId = trimToNull(kakaoPlaceId);
        kakaoPlaceUrl = trimToNull(kakaoPlaceUrl);
        if ((kakaoPlaceId == null) != (kakaoPlaceUrl == null)) {
            throw new IllegalArgumentException(
                    "kakaoPlaceId와 kakaoPlaceUrl은 함께 지정하거나 함께 비워야 합니다.");
        }
    }

    public boolean suppressed() {
        return kakaoPlaceId == null && kakaoPlaceUrl == null;
    }

    private static String requireText(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + "은 비어 있을 수 없습니다.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
