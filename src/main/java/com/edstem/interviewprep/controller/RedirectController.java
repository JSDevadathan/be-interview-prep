package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.entity.ShortUrl;
import com.edstem.interviewprep.service.UrlShortenerService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
        return redirectTo(urlShortenerService.resolveAndCountVisit(code));
    }

    @RequestMapping(value = SHORT_CODE_PATH, method = RequestMethod.HEAD)
    public ResponseEntity<Void> redirectWithoutCounting(@PathVariable String code) {
        return redirectTo(urlShortenerService.resolveWithoutCounting(code));
    }

    private static ResponseEntity<Void> redirectTo(String originalUrl) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
