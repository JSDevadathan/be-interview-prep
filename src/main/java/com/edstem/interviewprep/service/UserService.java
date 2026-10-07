package com.edstem.interviewprep.service;

import com.edstem.interviewprep.common.error.FieldValidationException;
import com.edstem.interviewprep.common.error.ResourceConflictException;
import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.dto.RegisterRequest;
import com.edstem.interviewprep.dto.UserResponse;
import com.edstem.interviewprep.entity.UserAccount;
import com.edstem.interviewprep.enums.Role;
import com.edstem.interviewprep.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String RESOURCE_NAME = "User";
    private static final String USERNAME_IDENTIFIER = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String PASSWORD_TOO_LONG_MESSAGE =
            "password must be at most " + BCRYPT_MAX_PASSWORD_BYTES + " bytes";
    private static final ChronoUnit DATABASE_TIMESTAMP_PRECISION = ChronoUnit.MICROS;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = UserAccount.normalizeUsername(request.username());
        if (userAccountRepository.existsByUsername(username)) {
            throw new ResourceConflictException(RESOURCE_NAME, USERNAME_IDENTIFIER, username);
        }
        return UserResponse.from(createAccount(username, request.password(), Role.USER));
    }

    @Transactional
    public boolean createAdminIfAbsent(String username, String password) {
        String normalizedUsername = UserAccount.normalizeUsername(username);
        Optional<UserAccount> existing = userAccountRepository.findByUsername(normalizedUsername);
        if (existing.isEmpty()) {
            createAccount(normalizedUsername, password, Role.ADMIN);
            return true;
        }
        if (existing.get().getRole() != Role.ADMIN) {
            throw new IllegalStateException(
                    "Configured admin username %s already belongs to a %s account"
                            .formatted(normalizedUsername, existing.get().getRole()));
        }
        return false;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String username) {
        return userAccountRepository.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, USERNAME_IDENTIFIER, username));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userAccountRepository.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    private UserAccount createAccount(String username, String password, Role role) {
        requirePasswordWithinHashLimit(password);
        UserAccount account = new UserAccount(
                username,
                passwordEncoder.encode(password),
                role,
                Instant.now(clock).truncatedTo(DATABASE_TIMESTAMP_PRECISION));
        return userAccountRepository.save(account);
    }

    private static void requirePasswordWithinHashLimit(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new FieldValidationException(PASSWORD_FIELD, PASSWORD_TOO_LONG_MESSAGE);
        }
    }
}
