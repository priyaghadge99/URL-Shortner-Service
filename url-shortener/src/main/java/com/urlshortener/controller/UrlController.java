package com.urlshortener.controller;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── POST /api/v1/urls — shorten a URL ──────────────────────────────────

    @PostMapping
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest req,
                                                    Principal principal) {
        String userId = principal != null ? principal.getName() : "anonymous";
        UrlMapping mapping = urlService.shorten(req.url(), userId, req.alias(), req.ttlDays());

        return ResponseEntity.status(HttpStatus.CREATED).body(new ShortenResponse(
            mapping.getShortCode(),
            baseUrl + "/" + mapping.getShortCode(),
            mapping.getOriginalUrl(),
            mapping.getExpiresAt() != null ? mapping.getExpiresAt().toString() : null
        ));
    }

    // ── DELETE /api/v1/urls/{code} ─────────────────────────────────────────

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode, Principal principal) {
        String userId = principal != null ? principal.getName() : "anonymous";
        urlService.delete(shortCode, userId);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/v1/urls/{code}/analytics ──────────────────────────────────

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsService.AnalyticsSummary> analytics(
        @PathVariable String shortCode,
        @RequestParam(defaultValue = "7d") String range) {

        return ResponseEntity.ok(analyticsService.getSummary(shortCode, range));
    }

    // ── DTOs ───────────────────────────────────────────────────────────────

    public record ShortenRequest(
        @NotBlank @URL String url,
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", message = "Alias: 3-20 alphanumeric chars") String alias,
        Integer ttlDays
    ) {}

    public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        String expiresAt
    ) {}
}
