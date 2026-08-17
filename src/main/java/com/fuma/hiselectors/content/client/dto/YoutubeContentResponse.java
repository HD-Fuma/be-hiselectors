package com.fuma.hiselectors.content.client.dto;

import java.util.List;

/** YouTube playlistItems API에서 실제 영상 목록을 받기 위한 응답 */
public record YoutubeContentResponse(
        // 다음 페이지 조회용 토큰
        String nextPageToken,
        // 현재 페이지의 영상 목록
        List<Item> items
) {

    /** 영상 목록의 항목 한 개 */
    public record Item(Snippet snippet, ContentDetails contentDetails) {
    }

    /** 영상 제목과 설명 */
    public record Snippet(String title, String description) {
    }

    public record ContentDetails(
            // 실제 YouTube 영상 ID
            String videoId,
            // 실제 영상 공개 시각
            String videoPublishedAt
    ) {
    }
}
