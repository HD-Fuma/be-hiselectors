package com.fuma.hiselectors.instagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "instagram.oauth")
public record InstagramOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        long stateValiditySeconds
) {
}
