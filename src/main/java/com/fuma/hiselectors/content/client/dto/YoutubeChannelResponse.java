package com.fuma.hiselectors.content.client.dto;

import java.util.List;
import java.util.Map;

/** YouTube channels API에서 업로드 영상 목록 ID를 받기 위한 응답 */
public record YoutubeChannelResponse(List<Item> items) {

    public record Item(String id, Snippet snippet, ContentDetails contentDetails) {
    }

    public record Snippet(
            String title,
            String customUrl,
            Map<String, Thumbnail> thumbnails) {
    }

    public record Thumbnail(String url) {
    }

    public record ContentDetails(RelatedPlaylists relatedPlaylists) {
    }

    public record RelatedPlaylists(
            // 채널에 업로드된 영상 목록 ID
            String uploads
    ) {
    }
}
