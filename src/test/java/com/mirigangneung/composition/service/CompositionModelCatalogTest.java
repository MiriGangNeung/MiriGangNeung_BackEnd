package com.mirigangneung.composition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mirigangneung.common.error.ApiException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class CompositionModelCatalogTest {
    private final CompositionModelCatalog catalog = new CompositionModelCatalog();

    @Test
    void listsAndLoadsTheDefaultModelAsset() throws IOException {
        var models = catalog.list();
        assertThat(models).hasSize(1);
        assertThat(models.get(0).id()).isEqualTo("default-female-01");

        var asset = catalog.load("default-female-01");
        try (var input = asset.input()) {
            assertThat(asset.contentType()).isEqualTo("image/png");
            assertThat(input.readNBytes(8)).containsExactly(
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
        }
    }

    @Test
    void rejectsUnknownModelId() {
        assertThatThrownBy(() -> catalog.load("unknown"))
                .isInstanceOf(ApiException.class)
                .hasMessage("유효하지 않은 AI 모델입니다.");
    }
}
