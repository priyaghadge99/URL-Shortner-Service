package com.urlshortener.controller;

import com.urlshortener.analytics.AnalyticsProducer;
import com.urlshortener.event.ClickEvent;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    private final UrlService urlService;
    private final AnalyticsProducer analyticsProducer;

    /**
     * Hot path — must be as fast as possible.
     * 1. Redis lookup (sub-millisecond)
     * 2. Fire async Kafka event (non-blocking)
     * 3. Return 302
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          HttpServletRequest request) {
        try {
            String originalUrl = urlService.resolve(shortCode);

            // Publish analytics event — async, does NOT block the response
            analyticsProducer.publishClickEvent(ClickEvent.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .timestamp(Instant.now())
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .build());

            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();

        } catch (UrlService.UrlNotFoundException ex) {
            log.warn("Short URL not found: {}", shortCode);
            return ResponseEntity.notFound().build();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
