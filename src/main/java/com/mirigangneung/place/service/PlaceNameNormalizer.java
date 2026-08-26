package com.mirigangneung.place.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class PlaceNameNormalizer {
    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*\\)");
    private static final Pattern REGION_PREFIX = Pattern.compile("^(?:강원특별자치도|강원도|강릉시)\\s*");
    private static final Pattern GANGNEUNG_PREFIX_WITH_SPACE = Pattern.compile("^강릉\\s+");
    private static final Pattern NON_NAME_CHARACTER = Pattern.compile("[^0-9a-z가-힣]");
    private static final Map<String, String> VERIFIED_ALIASES = Map.of(
            "강릉통일공원안보전시관", "강릉통일공원",
            "등명낙가사", "등명락가사",
            "구룡폭포", "구룡폭포오대산"
    );

    private PlaceNameNormalizer() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        normalized = PARENTHETICAL.matcher(normalized).replaceAll("");
        normalized = REGION_PREFIX.matcher(normalized).replaceFirst("");
        normalized = GANGNEUNG_PREFIX_WITH_SPACE.matcher(normalized).replaceFirst("");
        normalized = normalized.replace("해수욕장", "해변");
        normalized = NON_NAME_CHARACTER.matcher(normalized).replaceAll("");
        return VERIFIED_ALIASES.getOrDefault(normalized, normalized);
    }
}
