package com.fuma.hiselectors.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// 카카오 object_type: text JSON 구조
public record DefaultTextTemplate(
        @JsonProperty("object_type") String objectType,
        String text,
        Link link,
        List<Button> buttons
) implements KakaoMessageTemplate {

    public record Button(String title, Link link) {
    }

    public record Link(@JsonProperty("web_url") String webUrl,
                       @JsonProperty("mobile_web_url") String mobileWebUrl) {
    }
}
