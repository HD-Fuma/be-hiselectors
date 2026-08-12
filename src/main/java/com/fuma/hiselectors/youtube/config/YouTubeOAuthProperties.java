package com.fuma.hiselectors.youtube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube.oauth")
public record YouTubeOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope
) {
}
