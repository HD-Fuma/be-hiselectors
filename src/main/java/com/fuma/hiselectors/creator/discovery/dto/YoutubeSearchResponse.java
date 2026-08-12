package com.fuma.hiselectors.creator.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** search.list 응답. 영상 ID 만 담겨 있고 통계나 채널 정보는 없다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YoutubeSearchResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Id id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Id(String videoId) {
    }
}
