package com.mirigangneung.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPlaceImageStorageTest {

    @TempDir
    Path root;

    @Test
    void storesOriginalAndSmallerThumbnailWithDeterministicKeys() throws Exception {
        LocalPlaceImageStorage storage = new LocalPlaceImageStorage(root.toString(), 640);
        byte[] original = jpeg(1200, 800);

        PlaceImageStorage.StoredImage stored = storage.store(
                "https://tong.visitkorea.or.kr/cms/resource/place.jpg", original, "image/jpeg");

        assertThat(Files.size(root.resolve(stored.originalStorageKey()))).isEqualTo(original.length);
        assertThat(Files.size(root.resolve(stored.thumbnailStorageKey())))
                .isLessThan(original.length);
        assertThat(storage.find("https://tong.visitkorea.or.kr/cms/resource/place.jpg"))
                .contains(stored);
        assertThat(storage.open(stored.thumbnailStorageKey()))
                .get()
                .satisfies(asset -> {
                    assertThat(asset.contentType()).isEqualTo("image/jpeg");
                    assertThat(asset.byteSize()).isEqualTo(stored.thumbnailByteSize());
                });

        PlaceImageStorage.StoredImage repeated = storage.store(
                "https://tong.visitkorea.or.kr/cms/resource/place.jpg", original, "image/jpeg");
        assertThat(repeated.originalStorageKey()).isEqualTo(stored.originalStorageKey());
        assertThat(repeated.thumbnailStorageKey()).isEqualTo(stored.thumbnailStorageKey());
    }

    @Test
    void rejectsUnsafeStorageKeys() throws Exception {
        LocalPlaceImageStorage storage = new LocalPlaceImageStorage(root.toString(), 640);

        assertThatThrownBy(() -> storage.open("../outside.jpg"))
                .isInstanceOf(IOException.class);
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color((x * 17) % 255, (y * 31) % 255, (x + y) % 255).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
