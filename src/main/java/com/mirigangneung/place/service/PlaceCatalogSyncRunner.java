package com.mirigangneung.place.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tour.api", name = "sync-on-startup", havingValue = "true")
public class PlaceCatalogSyncRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlaceCatalogSyncRunner.class);

    private final PlaceCatalogSyncService syncService;

    public PlaceCatalogSyncRunner(PlaceCatalogSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        PlaceCatalogSyncService.SyncResult result = syncService.synchronizeAll();
        log.info(
                "Gangneung place catalog synchronized: fetched={}, categoryExcluded={}, foodDeleted={}, "
                        + "galleryOnlyDeleted={}, saved={}, withImages={}, brokenImagesExcluded={}",
                result.fetchedPlaces(),
                result.excludedByCategory(),
                result.deletedFoodPlaces(),
                result.deletedGalleryOnlyCards(),
                result.savedPlaces(),
                result.placesWithImages(),
                result.rejectedImageUrls());
    }
}
