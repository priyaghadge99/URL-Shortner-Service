# URL Shortener Service — Full Interview Explanation
## Java 8+ Features · @Transactional · Redis · Kafka

---

## 1. Project Overview (How to Introduce It)

> *"I built a production-grade URL Shortener Service using Spring Boot. The core idea is simple — shorten a long URL, redirect users via the short code. But the architecture goes deeper. I used Redis for sub-millisecond caching on the hot redirect path, Kafka to decouple click analytics from redirect latency, MySQL as the persistent store with Spring Data JPA, and Docker Compose to wire everything together. I also used several modern Java features like records, sealed pattern with switch expressions, streams, CompletableFuture, and method references."*

---

## 2. Java 8+ Features Used — File by File

---

### 2.1 Java Records (Java 16+)

**Where used:** `UrlController.java`, `AnalyticsService.java`, `DeviceEnricher.java`

```java
// UrlController.java
public record ShortenRequest(
    @NotBlank @URL String url,
    @Pattern(...) String alias,
    Integer ttlDays
) {}

public record ShortenResponse(
    String shortCode,
    String shortUrl,
    String originalUrl,
    String expiresAt
) {}

// AnalyticsService.java
public record AnalyticsSummary(
    String shortCode,
    long totalClicks,
    String range,
    List<Map<String, Object>> clicksOverTime,
    ...
) {}

// DeviceEnricher.java
public record DeviceInfo(String deviceType, String browser, String os) {}
```

**How to explain in interview:**
> *"Records are immutable data carriers introduced in Java 16. They auto-generate constructor, getters, equals, hashCode, and toString. I used them as DTOs — ShortenRequest for incoming API requests, ShortenResponse for responses, AnalyticsSummary for analytics data, and DeviceInfo for parsed user-agent info. They are perfect as DTOs because once a request comes in, you don't want to mutate it."*

---

### 2.2 Switch Expressions (Java 14+)

**Where used:** `AnalyticsService.java`

```java
private Instant parseSince(String range) {
    return switch (range) {
        case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
        case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
        default    -> Instant.now().minus(7,  ChronoUnit.DAYS);
    };
}
```

**How to explain in interview:**
> *"Switch expressions in Java 14 allow returning a value directly from the switch using the arrow syntax. No break statements, no fall-through issues. Here I'm parsing the analytics time range — '7d', '30d', '90d' — and returning an Instant offset from now. It's cleaner than a traditional switch with a local variable and multiple assignments."*

---

### 2.3 Stream API + .toList() (Java 16+)

**Where used:** `AnalyticsService.java`, `GlobalExceptionHandler.java`

```java
// AnalyticsService.java
private List<Map<String, Object>> toTimeSeriesMap(List<Object[]> rows) {
    return rows.stream()
        .map(r -> Map.of("date", r[0].toString(), "clicks", r[1]))
        .toList();  // Java 16+ — returns unmodifiable list directly
}

private List<Map<String, Object>> toCountMap(List<Object[]> rows) {
    return rows.stream()
        .map(r -> Map.of("label", r[0] == null ? "Unknown" : r[0].toString(), "count", r[1]))
        .toList();
}

// GlobalExceptionHandler.java
String msg = ex.getBindingResult().getFieldErrors().stream()
    .map(e -> e.getField() + ": " + e.getDefaultMessage())
    .findFirst()
    .orElse("Validation failed");
```

**How to explain in interview:**
> *"Stream API from Java 8 is used extensively. I chain .stream() → .map() → .toList() to convert raw DB query results (Object arrays) into clean maps for the API response. The .toList() method in Java 16 is a terminal operation that returns an unmodifiable list — shorter and cleaner than Collectors.toList(). For validation errors, I use .findFirst().orElse() which is lazy — it stops at the first error instead of collecting all of them."*

---

### 2.4 Optional (Java 8+)

**Where used:** `UrlService.java`, `UrlMappingRepository.java`

```java
// UrlService.java
UrlMapping mapping = urlMappingRepository
    .findByShortCodeAndActiveTrue(shortCode)
    .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

// Check alias presence
if (urlMappingRepository.findByCustomAlias(customAlias).isPresent()) {
    throw new IllegalArgumentException("Custom alias already in use: " + customAlias);
}
```

