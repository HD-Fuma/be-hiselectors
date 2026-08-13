package com.fuma.hiselectors.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "instagram.collection")
public record InstagramCollectionProperties(
        // Meta Graph API 버전
        String apiVersion,
        // API 요청 주체인 우리 Instagram 비즈니스 계정 ID
        String businessAccountId,
        // 우리 비즈니스 계정의 장기 액세스 토큰
        String accessToken
) {

    /** Instagram 콘텐츠 수집 필수 설정 존재 여부 */
    public boolean isConfigured() {
        return hasText(apiVersion) && hasText(businessAccountId) && hasText(accessToken);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
