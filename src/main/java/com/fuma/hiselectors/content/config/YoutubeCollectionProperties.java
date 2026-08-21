package com.fuma.hiselectors.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube.collection")
public record YoutubeCollectionProperties(
        // YouTube Data API 키
        String apiKey
) {

    /** YouTube 콘텐츠 수집 API 키 존재 여부 */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