**How to explain in interview:**
> *"Spring Data JPA returns Optional from repository methods. Instead of checking for null, I use .orElseThrow() which throws a meaningful exception if the record doesn't exist. This avoids NullPointerException and makes the intent clear — 'give me the value or throw.' The .isPresent() check is used to detect duplicate aliases before saving."*

---

### 2.5 CompletableFuture + Lambda (Java 8+)

**Where used:** `AnalyticsProducer.java`

```java
@Async
public void publishClickEvent(ClickEvent event) {
    CompletableFuture<SendResult<String, ClickEvent>> future =
        kafkaTemplate.send(clickEventsTopic, event.getShortCode(), event);

    future.whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to publish click event for {}: {}", 
                event.getShortCode(), ex.getMessage());
        } else {
            log.debug("Click event published for {} at offset {}",
                event.getShortCode(),
                result.getRecordMetadata().offset());
        }
    });
}
```

**How to explain in interview:**
> *"KafkaTemplate.send() returns a CompletableFuture — it doesn't block. I attach a .whenComplete() callback using a lambda. This callback fires when Kafka either confirms the message or fails — without blocking the main thread. The @Async annotation runs this entire method on a separate thread from the ThreadPoolTaskExecutor I configured, so the redirect response is returned to the user immediately while Kafka publishing happens in the background."*

---

### 2.6 Method References + Functional Interfaces (Java 8+)

**Where used:** `SecurityConfig.java`, `AppConfig.java`

```java
// SecurityConfig.java
.csrf(AbstractHttpConfigurer::disable)

// AppConfig.java
exec.setThreadNamePrefix("analytics-");
```

**How to explain in interview:**
> *"Method references like AbstractHttpConfigurer::disable are shorthand for a lambda that calls that method. It's equivalent to writing http -> http.disable(). Spring Security's lambda DSL uses functional interfaces throughout, so method references make the security config very concise and readable."*

---

### 2.7 @Builder Pattern (Lombok + Java)

**Where used:** `UrlMapping.java`, `ClickStat.java`, `ClickEvent.java`, `UrlService.java`

```java
// UrlService.java
UrlMapping mapping = UrlMapping.builder()
    .shortCode(shortCode)
    .originalUrl(originalUrl)
    .userId(userId)
    .customAlias(customAlias)
    .expiresAt(expiresAt)
    .active(true)
    .build();

// RedirectController.java
analyticsProducer.publishClickEvent(ClickEvent.builder()
    .shortCode(shortCode)
    .timestamp(Instant.now())
    .ipAddress(getClientIp(request))
    .userAgent(request.getHeader("User-Agent"))
    .build());
```

**How to explain in interview:**
> *"Lombok's @Builder generates a builder pattern. Instead of a constructor with 8 parameters, you use a fluent API. This is especially useful for ClickEvent where some fields are set at creation time (shortCode, IP) and others are enriched later (deviceType, browser, os) by the Kafka consumer. It also avoids constructor argument ordering mistakes."*

---

### 2.8 Text Blocks / Multiline Strings (Java 15+)

**Where used:** `ClickStatRepository.java`

```java
@Query("""
    SELECT DATE(c.timestamp) as day, COUNT(c) as clicks
    FROM ClickStat c
    WHERE c.shortCode = :code AND c.timestamp >= :since
    GROUP BY DATE(c.timestamp)
    ORDER BY DATE(c.timestamp)
    """)
List<Object[]> clicksPerDay(...);
```

**How to explain in interview:**
> *"Text blocks (Java 15) allow multi-line strings without string concatenation or escape characters. I used them for JPQL queries in the repository. It makes complex queries very readable — you can see the SQL structure clearly without all the + signs and \n escape sequences."*

---

### 2.9 Instant + ChronoUnit (java.time — Java 8+)

**Where used:** `UrlService.java`, `UrlMapping.java`, `CleanupService.java`

```java
// UrlService.java
Instant expiresAt = Instant.now().plus(
    ttlDays != null ? ttlDays : defaultTtlDays, ChronoUnit.DAYS
);

// UrlMapping.java
public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
}
```

