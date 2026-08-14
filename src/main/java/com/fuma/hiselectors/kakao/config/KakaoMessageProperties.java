package com.fuma.hiselectors.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.message")
public record KakaoMessageProperties(
        String webUrl,
        String mobileWebUrl,
        String imageUrl
) {
}
