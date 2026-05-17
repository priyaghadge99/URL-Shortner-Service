# URL Shortener Service

A **Java Spring Boot** REST API that works like bit.ly or TinyURL. You provide a long URL and it returns a short code. Visiting that short code redirects you back to the original URL. The application runs fully containerized using **Docker Compose** with a **MySQL** database.

---

## Table of Contents

- [What This Project Does](#what-this-project-does)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [How It Works](#how-it-works)
- [Database Entity](#database-entity)
- [API Endpoints](#api-endpoints)
- [Docker Setup](#docker-setup)
- [Running the Project](#running-the-project)
- [Common Errors & Fixes](#common-errors--fixes)
- [Example Usage](#example-usage)

---

## What This Project Does

- Accepts a long URL and returns a short alphanumeric code
- Stores the mapping between the short code and original URL in MySQL
- Redirects any request to `/{shortCode}` to the original long URL
- Fully containerized — the app and database both run as Docker containers

---

## Tech Stack

| Layer          | Technology                  |
|----------------|-----------------------------|
| Language       | Java                        |
| Framework      | Spring Boot                 |
| REST Layer     | Spring MVC (`@RestController`) |
| ORM            | Spring Data JPA / Hibernate |
| Database       | MySQL 8                     |
| Containerization | Docker + Docker Compose   |
| Build Tool     | Maven                       |

---

## Project Structure

```
URL-Shortner-Service/
├── url-shortener/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/.../
│   │       │   ├── controller/
│   │       │   │   └── UrlController.java       ← REST endpoints
│   │       │   ├── service/
│   │       │   │   └── UrlShortenerService.java  ← Business logic
│   │       │   ├── repository/
│   │       │   │   └── UrlRepository.java        ← Spring Data JPA
│   │       │   └── model/
│   │       │       └── Url.java                  ← Database entity
│   │       └── resources/
│   │           └── application.properties        ← DB config
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── pom.xml
└── README.md
```

---

## Architecture Overview

```
HTTP Client (Browser / cURL / Postman)
            │
            ▼
    UrlController          ← Spring MVC REST Layer
    POST /shorten
    GET  /{shortCode}
            │
            ▼
    UrlShortenerService    ← Business Logic
    Generate code · Resolve URL
            │
            ▼
    UrlRepository          ← Spring Data JPA
            │
            ▼
    MySQL Database         ← Docker container on port 3306
```

Docker Compose manages two services:
- **app** — Spring Boot JAR, exposed on port `8080`
- **db** — MySQL 8, exposed on port `3306`

Both services communicate over an internal Docker network.

---

## How It Works

### Shortening a URL

1. Client sends `POST /shorten` with `{ "originalUrl": "https://some-long-url.com" }`
2. `UrlController` receives the request and calls the service layer
3. `UrlShortenerService` generates a unique short alphanumeric code (e.g. `ab3fG7`) using one of:
   - **UUID-based**: `UUID.randomUUID().toString().substring(0, 6)`
   - **Hash-based**: MurmurHash or MD5 on the original URL
   - **Base62 encoding**: converts the auto-incremented DB ID from base 10 to base 62 (`[0-9A-Za-z]`)
4. The short code and original URL are saved to MySQL via `UrlRepository`
5. Returns the short URL: `http://localhost:8080/ab3fG7`

### Redirecting

1. Client visits `GET /ab3fG7`
2. Service looks up the code in the database
3. Returns HTTP `302 Redirect` to the original long URL

---

## Database Entity

```java
@Entity
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalUrl;
    private String shortCode;
    private LocalDateTime createdAt;
}
```

---

## API Endpoints

| Method | Endpoint         | Description                          | Request Body                          | Response                     |
|--------|------------------|--------------------------------------|---------------------------------------|------------------------------|
| POST   | `/shorten`       | Shorten a long URL                   | `{ "originalUrl": "https://..." }`    | `{ "shortUrl": "http://localhost:8080/ab3fG7" }` |
| GET    | `/{shortCode}`   | Redirect to the original URL         | None                                  | `302 Redirect` to original URL |

---

## Docker Setup

The `docker-compose.yml` defines two services:

```yaml
services:
  db:
    image: mysql:8
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: urlshortener

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - db
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/urlshortener
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root
```

The `app` service depends on `db`, so MySQL starts first. Spring Boot connects to MySQL using the service name `db` as the hostname inside the Docker network.

---

## Running the Project

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- Java 17+ (only if running outside Docker)
- Maven (only if running outside Docker)

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/priyaghadge99/URL-Shortner-Service.git
cd URL-Shortner-Service/url-shortener

# 2. Build and start all containers
docker compose up --build

# 3. Access the API
# http://localhost:8080
```

---

## Common Errors & Fixes

### Port 3306 Already in Use

```
Error: ports are not available: exposing port TCP 0.0.0.0:3306
```

This means MySQL is already running on your machine. Fix options:

**Option A — Stop local MySQL:**
```bash
# Windows
net stop MySQL

# Linux
sudo systemctl stop mysql

# Mac
brew services stop mysql
```

**Option B — Change the host port in `docker-compose.yml`:**
```yaml
ports:
  - "3307:3306"   # use 3307 on your machine instead
```

Then connect using port `3307`.

**Option C — Find and kill the process using port 3306:**
```bash
# Windows
netstat -ano | findstr :3306
taskkill /PID <PID> /F

# Linux/Mac
sudo lsof -i :3306
sudo kill -9 <PID>
```

---

## Example Usage

### Shorten a URL

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://www.google.com"}'
```

**Response:**
```json
{
  "shortUrl": "http://localhost:8080/ab3fG7"
}
```

### Use the Short URL

```bash
# Follow the redirect automatically
curl -L http://localhost:8080/ab3fG7

# Or open it in a browser — it will redirect to https://www.google.com
```

---

## Author

**Priya Ghadge** — [github.com/priyaghadge99](https://github.com/priyaghadge99)
