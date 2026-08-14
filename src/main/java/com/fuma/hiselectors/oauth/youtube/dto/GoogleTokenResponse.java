package com.fuma.hiselectors.oauth.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 소유 확인만 하므로 access_token 만 사용하고 저장하지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        String scope
) {
}