**How to explain in interview:**
> *"I used the java.time API instead of the old Date/Calendar. Instant represents a point in UTC time — perfect for storing timestamps in the database. ChronoUnit.DAYS gives clean arithmetic. The isExpired() method on the entity itself is a domain method — it keeps expiry logic close to the data it belongs to."*

---

## 3. @Transactional — Deep Explanation

**Where used:** `UrlService.java`, `AnalyticsConsumer.java`, `CleanupService.java`

### In UrlService.java:

```java
@Transactional
public UrlMapping shorten(String originalUrl, String userId, String customAlias, Integer ttlDays) {
    // 1. Check alias uniqueness
    // 2. Generate short code
    // 3. Save to DB
    // 4. Cache in Redis
}

@Transactional(readOnly = true)
public String resolve(String shortCode) {
    // Read-only — no DB writes, Hibernate flush is skipped
}

@Transactional
public void delete(String shortCode, String userId) {
    // Soft delete — sets active = false
    // Removes from Redis cache
}
```

### What to say in an interview:

> *"@Transactional wraps the method in a database transaction. If any exception is thrown inside, the entire operation rolls back — no partial saves. For example in shorten(), if saving to the DB succeeds but some other step throws an exception, the whole record is rolled back.*
>
> *I used @Transactional(readOnly = true) on the resolve() method. This tells Hibernate to skip the dirty-checking flush at the end of the transaction, which is a performance optimization. Hibernate normally checks every loaded entity for changes before committing — readOnly=true disables that.*
>
> *@Transactional works via Spring AOP — it wraps the method in a proxy. This is why you should never call a @Transactional method from within the same class (self-invocation) — the proxy is bypassed and the transaction won't apply.*
>
> *By default, @Transactional only rolls back on unchecked exceptions (RuntimeException and Error). Checked exceptions don't trigger rollback unless you explicitly say rollbackFor = Exception.class.*
>
> *In AnalyticsConsumer, I also annotated the consume() method with @Transactional — this wraps the Kafka message processing. If the DB save fails, the transaction rolls back and I don't call ack.acknowledge(), so Kafka redelivers the message. This gives at-least-once processing guarantee."*

---

## 4. Redis — Deep Explanation

**Where used:** `UrlService.java`, `AnalyticsConsumer.java`

### The Caching Strategy:

```java
// UrlService.java — WRITE: cache on shorten
private void cacheUrl(String shortCode, String originalUrl) {
    redisTemplate.opsForValue().set(
        "url:" + shortCode, originalUrl, cacheTtlSeconds, TimeUnit.SECONDS
    );
}

// UrlService.java — READ: cache-aside pattern
public String resolve(String shortCode) {
    // 1. Check Redis first
    String cached = redisTemplate.opsForValue().get("url:" + shortCode);
    if (cached != null) return cached;  // Cache HIT — sub-millisecond

    // 2. Cache MISS — go to DB
    UrlMapping mapping = urlMappingRepository
        .findByShortCodeAndActiveTrue(shortCode)
        .orElseThrow(() -> new UrlNotFoundException(...));

    // 3. Re-populate cache
    cacheUrl(shortCode, mapping.getOriginalUrl());
    return mapping.getOriginalUrl();
}

// UrlService.java — DELETE: evict from cache
redisTemplate.delete("url:" + shortCode);
```

### Analytics Counters in Redis:

```java
// AnalyticsConsumer.java
// Atomic increment — no race conditions
redisTemplate.opsForValue().increment("clicks:total:" + shortCode);

// Hash: key = shortCode, field = country, value = count
redisTemplate.opsForHash().increment("clicks:country:" + shortCode, event.getCountry(), 1);

// Hash: key = shortCode, field = deviceType, value = count
redisTemplate.opsForHash().increment("clicks:device:" + shortCode, event.getDeviceType(), 1);
```

### What to say in an interview:

