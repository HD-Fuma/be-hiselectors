package com.fuma.hiselectors.oauth.instagram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstagramTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("user_id") Long userId
) {
}
