package com.mirigangneung.infrastructure.storage;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class LocalTemporaryImageStorageTest {
    @Test void savesAndReadsWithGeneratedKey() throws Exception {
        Path dir = Files.createTempDirectory("miri-image-test");
        var storage = new LocalTemporaryImageStorage(dir.toString());
        String key = storage.save(new ByteArrayInputStream(new byte[]{1,2,3}), "image/png", 3, Instant.now().plusSeconds(60));
        assertThat(key).endsWith(".png");
        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.open(key).readAllBytes()).containsExactly(1,2,3);
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }
}
