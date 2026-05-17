package com.urlshortener.analytics;

import com.urlshortener.repository.ClickStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickStatRepository clickStatRepository;

    public AnalyticsSummary getSummary(String shortCode, String range) {
        Instant since = parseSince(range);

        List<Map<String, Object>> clicksOverTime = toTimeSeriesMap(
            clickStatRepository.clicksPerDay(shortCode, since)
        );
        List<Map<String, Object>> topCountries = toCountMap(
            clickStatRepository.topCountries(shortCode)
        );
        List<Map<String, Object>> deviceBreakdown = toCountMap(
            clickStatRepository.deviceBreakdown(shortCode)
        );
        List<Map<String, Object>> topReferrers = toCountMap(
            clickStatRepository.topReferrers(shortCode)
        );
        long totalClicks = clickStatRepository.countByShortCode(shortCode);

        return new AnalyticsSummary(
            shortCode, totalClicks, range,
            clicksOverTime, topCountries, deviceBreakdown, topReferrers
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Instant parseSince(String range) {
        return switch (range) {
            case "30d"  -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d"  -> Instant.now().minus(90, ChronoUnit.DAYS);
            default     -> Instant.now().minus(7,  ChronoUnit.DAYS);
        };
    }

    private List<Map<String, Object>> toTimeSeriesMap(List<Object[]> rows) {
        return rows.stream()
            .map(r -> Map.of("date", r[0].toString(), "clicks", r[1]))
            .toList();
    }

    private List<Map<String, Object>> toCountMap(List<Object[]> rows) {
        return rows.stream()
            .map(r -> Map.of("label", r[0] == null ? "Unknown" : r[0].toString(), "count", r[1]))
            .toList();
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    public record AnalyticsSummary(
        String shortCode,
        long totalClicks,
        String range,
        List<Map<String, Object>> clicksOverTime,
        List<Map<String, Object>> topCountries,
        List<Map<String, Object>> deviceBreakdown,
        List<Map<String, Object>> topReferrers
    ) {}
}
