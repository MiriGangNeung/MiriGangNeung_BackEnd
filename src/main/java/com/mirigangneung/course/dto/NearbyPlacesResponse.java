package com.mirigangneung.course.dto;

import java.util.List;

public record NearbyPlacesResponse(String category, List<NearbyPlaceResponse> places) {
    public NearbyPlacesResponse {
        places = places == null ? List.of() : List.copyOf(places);
    }
}
