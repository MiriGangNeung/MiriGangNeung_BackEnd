package com.mirigangneung.place.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@ConditionalOnProperty(prefix = "tour.api", name = "sync-on-startup", havingValue = "true")
public class KakaoPlaceMappingRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceMappingRunner.class);

    private final KakaoPlaceMappingService mappingService;

    public KakaoPlaceMappingRunner(KakaoPlaceMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        KakaoPlaceMappingService.MappingResult result = mappingService.applyMappings();
        log.info(
                "Curated Kakao place mappings applied after KTO synchronization: configured={}, "
                        + "updated={}, suppressed={}, missing={}",
                result.configuredMappings(),
                result.updatedPlaces(),
                result.suppressedPlaces(),
                result.missingPlaces());
    }
}
