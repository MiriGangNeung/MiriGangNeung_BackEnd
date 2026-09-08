package com.mirigangneung.place.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class KakaoPlaceMappingCatalog {
    static final String RESOURCE_PATH = "data/kakao-place-mappings.csv";
    private static final String HEADER = "tourContentId,kakaoPlaceId,kakaoPlaceUrl";

    private final List<KakaoPlaceMapping> mappings;

    public KakaoPlaceMappingCatalog() {
        this(new ClassPathResource(RESOURCE_PATH));
    }

    KakaoPlaceMappingCatalog(Resource resource) {
        this.mappings = load(resource);
    }

    public List<KakaoPlaceMapping> mappings() {
        return mappings;
    }

    private static List<KakaoPlaceMapping> load(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException("Kakao 장소 매핑 파일을 찾을 수 없습니다: " + RESOURCE_PATH);
        }

        List<KakaoPlaceMapping> result = new ArrayList<>();
        Set<String> contentIds = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new IllegalStateException("Kakao 장소 매핑 CSV 헤더가 올바르지 않습니다.");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                if (columns.length != 3) {
                    throw new IllegalStateException(
                            "Kakao 장소 매핑 CSV 열 수가 올바르지 않습니다: line=" + lineNumber);
                }
                KakaoPlaceMapping mapping;
                try {
                    mapping = new KakaoPlaceMapping(columns[0], columns[1], columns[2]);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(
                            "Kakao 장소 매핑 CSV 값이 올바르지 않습니다: line=" + lineNumber,
                            exception);
                }
                if (!contentIds.add(mapping.tourContentId())) {
                    throw new IllegalStateException(
                            "Kakao 장소 매핑 CSV에 중복된 tourContentId가 있습니다: "
                                    + mapping.tourContentId());
                }
                result.add(mapping);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Kakao 장소 매핑 파일을 읽을 수 없습니다: " + RESOURCE_PATH, exception);
        }
        return List.copyOf(result);
    }
}
