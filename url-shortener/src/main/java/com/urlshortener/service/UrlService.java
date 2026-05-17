package com.urlshortener.service;

import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String CACHE_PREFIX = "url:";

    private final UrlMappingRepository urlMappingRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.short-code-length:6}")
    private int shortCodeLength;

    @Value("${app.default-ttl-days:365}")
    private int defaultTtlDays;

    @Value("${app.cache.url-ttl-seconds:3600}")
    private long cacheTtlSeconds;

    @Transactional
    public UrlMapping shorten(String originalUrl, String userId, String customAlias, Integer ttlDays) {
        // Check custom alias uniqueness
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlMappingRepository.findByCustomAlias(customAlias).isPresent()) {
                throw new IllegalArgumentException("Custom alias already in use: " + customAlias);
            }
        }

        String shortCode = customAlias != null && !customAlias.isBlank()
            ? customAlias
            : generateUniqueCode();

        Instant expiresAt = Instant.now().plus(
            ttlDays != null ? ttlDays : defaultTtlDays, ChronoUnit.DAYS
        );

        UrlMapping mapping = UrlMapping.builder()
            .shortCode(shortCode)
            .originalUrl(originalUrl)
            .userId(userId)
            .customAlias(customAlias)
            .expiresAt(expiresAt)
            .active(true)
            .build();

        UrlMapping saved = urlMappingRepository.save(mapping);
        cacheUrl(shortCode, originalUrl);

        log.info("Shortened URL: {} -> {}", shortCode, originalUrl);
        return saved;
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        // 1. Check Redis cache first
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            log.debug("Cache hit for: {}", shortCode);
            return cached;
        }

        // 2. Fallback to DB
        UrlMapping mapping = urlMappingRepository
            .findByShortCodeAndActiveTrue(shortCode)
            .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        if (mapping.isExpired()) {
            throw new UrlNotFoundException("Short URL has expired: " + shortCode);
        }

        // Repopulate cache
        cacheUrl(shortCode, mapping.getOriginalUrl());
        return mapping.getOriginalUrl();
    }

    @Transactional
    public void delete(String shortCode, String userId) {
        UrlMapping mapping = urlMappingRepository
            .findByShortCodeAndActiveTrue(shortCode)
            .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        if (!mapping.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this URL");
        }

        mapping.setActive(false);
        urlMappingRepository.save(mapping);
        redisTemplate.delete(CACHE_PREFIX + shortCode);
        log.info("Deleted short URL: {}", shortCode);
    }

    @Transactional
    public void incrementClick(String shortCode) {
        urlMappingRepository.incrementClickCount(shortCode);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = encodeBase62(System.nanoTime());
            if (++attempts > 10) throw new RuntimeException("Failed to generate unique short code");
        } while (urlMappingRepository.existsByShortCode(code));
        return code;
    }

    private String encodeBase62(long value) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < shortCodeLength) {
            sb.append(BASE62.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }

    private void cacheUrl(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl, cacheTtlSeconds, TimeUnit.SECONDS);
    }

    // ── Inner exception ───────────────────────────────────────────────────────

    public static class UrlNotFoundException extends RuntimeException {
        public UrlNotFoundException(String message) { super(message); }
    }
}
