# URL Shortener Service

Spring Boot 3.2 · Java 17 · Kafka · Redis · MySQL · Docker

## Tech Stack

| Layer       | Technology                        |
|-------------|-----------------------------------|
| Framework   | Spring Boot 3.2, Spring MVC       |
| Security    | Spring Security (Basic Auth)      |
| Persistence | Spring Data JPA + MySQL 8         |
| Cache       | Redis (hot URL lookup)            |
| Messaging   | Apache Kafka (KRaft mode)         |
| Testing     | JUnit 5, Mockito                  |

## Quick Start

### 1. Start infrastructure
```bash
docker-compose up -d
```

### 2. Run the application
```bash
./mvnw spring-boot:run
```

App starts on `http://localhost:8080`

---

## API Reference

### Shorten a URL
```http
POST /api/v1/urls
Authorization: Basic cHJpeWE6cGFzc3dvcmQxMjM=
Content-Type: application/json

{
  "url": "https://github.com/priya/real-time-orders",
  "alias": "my-project",   // optional
  "ttlDays": 30            // optional, default 365
}
```
**Response:**
```json
{
  "shortCode": "my-project",
  "shortUrl":  "http://localhost:8080/my-project",
  "originalUrl": "https://github.com/priya/real-time-orders",
  "expiresAt": "2026-06-17T..."
}
```

---

### Redirect (public)
```http
GET /my-project
→ 302 https://github.com/priya/real-time-orders
```

---

### Get Analytics
```http
GET /api/v1/urls/my-project/analytics?range=7d
Authorization: Basic ...
```
**Response:**
```json
{
  "shortCode": "my-project",
  "totalClicks": 482,
  "range": "7d",
  "clicksOverTime": [{"date":"2026-05-10","clicks":67}, ...],
  "topCountries":   [{"label":"India","count":210}, ...],
  "deviceBreakdown":[{"label":"MOBILE","count":270}, ...],
  "topReferrers":   [{"label":"linkedin.com","count":120}, ...]
}
```

---

### Delete a URL
```http
DELETE /api/v1/urls/my-project
Authorization: Basic ...
→ 204 No Content
```

---

## Project Structure

```
src/main/java/com/urlshortener/
├── UrlShortenerApplication.java
├── controller/
│   ├── RedirectController.java     # GET /{code} → 302
│   └── UrlController.java          # POST/DELETE/analytics REST API
├── service/
│   ├── UrlService.java             # Shorten, resolve, delete, Base62 encoding
│   └── CleanupService.java         # Hourly TTL expiry job
├── analytics/
│   ├── AnalyticsProducer.java      # Async Kafka publisher
│   ├── AnalyticsConsumer.java      # Kafka listener → DB + Redis counters
│   ├── AnalyticsService.java       # Query aggregations for dashboard
│   └── DeviceEnricher.java         # UA-string → device/browser/OS
├── model/
│   ├── UrlMapping.java             # JPA entity — URL mappings table
│   └── ClickStat.java              # JPA entity — click_stats table
├── event/
│   └── ClickEvent.java             # Kafka message payload
├── repository/
│   ├── UrlMappingRepository.java
│   └── ClickStatRepository.java
└── config/
    ├── AppConfig.java              # Kafka topics, consumer factory, async executor
    ├── SecurityConfig.java         # Spring Security rules
    └── GlobalExceptionHandler.java # RFC 9457 ProblemDetail responses
```

## Key Design Decisions

- **302 redirect** — ensures every click hits the server for analytics counting
- **Redis cache-aside** — hot URLs served in <1ms without DB hit
- **Async Kafka publishing** — redirect latency unaffected by analytics writes
- **Manual offset commit** — Kafka offset committed only after successful DB write (at-least-once guarantee)
- **Base62 encoding** — 6-char codes give 56 billion unique URLs
- **Partition by shortCode** — per-URL event ordering preserved in Kafka

## Default Credentials (dev only)

| User  | Password     | Role       |
|-------|--------------|------------|
| priya | password123  | USER       |
| admin | admin123     | USER,ADMIN |
