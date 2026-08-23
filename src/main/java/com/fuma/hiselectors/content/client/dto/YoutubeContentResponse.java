package com.fuma.hiselectors.content.client.dto;

import java.util.List;
import java.util.Map;

/** YouTube playlistItems API에서 실제 영상 목록을 받기 위한 응답 */
public record YoutubeContentResponse(
        // 다음 페이지 조회용 토큰
        String nextPageToken,
        // 현재 페이지의 영상 목록
        List<Item> items
) {

    /** 영상 목록의 항목 한 개 */
    public record Item(
            String id,
            Snippet snippet,
            ContentDetails contentDetails,
            Statistics statistics) {
    }

    /** 영상 제목과 설명 */
    public record Snippet(
            String title,
            String description,
            String publishedAt,
            Map<String, Thumbnail> thumbnails) {
    }

    public record Thumbnail(String url) {
    }

    public record ContentDetails(
            // 실제 YouTube 영상 ID (playlistItems 응답)
            String videoId,
            // 실제 영상 공개 시각 (playlistItems 응답)
            String videoPublishedAt,
            // ISO-8601 영상 길이 (videos.list 응답). 예: PT1M30S
            String duration
    ) {
    }

    public record Statistics(String viewCount, String likeCount, String commentCount) {
    }
}
