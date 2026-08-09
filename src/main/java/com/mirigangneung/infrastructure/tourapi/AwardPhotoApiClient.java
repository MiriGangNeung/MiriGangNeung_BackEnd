package com.mirigangneung.infrastructure.tourapi;

import java.util.List;

public interface AwardPhotoApiClient {
    List<AwardPhoto> search(String regionCode, int page, int size);

    record AwardPhoto(
            String contentId,
            String title,
            String location,
            String award,
            List<String> keywords,
            String originalImageUrl,
            String thumbnailUrl,
            String photographer,
            String copyrightCode) {
    }
}
