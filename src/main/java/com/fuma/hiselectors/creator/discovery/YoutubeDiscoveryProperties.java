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

    /** search.list 100 + videos/channels 배치 2. */
    public static final int BASE_QUOTA_PER_KEYWORD = 102;
    public static final int MAX_ACTIVITY_PAGES_PER_CHANNEL = 4;
    private static final int YOUTUBE_LIST_MAX_RESULTS = 50;
    public static final int MAX_FILTERABLE_RECENT_ACTIVITY_COUNT =
            MAX_ACTIVITY_PAGES_PER_CHANNEL * YOUTUBE_LIST_MAX_RESULTS;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int dailyQuotaOrDefault() {
        return dailyQuota == null ? 10_000 : dailyQuota;
    }

    public int maxResultsOrDefault() {
        int maxResults = maxResultsPerKeyword == null ? 25 : maxResultsPerKeyword;
        if (maxResults < 1 || maxResults > YOUTUBE_LIST_MAX_RESULTS) {
            throw new IllegalStateException(
                    "youtube.discovery.max-results-per-keyword must be between 1 and 50");
        }
        return maxResults;
    }

    /** 최근 활동 조회의 채널별 최대 호출 수까지 포함한 키워드당 예약 쿼터. */
    public int quotaPerKeyword() {
        return BASE_QUOTA_PER_KEYWORD
                + maxResultsOrDefault() * MAX_ACTIVITY_PAGES_PER_CHANNEL;
    }
}
