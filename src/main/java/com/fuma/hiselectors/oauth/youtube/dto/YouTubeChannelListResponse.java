package com.fuma.hiselectors.oauth.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

//items 가 비어 있으면 해당 구글 계정에 연결된 채널이 없다
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeChannelListResponse(
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            Snippet snippet,
            Statistics statistics
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(
            String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
            // 구독자 수. 문자열로 오며, 비공개(hiddenSubscriberCount)면 없을 수 있다.
            @JsonProperty("subscriberCount") String subscriberCount,
            @JsonProperty("hiddenSubscriberCount") Boolean hiddenSubscriberCount
    ) {
    }
}
