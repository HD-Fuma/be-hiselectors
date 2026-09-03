package com.fuma.hiselectors.creator.discovery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 발굴용 YouTube 조회. API 키로 남의 공개 채널을 검색한다.
 *
 * <p>{@code com.fuma.hiselectors.oauth.youtube} 의 OAuth 인증과는 용도가 다르다.
 * 저쪽은 사용자가 동의한 본인 채널을 확인하고, 이쪽은 동의 없이 공개 정보를 수집한다.
 * 자격증명도 사용자 토큰이 아니라 우리 API 키다.
 *
 * <p>인터페이스로 둔 이유는 Mock 구현으로 쿼터를 쓰지 않고 파이프라인을 테스트하기 위해서다.
 */
public interface YoutubeDiscoveryClient {

    /**
     * 키워드로 영상을 검색해 채널을 찾는다.
     *
     * <p>채널이 아니라 영상을 검색한다. 채널명에 키워드가 없어도 그 주제로 콘텐츠를
     * 만드는 채널을 찾기 위해서다. 영상을 먼저 찾고 주인을 역추적한다.
     *
     * @param maxResults 키워드당 검색할 영상 수. 최대 50
     * @return 발굴된 채널들. 검색 결과가 없으면 빈 목록
     */
    List<DiscoveredChannel> discoverByKeyword(String keyword, int maxResults);

    default List<DiscoveredChannel> discoverByKeyword(
            String keyword, int maxResults, boolean currentMonthOnly) {
        return discoverByKeyword(keyword, maxResults);
    }

    /** 이번 호출로 사용한 쿼터. 키워드 1개당 약 102 units. */
    int consumedQuota();

    /**
     * 발굴된 채널 하나.
     *
     * @param matchedVideoViews 이 키워드로 걸린 영상들의 조회수 합.
     *                          카테고리별 비중을 내 대표 카테고리를 정하는 데 쓴다
     */
    record DiscoveredChannel(
            String channelId,
            String title,
            String description,
            String profileImageUrl,
            Long subscriberCount,
            Long totalViewCount,
            LocalDateTime lastUploadAt,
            Integer recent90DayContentCount,
            long matchedVideoViews,
            long matchedVideoLikes,
            long matchedVideoComments
    ) {

        /**
         * 참여율. (좋아요 + 댓글) / 조회수 를 백분율로.
         *
         * <p>조회수가 0이면 계산할 수 없으므로 0을 돌려준다.
         */
        public double engagementRatePercent() {
            if (matchedVideoViews <= 0) {
                return 0;
            }
            return (double) (matchedVideoLikes + matchedVideoComments) / matchedVideoViews * 100;
        }
    }
}
