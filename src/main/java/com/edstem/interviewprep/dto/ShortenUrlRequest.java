package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.common.validation.HttpUrl;
import com.edstem.interviewprep.common.validation.IsoDate;
import com.edstem.interviewprep.common.validation.NotPastDate;
import com.edstem.interviewprep.entity.ShortUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortenUrlRequest(
        @NotBlank(message = "url is required")
        @Size(max = ShortUrl.URL_MAX_LENGTH, message = "url must be at most " + ShortUrl.URL_MAX_LENGTH + " characters")
        @HttpUrl(message = "url must be an absolute http or https URL")
        String url,

        @IsoDate(message = "expiryDate must be a date in yyyy-MM-dd format")
        @NotPastDate(message = "expiryDate must not be in the past")
        String expiryDate) {
}
