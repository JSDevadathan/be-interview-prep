package com.edstem.interviewprep.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AdminAccountProperties.class})
public class JwtConfig {

    public static final String ROLES_CLAIM = "roles";
    public static final MacAlgorithm SIGNING_ALGORITHM = MacAlgorithm.HS256;

    private static final int MIN_SECRET_BYTES = 32;
    private static final String HMAC_KEY_ALGORITHM = "HmacSHA256";
    private static final String NO_AUTHORITY_PREFIX = "";

    @Bean
    JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(jwtProperties.secret())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties jwtProperties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(jwtProperties.secret()))
                .macAlgorithm(SIGNING_ALGORITHM)
                .build();
        // Tokens are issued and checked against this service's own clock, so no skew allowance is needed and a
        // login ends exactly when the configured time to live runs out.
        JwtTimestampValidator expiryValidator = new JwtTimestampValidator(Duration.ZERO);
        expiryValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                expiryValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(ROLES_CLAIM);
        authoritiesConverter.setAuthorityPrefix(NO_AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    static SecretKey signingKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least %d bytes for %s, but was %d bytes"
                    .formatted(MIN_SECRET_BYTES, SIGNING_ALGORITHM.getName(), secretBytes.length));
        }
        return new SecretKeySpec(secretBytes, HMAC_KEY_ALGORITHM);
    }
}