> *"The redirect path is the hottest path in this application — it must be as fast as possible. A MySQL query even with an index takes 5–20ms. Redis lookup takes under 1ms. So I implemented a cache-aside pattern: on every shorten operation, I write the mapping to Redis with a 1-hour TTL. On every redirect, I check Redis first. If it's a cache hit, I skip the database entirely.*
>
> *The cache key is 'url:{shortCode}' — I use a prefix to namespace keys so they don't collide with other data in Redis. TTL is 1 hour by default, configurable via application.yml.*
>
> *For delete, I call redisTemplate.delete() to evict the key so stale data doesn't get served after a URL is deactivated.*
>
> *For analytics, I use Redis atomic increment operations for real-time counters. Redis INCR is atomic — if two clicks come in simultaneously, they won't interfere. I store country and device breakdowns as Redis Hashes: the key is the shortCode, the field is the country name, and the value is the count. This allows O(1) reads for any breakdown.*
>
> *StringRedisTemplate is used instead of RedisTemplate because all my values are strings — it avoids the need for explicit serializer configuration."*

---

## 5. Kafka — Deep Explanation

**Where used:** `AnalyticsProducer.java`, `AnalyticsConsumer.java`, `AppConfig.java`

### Topic Configuration:

```java
// AppConfig.java
@Bean
public NewTopic clickEventsTopic() {
    return TopicBuilder.name("click-events")
        .partitions(6)   // partitioned by shortCode
        .replicas(1)
        .build();
}
```

### Producer:

```java
// AnalyticsProducer.java
@Async
public void publishClickEvent(ClickEvent event) {
    CompletableFuture<SendResult<String, ClickEvent>> future =
        kafkaTemplate.send(clickEventsTopic, event.getShortCode(), event);

    future.whenComplete((result, ex) -> { ... });
}
```

### Consumer:

```java
// AnalyticsConsumer.java
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
        // Enrich → Save to DB → Increment Redis → Acknowledge offset
        ack.acknowledge();
    } catch (Exception e) {
        // Do NOT acknowledge — Kafka will redeliver
    }
}
```

### Manual Offset Commit Configuration:

```java
// AppConfig.java
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // manual commit
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
```

### What to say in an interview:

> *"The key design decision here is decoupling. When a user hits a short URL, the response time must be fast — Redis gives us the original URL in under 1ms. But we also need to record analytics — device type, country, referrer, click count. Doing that DB write synchronously would add 10–20ms to every redirect.*
>
> *So I separated the redirect path from the analytics path using Kafka. The RedirectController resolves the URL from Redis, fires a ClickEvent to Kafka asynchronously using @Async + CompletableFuture, and immediately returns the 302 redirect. The user gets redirected fast. Kafka delivers the event to the AnalyticsConsumer in the background.*
>
> *The Kafka topic 'click-events' has 6 partitions, and the producer uses the shortCode as the partition key. This means all clicks for the same URL go to the same partition — ordering is preserved per URL.*
>
> *The consumer uses manual offset commit (MANUAL_IMMEDIATE). Kafka does not advance the offset until ack.acknowledge() is explicitly called. This guarantees at-least-once delivery — if the DB save fails, we don't acknowledge, and Kafka redelivers the same message. The consumer runs with concurrency=3, meaning 3 threads process messages in parallel from different partitions.*
>
> *The consumer is also @Transactional — if saving ClickStat to DB fails mid-way, the DB transaction rolls back and the offset is not committed. This prevents partial processing.*
>
> *AUTO_OFFSET_RESET is set to 'earliest' — if the consumer group starts fresh or loses its offset, it re-reads from the beginning of the topic."*

---

## 6. Complete Request Flow (Tell as a Story)

> *"Let me walk you through what happens when a user visits a short URL like http://localhost:8080/ab3fG7:*
>
> *1. The GET /{shortCode} request hits RedirectController.*
> *2. It calls urlService.resolve('ab3fG7').*
> *3. UrlService checks Redis first — key 'url:ab3fG7'. If it's a cache hit, we have the original URL in under 1ms.*
> *4. If it's a cache miss, we query MySQL using Spring Data JPA. If found and not expired, we repopulate the Redis cache.*
> *5. Back in the controller, we build a ClickEvent using the builder pattern — shortCode, IP, User-Agent, timestamp.*
> *6. analyticsProducer.publishClickEvent() is called — this runs on a separate thread (@Async) and sends the event to Kafka topic 'click-events' with shortCode as the partition key.*
> *7. The controller immediately returns ResponseEntity with status 302 and Location header pointing to the original URL.*
> *8. Meanwhile, AnalyticsConsumer receives the ClickEvent from Kafka. It parses the User-Agent using DeviceEnricher to extract device type, browser, OS. It saves a ClickStat record to MySQL. It increments the Redis counter. Only then does it call ack.acknowledge() to commit the Kafka offset.*
>
> *The entire redirect is sub-5ms from the user's perspective. Analytics processing happens asynchronously and doesn't affect user experience."*

