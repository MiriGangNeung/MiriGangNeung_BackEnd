package com.mirigangneung.place.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class KakaoPlaceMappingRunnerTest {

    @Mock private KakaoPlaceMappingService mappingService;
    @Mock private ApplicationArguments arguments;

    @Test
    void appliesTheCuratedCatalogWhenTheApplicationStarts() {
        when(mappingService.applyMappings()).thenReturn(
                KakaoPlaceMappingService.MappingResult.empty());

        new KakaoPlaceMappingRunner(mappingService).run(arguments);

        verify(mappingService).applyMappings();
    }
}
