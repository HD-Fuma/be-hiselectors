package com.fuma.hiselectors.creator.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 채널 업로드 목록에서 최근 게시일만 읽기 위한 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubePlaylistItemListResponse(String nextPageToken, List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(ContentDetails contentDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(String videoPublishedAt) {
    }
}
