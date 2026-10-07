package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.ShortUrl;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.util.UriComponentsBuilder;

public record ShortUrlResponse(
        String code,
        String shortUrl,
        String originalUrl,
        LocalDate expiryDate,
        Instant createdAt) {

    public static ShortUrlResponse from(ShortUrl shortUrl, String baseUrl) {
        String link = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(shortUrl.getCode())
                .toUriString();
        return new ShortUrlResponse(
                shortUrl.getCode(),
                link,
                shortUrl.getOriginalUrl(),
                shortUrl.getExpiryDate(),
                shortUrl.getCreatedAt());
    }
}
