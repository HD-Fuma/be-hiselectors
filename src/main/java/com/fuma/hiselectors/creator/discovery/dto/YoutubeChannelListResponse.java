package com.fuma.hiselectors.creator.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * channels.list 응답.
 *
 * <p>{@code snippet.description} 이 인스타 핸들의 유일한 소스다.
 * YouTube 는 채널 페이지의 '링크' 섹션을 API 로 주지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeChannelListResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, Statistics statistics,
                       ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String description, String country,
                          Map<String, Thumbnail> thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(String subscriberCount, String viewCount, String videoCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(RelatedPlaylists relatedPlaylists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedPlaylists(String uploads) {
    }
}
