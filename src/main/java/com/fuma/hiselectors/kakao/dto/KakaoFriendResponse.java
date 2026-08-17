package com.fuma.hiselectors.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoFriendResponse(
        List<Friend> elements,
        @JsonProperty("total_count") int totalCount
) {
    public record Friend(
            Long id,
            String uuid,
            Boolean favorite,
            @JsonProperty("profile_nickname") String profileNickname,
            @JsonProperty("profile_thumbnail_image") String profileThumbnailImage
    ) {
    }
}
