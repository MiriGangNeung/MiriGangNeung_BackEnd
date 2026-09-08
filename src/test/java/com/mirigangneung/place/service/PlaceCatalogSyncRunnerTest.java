package com.mirigangneung.place.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlaceCatalogSyncRunnerTest {
    @Mock
    private PlaceCatalogSyncService syncService;
    @Mock
    private ApplicationArguments arguments;

    @Test
    void onlySynchronizesTheCatalogWithoutCallingKakaoEnrichment() {
        org.mockito.Mockito.when(syncService.synchronizeAll())
                .thenReturn(new PlaceCatalogSyncService.SyncResult(0, 0, 0, 0, 0, 0, 0));

        new PlaceCatalogSyncRunner(syncService).run(arguments);

        verify(syncService).synchronizeAll();
    }
}
