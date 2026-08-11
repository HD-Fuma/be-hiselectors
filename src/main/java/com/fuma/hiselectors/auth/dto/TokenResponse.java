package com.fuma.hiselectors.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        String loginId,
        String role
) {

    public static TokenResponse of(String accessToken, String loginId, String role) {
        return new TokenResponse(accessToken, "Bearer", loginId, role);
    }
}
