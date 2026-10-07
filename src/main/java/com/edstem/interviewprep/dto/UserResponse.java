package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.UserAccount;
import com.edstem.interviewprep.enums.Role;
import java.time.Instant;

public record UserResponse(Long id, String username, Role role, Instant createdAt) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getUsername(), account.getRole(), account.getCreatedAt());
    }
}
