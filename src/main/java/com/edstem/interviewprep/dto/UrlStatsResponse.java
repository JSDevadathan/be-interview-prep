package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.ShortUrl;
import java.time.Instant;
import java.time.LocalDate;

public record UrlStatsResponse(
        String code,
        String originalUrl,
        long visitCount,
        Instant createdAt,
        LocalDate expiryDate) {

    public static UrlStatsResponse from(ShortUrl shortUrl) {
        return new UrlStatsResponse(
                shortUrl.getCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getVisitCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiryDate());
    }
}
