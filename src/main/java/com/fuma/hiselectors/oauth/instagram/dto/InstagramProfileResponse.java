package com.fuma.hiselectors.oauth.instagram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstagramProfileResponse(
        String id,
        String username,
        @JsonProperty("followers_count") Long followersCount
) {
}
