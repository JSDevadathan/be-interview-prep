package com.edstem.interviewprep.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    @Test
    void signingKeyRejectsSecretShorterThan32BytesWithoutRevealingIt() {
        String shortSecret = "too-short-secret";

        assertThatThrownBy(() -> JwtConfig.signingKey(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("app.security.jwt.secret must be at least 32 bytes for HS256, but was 16 bytes")
                .message().doesNotContain(shortSecret);
    }

    @Test
    void signingKeyAcceptsSecretOf32Bytes() {
        assertThat(JwtConfig.signingKey("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
