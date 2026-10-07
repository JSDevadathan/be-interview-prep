package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.repository.ShortUrlRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VisitCountConcurrencyTest {

    private static final int SIMULTANEOUS_VISITS = 50;
    private static final String CODE = "popular1";
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @AfterEach
    void deleteAllShortUrls() {
        shortUrlRepository.deleteAll();
    }

    @Test
    void simultaneousVisitsAreAllCounted() throws Exception {
        shortUrlRepository.save(new ShortUrl(CODE, "https://example.com", null, Instant.parse("2026-01-15T10:00:00Z")));
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_VISITS);
        try {
            List<Future<String>> visits = new ArrayList<>();
            for (int visit = 0; visit < SIMULTANEOUS_VISITS; visit++) {
                visits.add(executor.submit(() -> {
                    startSignal.await();
                    return urlShortenerService.resolveAndCountVisit(CODE);
                }));
            }
            startSignal.countDown();
            for (Future<String> visit : visits) {
                assertThat(visit.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("https://example.com");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(urlShortenerService.stats(CODE).visitCount()).isEqualTo(SIMULTANEOUS_VISITS);
    }
}
