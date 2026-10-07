package com.edstem.interviewprep.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.security.admin")
public record AdminAccountProperties(String username, String password) {

    public AdminAccountProperties {
        if (StringUtils.hasText(username) != StringUtils.hasText(password)) {
            throw new IllegalArgumentException(
                    "app.security.admin.username and app.security.admin.password must be set together");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(username);
    }
}
