package com.fuma.hiselectors.creator.discovery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Meta Graph API의 {@code business_discovery} 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstagramBusinessDiscoveryResponse(
        @JsonProperty("business_discovery") BusinessDiscovery businessDiscovery,
        String id
) {

    /** 조회 대상 Instagram 프로 계정의 공개 정보와 최근 게시물. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BusinessDiscovery(
            String id,
            String username,
            String name,
            String biography,
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            @JsonProperty("followers_count") Long followersCount,
            @JsonProperty("media_count") Long mediaCount,
            Media media
    ) {
    }

    /** 요청한 최근 게시물 목록. 페이징 정보는 현재 발굴 계산에 쓰지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(List<MediaItem> data) {
    }

    /** 참여율과 최근 활동일 계산에 사용하는 게시물 지표. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaItem(
            String id,
            String caption,
            @JsonProperty("media_type") String mediaType,
            String permalink,
            String timestamp,
            @JsonProperty("like_count") Long likeCount,
            @JsonProperty("comments_count") Long commentsCount
    ) {
    }
}
