package com.urlshortener.event;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEvent {

    private String shortCode;
    private String originalUrl;
    private Instant timestamp;

    // Request metadata
    private String ipAddress;
    private String userAgent;
    private String referer;

    // Enriched (filled by consumer)
    private String country;
    private String city;
    private String deviceType;   // MOBILE, DESKTOP, TABLET
    private String browser;
    private String os;
}
