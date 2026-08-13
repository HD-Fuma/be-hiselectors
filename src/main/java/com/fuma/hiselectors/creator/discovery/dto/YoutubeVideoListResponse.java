package com.fuma.hiselectors.creator.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** videos.list 응답. 여기서 채널 ID 와 조회수를 얻는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeVideoListResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, Statistics statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String channelId, String title, String publishedAt) {
    }

    /** 숫자가 문자열로 온다. 비공개면 필드 자체가 없을 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(String viewCount, String likeCount, String commentCount) {
    }
}
