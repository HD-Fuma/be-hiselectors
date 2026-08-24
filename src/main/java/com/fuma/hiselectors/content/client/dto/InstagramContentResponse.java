package com.fuma.hiselectors.content.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Instagram Business Discovery API 응답 */
public record InstagramContentResponse(
        // JSON의 business_discovery를 Java의 businessDiscovery로 연결
        @JsonProperty("business_discovery") BusinessDiscovery businessDiscovery
) {

    /** 조회 대상 Instagram 계정의 공개 게시글 정보 */
    public record BusinessDiscovery(
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            MediaPage media) {
    }

    /** 현재 페이지의 게시글 목록과 다음 페이지 정보 */
    public record MediaPage(List<Media> data, Paging paging) {
    }

    /** Instagram 게시글 또는 캐러셀 내부 미디어 한 개 */
    public record Media(
            // Instagram 게시글 또는 미디어 ID
            String id,
            // 게시글 본문
            String caption,
            // IMAGE, VIDEO, CAROUSEL_ALBUM
            @JsonProperty("media_type") String mediaType,
            // FEED 또는 REELS
            @JsonProperty("media_product_type") String mediaProductType,
            // 관리자가 게시글 원문으로 이동할 수 있는 주소
            String permalink,
            // Instagram 게시 시각
            String timestamp,
            // 이미지나 영상 파일 직접 주소이며 응답에 없을 수 있음
            @JsonProperty("media_url") String mediaUrl,
            // 영상 대표 이미지 주소이며 응답에 없을 수 있음
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("view_count") Long viewCount,
            @JsonProperty("like_count") Long likeCount,
            @JsonProperty("comments_count") Long commentsCount,
            // 캐러셀 내부 이미지와 영상 목록
            Children children
    ) {
    }

    /** 캐러셀 내부 미디어 목록 */
    public record Children(List<Media> data) {
    }

    /** 다음 페이지 URL */
    public record Paging(String next) {
    }

    /** Instagram Graph API 오류 응답 */
    public record GraphErrorResponse(GraphError error) {
    }

    /** 게시물 미존재 여부 판별에 사용하는 Instagram Graph API 오류 코드 */
    public record GraphError(
            Integer code,
            @JsonProperty("error_subcode") Integer errorSubcode
    ) {
    }
}
