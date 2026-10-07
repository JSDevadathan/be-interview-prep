package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.UserAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Pattern(
                regexp = UserAccount.USERNAME_PATTERN,
                message = "username must be " + UserAccount.USERNAME_MIN_LENGTH + " to " + UserAccount.USERNAME_MAX_LENGTH
                        + " characters of letters, digits, dots, underscores or hyphens")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = RegisterRequest.PASSWORD_MIN_LENGTH,
                message = "password must be at least " + RegisterRequest.PASSWORD_MIN_LENGTH + " characters")
        String password) {

    public static final int PASSWORD_MIN_LENGTH = 8;
}
