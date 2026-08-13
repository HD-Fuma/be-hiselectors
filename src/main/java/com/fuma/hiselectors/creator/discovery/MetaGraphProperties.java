package com.fuma.hiselectors.creator.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Meta Graph API의 크리에이터 발굴용 설정. */
@ConfigurationProperties(prefix = "meta.graph")
public record MetaGraphProperties(
        String baseUrl,
        String apiVersion,
        String accessToken,
        String igUserId
) {

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank()
                ? "https://graph.facebook.com"
                : baseUrl;
    }

    public String apiVersionOrDefault() {
        return apiVersion == null || apiVersion.isBlank() ? "v26.0" : apiVersion;
    }

    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank()
                && igUserId != null && !igUserId.isBlank();
    }
}
