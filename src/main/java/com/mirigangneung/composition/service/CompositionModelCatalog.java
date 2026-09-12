package com.mirigangneung.composition.service;

import com.mirigangneung.common.error.ApiException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CompositionModelCatalog {
    private static final Model DEFAULT_FEMALE = new Model(
            "default-female-01",
            "기본 AI 모델",
            "사진 업로드 없이 바로 체험할 수 있는 기본 모델입니다.",
            "composition-models/default-female-01.png",
            "image/png");

    public List<Model> list() {
        return List.of(DEFAULT_FEMALE);
    }

    public Optional<Model> find(String id) {
        return list().stream().filter(model -> model.id().equals(id)).findFirst();
    }

    public Asset load(String id) {
        Model model = find(id).orElseThrow(() -> new ApiException(
                "INVALID_MODEL_PRESET_ID", HttpStatus.BAD_REQUEST, "유효하지 않은 AI 모델입니다."));
        ClassPathResource resource = new ClassPathResource(model.resourcePath());
        try {
            return new Asset(resource.getInputStream(), resource.contentLength(), model.contentType());
        } catch (IOException exception) {
            throw new ApiException(
                    "COMPOSITION_MODEL_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "기본 AI 모델 이미지를 준비할 수 없습니다.");
        }
    }

    public record Model(String id, String name, String description, String resourcePath, String contentType) {
    }

    public record Asset(java.io.InputStream input, long size, String contentType) {
    }
}
