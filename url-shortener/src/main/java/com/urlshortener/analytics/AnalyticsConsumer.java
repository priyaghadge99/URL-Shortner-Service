package com.urlshortener.analytics;

import com.urlshortener.event.ClickEvent;
import com.urlshortener.model.ClickStat;
import com.urlshortener.repository.ClickStatRepository;
import com.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private static final String CLICKS_KEY      = "clicks:total:";
    private static final String COUNTRY_KEY     = "clicks:country:";
    private static final String DEVICE_KEY      = "clicks:device:";

    private final ClickStatRepository clickStatRepository;
    private final UrlService urlService;
    private final StringRedisTemplate redisTemplate;
    private final DeviceEnricher deviceEnricher;

    /**
     * Consumes click events from Kafka, enriches them with device/geo info,
     * persists to DB, and increments Redis real-time counters.
     *
     * Manual offset commit (Acknowledgment) — offset acknowledged only after
     * successful processing, guaranteeing at-least-once delivery.
     */
    @KafkaListener(
        topics = "${kafka.topics.click-events:click-events}",
        groupId = "analytics-group",
        containerFactory = "analyticsKafkaListenerFactory"
    )
    @Transactional
    public void consume(ClickEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        try {
            log.debug("Processing click event: code={} partition={} offset={}", event.getShortCode(), partition, offset);

            // Enrich with device/browser info from User-Agent
            DeviceEnricher.DeviceInfo deviceInfo = deviceEnricher.parse(event.getUserAgent());
            event.setDeviceType(deviceInfo.deviceType());
            event.setBrowser(deviceInfo.browser());
            event.setOs(deviceInfo.os());

            // Persist to analytics DB
            ClickStat stat = ClickStat.builder()
                .shortCode(event.getShortCode())
                .timestamp(event.getTimestamp())
                .ipAddress(event.getIpAddress())
                .country(event.getCountry())
                .city(event.getCity())
                .deviceType(event.getDeviceType())
                .browser(event.getBrowser())
                .os(event.getOs())
                .referer(event.getReferer())
                .build();
            clickStatRepository.save(stat);

            // Increment DB click count
            urlService.incrementClick(event.getShortCode());

            // Increment Redis real-time counters (atomic)
            redisTemplate.opsForValue().increment(CLICKS_KEY + event.getShortCode());

            if (event.getCountry() != null) {
                redisTemplate.opsForHash().increment(
                    COUNTRY_KEY + event.getShortCode(), event.getCountry(), 1
                );
            }
            if (event.getDeviceType() != null) {
                redisTemplate.opsForHash().increment(
                    DEVICE_KEY + event.getShortCode(), event.getDeviceType(), 1
                );
            }

            // Acknowledge offset only after successful processing
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process click event for {}: {}", event.getShortCode(), e.getMessage(), e);
            // Do NOT acknowledge — Kafka will redeliver
        }
    }
}
