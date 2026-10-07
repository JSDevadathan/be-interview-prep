package com.edstem.interviewprep.service;

import com.edstem.interviewprep.common.error.ResourceGoneException;
import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.dto.ShortUrlResponse;
import com.edstem.interviewprep.dto.ShortenUrlRequest;
import com.edstem.interviewprep.dto.UrlStatsResponse;
import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlShortenerService {

    private static final String RESOURCE_NAME = "Short URL";
    private static final String CODE_IDENTIFIER = "code";
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final ChronoUnit DATABASE_TIMESTAMP_PRECISION = ChronoUnit.MICROS;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository, ShortCodeGenerator codeGenerator, Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public ShortUrlResponse shorten(ShortenUrlRequest request, String baseUrl) {
        ShortUrl shortUrl = new ShortUrl(
                generateUniqueCode(),
                request.url(),
                request.expiryDate() == null ? null : LocalDate.parse(request.expiryDate()),
                Instant.now(clock).truncatedTo(DATABASE_TIMESTAMP_PRECISION));
        return ShortUrlResponse.from(shortUrlRepository.save(shortUrl), baseUrl);
    }

    @Transactional
    public String resolveAndCountVisit(String code) {
        ShortUrl shortUrl = findByCode(code);
        if (shortUrl.isExpiredOn(LocalDate.now(clock))) {
            throw new ResourceGoneException(
                    "Short URL with code %s expired on %s".formatted(code, shortUrl.getExpiryDate()));
        }
        shortUrlRepository.incrementVisitCount(shortUrl.getId());
        return shortUrl.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse stats(String code) {
        return UrlStatsResponse.from(findByCode(code));
    }

    private String generateUniqueCode() {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!shortUrlRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique short code after %d attempts".formatted(MAX_CODE_ATTEMPTS));
    }

    private ShortUrl findByCode(String code) {
        return shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, CODE_IDENTIFIER, code));
    }
}
