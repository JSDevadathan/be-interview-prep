package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.repository.ShortUrlRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ShortUrlControllerIntegrationTest.FixedClockConfig.class)
class ShortUrlControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String TODAY = "2026-01-15";
    private static final String YESTERDAY = "2026-01-14";
    private static final String NEXT_MONTH = "2026-02-15";
    private static final String LONG_URL = "https://example.com/articles/2026/01/a-very-long-path?ref=newsletter";
    private static final String CODE_PATTERN = "^[A-Za-z0-9]{7}$";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @AfterEach
    void deleteAllShortUrls() {
        shortUrlRepository.deleteAll();
    }

    @Test
    void shortenReturnsCodeShortUrlAndStatsLocation() throws Exception {
        String body = shorten("""
                {"url": "%s", "expiryDate": "%s"}
                """.formatted(LONG_URL, NEXT_MONTH))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/urls/[A-Za-z0-9]{7}/stats")))
                .andExpect(jsonPath("$.code").value(matchesPattern(CODE_PATTERN)))
                .andExpect(jsonPath("$.originalUrl").value(LONG_URL))
                .andExpect(jsonPath("$.expiryDate").value(NEXT_MONTH))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String code = JsonPath.read(body, "$.code");
        String shortUrl = JsonPath.read(body, "$.shortUrl");
        assertThat(shortUrl).isEqualTo("http://localhost/" + code);
    }

    @Test
    void shorteningTheSameUrlTwiceCreatesTwoIndependentLinks() throws Exception {
        String firstCode = createLink(LONG_URL);

        shorten("""
                {"url": "%s"}
                """.formatted(LONG_URL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(not(firstCode)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "ftp://example.com/file", "javascript:alert(1)", "/relative/path", "https://"})
    void shortenRejectsUrlsThatAreNotAbsoluteHttp(String invalidUrl) throws Exception {
        shorten("""
                {"url": "%s"}
                """.formatted(invalidUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("url must be an absolute http or https URL"));
    }

    @Test
    void shortenRequiresUrl() throws Exception {
        shorten("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("url is required"));
    }

    @Test
    void shortenRejectsExpiryDateInThePast() throws Exception {
        shorten("""
                {"url": "%s", "expiryDate": "%s"}
                """.formatted(LONG_URL, YESTERDAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expiryDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("expiryDate must not be in the past"));
    }

    @Test
    void visitingShortUrlRedirectsToOriginalWithoutCaching() throws Exception {
        String code = createLink(LONG_URL);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG_URL))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void statsCountEveryVisit() throws Exception {
        String code = createLink(LONG_URL);
        for (int visit = 0; visit < 3; visit++) {
            mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        }

        mockMvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.originalUrl").value(LONG_URL))
                .andExpect(jsonPath("$.visitCount").value(3))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void unknownCodeReturnsNotFoundForRedirectAndStats() throws Exception {
        mockMvc.perform(get("/{code}", "missing1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Short URL with code missing1 was not found"));

        mockMvc.perform(get("/api/urls/{code}/stats", "missing1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void linkExpiringTodayStillRedirects() throws Exception {
        String code = createLink(LONG_URL, TODAY);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound());
    }

    @Test
    void expiredLinkReturnsGoneAndIsNotCounted() throws Exception {
        shortUrlRepository.save(new ShortUrl("expired1", LONG_URL, LocalDate.parse(YESTERDAY), NOW));

        mockMvc.perform(get("/{code}", "expired1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Short URL with code expired1 expired on 2026-01-14"));

        mockMvc.perform(get("/api/urls/{code}/stats", "expired1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitCount").value(0));
    }

    private String createLink(String url) throws Exception {
        return createLink(url, null);
    }

    private String createLink(String url, String expiryDate) throws Exception {
        String json = expiryDate == null
                ? "{\"url\": \"%s\"}".formatted(url)
                : "{\"url\": \"%s\", \"expiryDate\": \"%s\"}".formatted(url, expiryDate);
        String body = shorten(json)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private ResultActions shorten(String json) throws Exception {
        return mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
