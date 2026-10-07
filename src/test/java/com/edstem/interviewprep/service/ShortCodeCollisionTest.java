package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.edstem.interviewprep.dto.ShortenUrlRequest;
import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.repository.ShortUrlRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ShortCodeCollisionTest {

    private static final String TAKEN_CODE = "taken01";
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final String BASE_URL = "http://localhost";
    private static final ShortenUrlRequest REQUEST = new ShortenUrlRequest("https://example.com/new", null);

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @MockitoBean
    private ShortCodeGenerator codeGenerator;

    @BeforeEach
    void saveLinkWithTakenCode() {
        shortUrlRepository.save(new ShortUrl(TAKEN_CODE, "https://example.com/old", null, CREATED_AT));
    }

    @AfterEach
    void deleteAllShortUrls() {
        shortUrlRepository.deleteAll();
    }

    @Test
    void retriesWithANewCodeWhenTheGeneratedCodeIsTaken() {
        given(codeGenerator.generate()).willReturn(TAKEN_CODE, "fresh01");

        assertThat(urlShortenerService.shorten(REQUEST, BASE_URL).code()).isEqualTo("fresh01");
    }

    @Test
    void failsAfterRepeatedCollisionsInsteadOfLoopingForever() {
        given(codeGenerator.generate()).willReturn(TAKEN_CODE);

        assertThatThrownBy(() -> urlShortenerService.shorten(REQUEST, BASE_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not generate a unique short code after 5 attempts");
    }
}
