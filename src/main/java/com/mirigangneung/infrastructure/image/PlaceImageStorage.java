package com.mirigangneung.infrastructure.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface PlaceImageStorage {
    StoredImage store(String sourceUrl, byte[] originalBytes, String contentType) throws IOException;

    Optional<StoredImage> find(String sourceUrl) throws IOException;

    Optional<StoredAsset> open(String storageKey) throws IOException;

    record StoredImage(
            String originalStorageKey,
            String thumbnailStorageKey,
            String contentType,
            long originalByteSize,
            long thumbnailByteSize) {
    }

    record StoredAsset(InputStream input, String contentType, long byteSize) {
    }
}
