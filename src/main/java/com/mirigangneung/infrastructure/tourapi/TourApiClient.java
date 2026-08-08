package com.mirigangneung.infrastructure.tourapi;
import java.util.*;
public interface TourApiClient {
    List<TourPlace> search(String keyword, String category, int page, int size);
    Optional<TourPlace> find(String contentId);
    List<TourPlace> nearby(String contentId, double latitude, double longitude);
    List<TourPlace> related(String contentId);
    record TourPlace(String contentId, String name, String region, String category, String description,
                     Double latitude, Double longitude, String thumbnailUrl, List<String> imageUrls) {}
}
