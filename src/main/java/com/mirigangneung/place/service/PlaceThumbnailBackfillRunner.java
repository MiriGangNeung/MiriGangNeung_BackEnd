package com.mirigangneung.place.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlaceThumbnailBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlaceThumbnailBackfillRunner.class);

    private final PlaceThumbnailBackfillService backfillService;

    public PlaceThumbnailBackfillRunner(PlaceThumbnailBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = backfillService.backfillMissingThumbnails();
        log.info("Place thumbnails backfilled from stored images: updated={}", updated);
    }
}
