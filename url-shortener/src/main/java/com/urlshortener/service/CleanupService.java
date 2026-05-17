package com.urlshortener.service;

import com.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {

    private final UrlMappingRepository urlMappingRepository;

    /**
     * Runs every hour. Marks expired URLs as inactive.
     */
    @Scheduled(fixedRateString = "PT1H")
    @Transactional
    public void deactivateExpiredUrls() {
        int count = urlMappingRepository.deactivateExpiredUrls(Instant.now());
        if (count > 0) {
            log.info("Deactivated {} expired URL mappings", count);
        }
    }
}
