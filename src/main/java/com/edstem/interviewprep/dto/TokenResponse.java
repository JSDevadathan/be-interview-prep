package com.edstem.interviewprep.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
