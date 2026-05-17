package com.urlshortener.config;

import com.urlshortener.event.ClickEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@EnableKafka
public class AppConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Kafka topic ────────────────────────────────────────────────────────

    @Bean
    public NewTopic clickEventsTopic() {
        return TopicBuilder.name("click-events")
            .partitions(6)   // partition by shortCode
            .replicas(1)
            .build();
    }

    // ── Kafka consumer with manual offset commit ───────────────────────────

    @Bean
    public ConsumerFactory<String, ClickEvent> analyticsConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // manual commit

        JsonDeserializer<ClickEvent> valueDeserializer = new JsonDeserializer<>(ClickEvent.class);
        valueDeserializer.addTrustedPackages("com.urlshortener.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClickEvent> analyticsKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ClickEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(analyticsConsumerFactory());
        factory.setConcurrency(3);
        // MANUAL_IMMEDIATE — offset committed only when Acknowledgment.acknowledge() is called
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    // ── Async executor for fire-and-forget analytics publishing ───────────

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("analytics-");
        exec.initialize();
        return exec;
    }
}
