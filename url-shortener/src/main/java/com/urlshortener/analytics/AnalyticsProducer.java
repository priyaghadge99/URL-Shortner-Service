package com.urlshortener.analytics;

import com.urlshortener.event.ClickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsProducer {

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;

    @Value("${kafka.topics.click-events:click-events}")
    private String clickEventsTopic;

    /**
     * Publishes a click event asynchronously so redirect latency is unaffected.
     * Partitioned by shortCode — all events for one URL go to the same partition,
     * preserving ordering per URL.
     */
    @Async
    public void publishClickEvent(ClickEvent event) {
        CompletableFuture<SendResult<String, ClickEvent>> future =
            kafkaTemplate.send(clickEventsTopic, event.getShortCode(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish click event for {}: {}", event.getShortCode(), ex.getMessage());
            } else {
                log.debug("Click event published for {} at offset {}",
                    event.getShortCode(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
