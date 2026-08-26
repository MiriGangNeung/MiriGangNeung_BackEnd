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
@Order(Ordered.LOWEST_PRECEDENCE - 200)
@ConditionalOnProperty(prefix = "kakao.local", name = "enrichment-on-startup", havingValue = "true")
public class KakaoPlaceEnrichmentRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceEnrichmentRunner.class);

    private final KakaoPlaceEnrichmentService enrichmentService;

    public KakaoPlaceEnrichmentRunner(KakaoPlaceEnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        KakaoPlaceEnrichmentService.EnrichmentResult result = enrichmentService.enrichMissingPlaces();
        log.info(
                "Kakao place links enriched: attempted={}, matched={}, skipped={}, failed={}",
                result.attemptedPlaces(),
                result.matchedPlaces(),
                result.skippedPlaces(),
                result.failedPlaces());
    }
}
