package com.fuma.hiselectors.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.oauth")
public record KakaoOAuthProperties(
        String restApiKey,
        String clientSecret,
        String redirectUri,
        String senderScope,
        String recipientScope
) {
}
