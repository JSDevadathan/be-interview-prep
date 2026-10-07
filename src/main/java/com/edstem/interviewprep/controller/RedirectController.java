package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.service.UrlShortenerService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private static final String SHORT_CODE_PATH = "/{code:[A-Za-z0-9]{1," + ShortUrl.CODE_MAX_LENGTH + "}}";

    private final UrlShortenerService urlShortenerService;

    public RedirectController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @GetMapping(SHORT_CODE_PATH)
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        URI target = URI.create(urlShortenerService.resolveAndCountVisit(code));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(target)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