---

## 7. Other Notable Patterns

### Soft Delete
```java
// UrlService.delete()
mapping.setActive(false);  // never actually deletes the row
urlMappingRepository.save(mapping);
redisTemplate.delete("url:" + shortCode);
```
> *"I used soft delete — instead of physically removing the row, I set active=false. This preserves history and click analytics. All queries use findByShortCodeAndActiveTrue() so inactive URLs are automatically excluded."*

### Scheduled Cleanup
```java
// CleanupService.java
@Scheduled(fixedRateString = "PT1H")
@Transactional
public void deactivateExpiredUrls() {
    int count = urlMappingRepository.deactivateExpiredUrls(Instant.now());
}
```
> *"CleanupService runs hourly using @Scheduled with ISO-8601 duration format PT1H. It runs a bulk UPDATE to deactivate all URLs where expiresAt < now. This is more efficient than loading entities one by one — a single SQL UPDATE."*

### Global Exception Handling with ProblemDetail
```java
// GlobalExceptionHandler.java
@ExceptionHandler(UrlService.UrlNotFoundException.class)
public ProblemDetail handleNotFound(UrlService.UrlNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setProperty("timestamp", Instant.now());
    return pd;
}
```
> *"I used @RestControllerAdvice with ProblemDetail — a Spring 6 / RFC 7807 standard for structured error responses. Instead of custom error objects, ProblemDetail provides a standard format with type, status, detail, and timestamp fields. All exceptions are caught centrally — no try/catch in controllers."*

### Unit Testing with Mockito
```java
// UrlServiceTest.java
@ExtendWith(MockitoExtension.class)
void resolve_shouldReturnCachedUrl() {
    when(valueOps.get("url:abc123")).thenReturn("https://example.com");
    String url = urlService.resolve("abc123");
    verify(urlMappingRepository, never()).findByShortCodeAndActiveTrue(any());
}
```
> *"Tests use Mockito to mock the repository and Redis template. The cache test verifies that when Redis returns a value, the DB is never called — this confirms the cache-aside pattern works correctly. I use assertThat from AssertJ for fluent assertions."*

---

## 8. Quick-Fire Interview Q&A

**Q: Why Redis instead of just using MySQL?**
> "MySQL indexed read is ~10ms. Redis is ~0.1ms. For a URL redirector that handles millions of requests, that 100x difference matters enormously."

**Q: Why Kafka instead of just writing analytics to DB directly?**
> "Synchronous DB write would add latency to every redirect. Kafka decouples it — redirect is fast, analytics is eventual. Also, Kafka gives durability: if analytics processing fails, messages can be reprocessed."

**Q: What is the difference between @Transactional and @Transactional(readOnly=true)?**
> "readOnly=true tells Hibernate to skip dirty checking at flush time and allows the DB driver to optimize read-only transactions. It does NOT prevent writes at the JDBC level — it's a hint for optimization."

**Q: What happens if Kafka is down when a user visits a short URL?**
> "The redirect still works — Kafka is only used for analytics, not the core redirect path. @Async publishing with CompletableFuture handles Kafka failures gracefully via .whenComplete() error logging. The user is never affected."

**Q: What is at-least-once delivery?**
> "Messages are guaranteed to be processed at least once. If the consumer crashes after processing but before committing the offset, Kafka redelivers the message. The consumer may see the same message twice, so operations should be idempotent — or we can check for duplicates."

**Q: How do you prevent cache and DB getting out of sync?**
> "On delete, I evict the key from Redis immediately. On shorten, I write to both DB and Redis in the same @Transactional method. The TTL ensures stale keys expire automatically. There is a small window for inconsistency, which is acceptable for a URL redirector."
