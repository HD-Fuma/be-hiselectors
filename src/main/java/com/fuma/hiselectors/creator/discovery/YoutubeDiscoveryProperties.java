package com.fuma.hiselectors.creator.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 발굴용 YouTube API 설정.
 *
 * <p>{@code youtube.oauth} 와는 별개다. 저쪽은 사용자별 OAuth 토큰이고
 * 이쪽은 우리 계정의 고정 API 키다. 자격증명 종류가 달라 자리를 나눠 둔다.
 */
@ConfigurationProperties(prefix = "youtube.discovery")
public record YoutubeDiscoveryProperties(
        String apiKey,
        Integer dailyQuota,
        Integer maxResultsPerKeyword
) {

    /** 키워드 1개 발굴에 드는 쿼터. search.list 100 + videos/channels 배치 2. */
    public static final int QUOTA_PER_KEYWORD = 102;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int dailyQuotaOrDefault() {
        return dailyQuota == null ? 10_000 : dailyQuota;
    }

    public int maxResultsOrDefault() {
        return maxResultsPerKeyword == null ? 25 : maxResultsPerKeyword;
    }
}
