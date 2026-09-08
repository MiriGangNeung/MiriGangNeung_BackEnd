package com.mirigangneung.composition.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompositionPollingJob {
    private final CompositionService compositionService;

    public CompositionPollingJob(CompositionService compositionService) {
        this.compositionService = compositionService;
    }

    @Scheduled(fixedDelayString = "${ai.poll-delay:2s}")
    public void poll() {
        compositionService.pollPendingJobs();
    }
}
