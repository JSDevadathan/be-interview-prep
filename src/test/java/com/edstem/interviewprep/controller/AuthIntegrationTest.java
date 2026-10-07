package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.entity.UserAccount;
import com.edstem.interviewprep.enums.Role;
import com.edstem.interviewprep.repository.UserAccountRepository;
import com.edstem.interviewprep.service.UserService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {
        "app.security.admin.username=" + AuthIntegrationTest.ADMIN_USERNAME,
        "app.security.admin.password=" + AuthIntegrationTest.ADMIN_PASSWORD})
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.MutableClockConfig.class)
class AuthIntegrationTest {

    static final String ADMIN_USERNAME = "admin";
    static final String ADMIN_PASSWORD = "admin-test-password";

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Duration LOGIN_LIFETIME = Duration.ofMinutes(15);
    private static final String USERNAME = "alice";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String PROFILE_PATH = "/api/users/me";
    private static final String USERS_PATH = "/api/users";

    static final class MutableClock extends Clock {

        private Instant instant = NOW;

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("MutableClock is always UTC");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private UserService userService;

    @Value("${app.security.jwt.secret}")
    private String signingSecret;

    @BeforeEach
    void resetClock() {
        clock.setInstant(NOW);
    }

    @AfterEach
    void deleteRegisteredUsers() {
        List<UserAccount> registeredUsers = userAccountRepository.findAll().stream()
                .filter(account -> account.getRole() == Role.USER)
                .toList();
        userAccountRepository.deleteAll(registeredUsers);
    }

    @Test
    void registerCreatesUserWithUserRoleAndNoPasswordInResponse() throws Exception {
        register(USERNAME, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerIgnoresRoleSuppliedByClient() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s", "role": "ADMIN"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void registerStoresOnlyAOneWayHashOfThePassword() throws Exception {
        register(USERNAME, PASSWORD).andExpect(status().isCreated());

        String storedHash = userAccountRepository.findByUsername(USERNAME).orElseThrow().getPasswordHash();
        assertThat(storedHash).doesNotContain(PASSWORD).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();
    }

    @Test
    void registerRejectsUsernameThatDiffersOnlyByCase() throws Exception {
        register(USERNAME, PASSWORD).andExpect(status().isCreated());

        register("Alice", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User with username alice already exists"));
    }

    @Test
    void registerRejectsInvalidInputWithFieldErrors() throws Exception {
        register("a!", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at least 8 characters"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("username"));
    }

    @Test
    void registerRejectsPasswordLongerThanTheHashCanUse() throws Exception {
        String seventyFourBytePassword = "é".repeat(37);

        register(USERNAME, seventyFourBytePassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at most 72 bytes"));
    }

    @Test
    void loginReturnsBearerTokenThatExpiresInFifteenMinutes() throws Exception {
        register(USERNAME, PASSWORD);

        login(USERNAME, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(LOGIN_LIFETIME.toSeconds()));
    }

    @Test
    void loginFailureReturnsSameErrorForWrongPasswordAndUnknownUser() throws Exception {
        register(USERNAME, PASSWORD);

        login(USERNAME, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        login("nobody", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void loginWithPasswordLongerThanTheHashCanUseIsRejectedAsInvalid() throws Exception {
        register(USERNAME, PASSWORD);

        login(USERNAME, "é".repeat(37))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void loggedInUserCanViewOwnProfile() throws Exception {
        register(USERNAME, PASSWORD);
        String token = obtainToken("ALICE", PASSWORD);

        getWithToken(PROFILE_PATH, token)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void requestWithoutTokenReturnsUnauthorizedJson() throws Exception {
        mockMvc.perform(get(PROFILE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required to access this resource"))
                .andExpect(jsonPath("$.instance").value(PROFILE_PATH));
    }

    @Test
    void existingApisRequireLogin() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedTokenReturnsUnauthorizedJson() throws Exception {
        getWithToken(PROFILE_PATH, "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        register(USERNAME, PASSWORD);
        JwtClaimsSet adminClaims = JwtClaimsSet.builder()
                .subject(USERNAME)
                .issuedAt(NOW)
                .expiresAt(NOW.plus(LOGIN_LIFETIME))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        String forgedAdminToken = sign("a-different-secret-that-is-32-bytes-or-more", adminClaims);

        getWithToken(USERS_PATH, forgedAdminToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginExpiresAfterFifteenMinutes() throws Exception {
        register(USERNAME, PASSWORD);
        String token = obtainToken(USERNAME, PASSWORD);

        clock.setInstant(NOW.plus(LOGIN_LIFETIME).minusSeconds(1));
        getWithToken(PROFILE_PATH, token).andExpect(status().isOk());

        clock.setInstant(NOW.plus(LOGIN_LIFETIME).plusSeconds(1));
        getWithToken(PROFILE_PATH, token)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void userCannotListAllUsers() throws Exception {
        register(USERNAME, PASSWORD);
        String userToken = obtainToken(USERNAME, PASSWORD);

        getWithToken(USERS_PATH, userToken)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource"))
                .andExpect(jsonPath("$.instance").value(USERS_PATH));
    }

    @Test
    void userCannotReachAdminEndpointWithHeadRequest() throws Exception {
        register(USERNAME, PASSWORD);
        String userToken = obtainToken(USERNAME, PASSWORD);

        mockMvc.perform(head(USERS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        register(USERNAME, PASSWORD);
        String adminToken = obtainToken(ADMIN_USERNAME, ADMIN_PASSWORD);

        getWithToken(USERS_PATH, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", containsInAnyOrder(ADMIN_USERNAME, USERNAME)))
                .andExpect(jsonPath("$[*].role", containsInAnyOrder("ADMIN", "USER")));
    }

    @Test
    void tokenWithoutExpiryIsRejected() throws Exception {
        register(USERNAME, PASSWORD);
        JwtClaimsSet claimsWithoutExpiry = JwtClaimsSet.builder()
                .subject(USERNAME)
                .issuedAt(NOW)
                .claim("roles", List.of("ROLE_USER"))
                .build();

        getWithToken(PROFILE_PATH, sign(signingSecret, claimsWithoutExpiry))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSeedingFailsWhenConfiguredUsernameBelongsToARegularUser() throws Exception {
        register(USERNAME, PASSWORD);

        assertThatThrownBy(() -> userService.createAdminIfAbsent(USERNAME, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured admin username alice already belongs to a USER account");
    }

    @Test
    void adminSeedingLeavesExistingAdminUnchanged() {
        assertThat(userService.createAdminIfAbsent(ADMIN_USERNAME, "another-password")).isFalse();
    }

    private ResultActions register(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private String obtainToken(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String sign(String secret, JwtClaimsSet claims) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
