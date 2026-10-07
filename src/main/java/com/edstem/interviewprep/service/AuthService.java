package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.LoginRequest;
import com.edstem.interviewprep.dto.TokenResponse;
import com.edstem.interviewprep.security.JwtConfig;
import com.edstem.interviewprep.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return issueToken(authentication);
    }

    private TokenResponse issueToken(Authentication authentication) {
        Instant issuedAt = Instant.now(clock);
        Duration timeToLive = jwtProperties.timeToLive();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(timeToLive))
                .claim(JwtConfig.ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(JwtConfig.SIGNING_ALGORITHM).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, TOKEN_TYPE, timeToLive.toSeconds());
    }
}
