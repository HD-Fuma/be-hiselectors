package com.fuma.hiselectors.youtube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube.oauth")
public record YouTubeOAuthProperties(
        String clientId,
        String clientSecret,
        // 구글이 콜백을 보낼 백엔드 URI. 콘솔의 '승인된 리디렉션 URI'와 정확히 일치해야 함
        String redirectUri,
        String frontendRedirectUri,
        String scope,
        long stateValiditySeconds
) {
}
