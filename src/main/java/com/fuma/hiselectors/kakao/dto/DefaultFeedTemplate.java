package com.fuma.hiselectors.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 카카오 object_type: feed JSON 구조
public record DefaultFeedTemplate(
        @JsonProperty("object_type") String objectType,
        Content content,
        List<Button> buttons
) implements KakaoMessageTemplate {
    public record Content(String title, String description,
                          @JsonProperty("image_url") String imageUrl, Link link) {
    }

    public record Button(String title, Link link) {
    }

    public record Link(@JsonProperty("web_url") String webUrl,
                       @JsonProperty("mobile_web_url") String mobileWebUrl) {
    }
}
