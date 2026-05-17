package com.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "click_stats", indexes = {
    @Index(name = "idx_cs_short_code", columnList = "shortCode"),
    @Index(name = "idx_cs_timestamp",  columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String shortCode;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 50)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String deviceType;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String os;

    @Column(length = 500)
    private String referer;
}
