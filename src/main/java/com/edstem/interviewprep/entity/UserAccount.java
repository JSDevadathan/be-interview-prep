package com.edstem.interviewprep.entity;

import com.edstem.interviewprep.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final String USERNAME_PATTERN =
            "^[A-Za-z0-9._-]{" + USERNAME_MIN_LENGTH + "," + USERNAME_MAX_LENGTH + "}$";
    private static final int PASSWORD_HASH_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = USERNAME_MAX_LENGTH)
    private String username;

    @Column(nullable = false, length = PASSWORD_HASH_MAX_LENGTH)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, Role role, Instant createdAt) {
        this.username = normalizeUsername(username);
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /**
     * Usernames are case-insensitive so that "Alice" and "alice" cannot be registered as two different people.
     */
    public static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
