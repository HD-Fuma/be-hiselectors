package com.fuma.hiselectors.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

//items 가 비어 있으면 해당 구글 계정에 연결된 채널이 없다
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeChannelListResponse(
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            Snippet snippet
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title
    ) {
    }
}
