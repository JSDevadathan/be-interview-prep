package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.ShortUrlResponse;
import com.edstem.interviewprep.dto.ShortenUrlRequest;
import com.edstem.interviewprep.dto.UrlStatsResponse;
import com.edstem.interviewprep.service.UrlShortenerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/urls")
public class ShortUrlController {

    private final UrlShortenerService urlShortenerService;

    public ShortUrlController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        ShortUrlResponse created = urlShortenerService.shorten(request, baseUrl);
        URI statsLocation = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}/stats")
                .buildAndExpand(created.code())
                .toUri();
        return ResponseEntity.created(statsLocation).body(created);
    }

    @GetMapping("/{code}/stats")
    public UrlStatsResponse stats(@PathVariable String code) {
        return urlShortenerService.stats(code);
    }
}
