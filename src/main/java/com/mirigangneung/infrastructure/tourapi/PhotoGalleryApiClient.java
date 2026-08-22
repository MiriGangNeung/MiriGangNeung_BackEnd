package com.mirigangneung.infrastructure.tourapi;

import java.util.List;

public interface PhotoGalleryApiClient {
    List<PhotoGalleryPhoto> search(String keyword, int page, int size);

    record PhotoGalleryPhoto(
            String contentId,
            String title,
            String location,
            String photographyMonth,
            List<String> keywords,
            String originalImageUrl,
            String thumbnailUrl,
            String photographer) {
    }
}
