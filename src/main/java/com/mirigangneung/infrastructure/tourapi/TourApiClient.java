package com.mirigangneung.infrastructure.tourapi;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface TourApiClient {
    List<TourPlace> search(String keyword, String category, int page, int size);

    Optional<TourPlace> find(String contentId);

    List<TourPlace> nearby(String contentId, double latitude, double longitude);

    List<TourPlace> related(String contentId);

    default Optional<TourPlaceIntro> intro(String contentId, String contentTypeId) {
        return Optional.empty();
    }

    record TourImage(String imageUrl, String title, String copyrightCode, int sortOrder) {
    }

    record TourPlace(String contentId, String name, String region, String category, String description,
                     Double latitude, Double longitude, String thumbnailUrl, List<TourImage> images,
                     OffsetDateTime sourceUpdatedAt) {
        public TourPlace(String contentId, String name, String region, String category, String description,
                         Double latitude, Double longitude, String thumbnailUrl, List<String> imageUrls) {
            this(contentId, name, region, category, description, latitude, longitude, thumbnailUrl,
                    toImages(imageUrls), null);
        }

        public TourPlace {
            images = images == null ? List.of() : List.copyOf(images);
        }

        public List<String> imageUrls() {
            return images.stream()
                    .map(TourImage::imageUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
        }

        public TourPlace withImages(List<TourImage> newImages) {
            return new TourPlace(contentId, name, region, category, description, latitude, longitude,
                    thumbnailUrl, newImages, sourceUpdatedAt);
        }

        private static List<TourImage> toImages(List<String> imageUrls) {
            if (imageUrls == null || imageUrls.isEmpty()) {
                return List.of();
            }
            List<TourImage> result = new ArrayList<>();
            for (int i = 0; i < imageUrls.size(); i++) {
                result.add(new TourImage(imageUrls.get(i), null, null, i));
            }
            return result;
        }
    }

    record TourPlaceIntro(String contentId, String contentTypeId, String useTime, String restDate,
                          String parking, String infoCenter) {
    }
}
