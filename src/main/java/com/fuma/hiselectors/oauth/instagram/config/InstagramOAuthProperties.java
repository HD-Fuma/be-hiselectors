package com.fuma.hiselectors.oauth.instagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "instagram.oauth")
public record InstagramOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope
) {

    @Override
    public String redirectUri() {
        if (redirectUri == null || redirectUri.isBlank() || redirectUri.endsWith("/")) {
            return redirectUri;
        }
        return java.net.URI.create(redirectUri).getPath().isEmpty() ? redirectUri + "/" : redirectUri;
    }
}
