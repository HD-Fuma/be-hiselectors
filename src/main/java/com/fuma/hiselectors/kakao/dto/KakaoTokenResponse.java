package com.fuma.hiselectors.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
        String scope
) {
}
