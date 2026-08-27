package com.mirigangneung.course.dto;

import java.util.List;

public record NearbyPlacesResponse(
        String scope,
        String category,
        int page,
        int size,
        boolean isEnd,
        List<NearbyPlaceResponse> places
) {
    /** Compatibility constructor for callers that only need the old nearby list shape. */
    public NearbyPlacesResponse(String category, List<NearbyPlaceResponse> places) {
        this("nearby", category, 0, places == null ? 0 : places.size(), true, places);
    }

    public NearbyPlacesResponse {
        places = places == null ? List.of() : List.copyOf(places);
    }
}
