package com.edstem.interviewprep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    public static final int CODE_MAX_LENGTH = 8;
    public static final int URL_MAX_LENGTH = 2048;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = CODE_MAX_LENGTH)
    private String code;

    @Column(nullable = false, length = URL_MAX_LENGTH)
    private String originalUrl;

    private LocalDate expiryDate;

    @Column(nullable = false)
    private long visitCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ShortUrl() {
    }

    public ShortUrl(String code, String originalUrl, LocalDate expiryDate, Instant createdAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
    }

    public boolean isExpiredOn(LocalDate date) {
        return expiryDate != null && date.isAfter(expiryDate);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public long getVisitCount() {
        return visitCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
