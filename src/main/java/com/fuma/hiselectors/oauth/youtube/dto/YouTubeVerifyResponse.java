package com.fuma.hiselectors.oauth.youtube.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유튜브 채널 인증 결과 (지원서 저장에 사용)")
public record YouTubeVerifyResponse(
        @Schema(description = "인증 성공 여부", example = "true")
        boolean verified,
        @Schema(description = "채널 ID (지원서 sns_account_id 에 저장)", example = "UCxxxxxxxxxxxxxxxx")
        String channelId,
        @Schema(description = "채널명", example = "내 채널")
        String channelTitle,
        @Schema(description = "구독자 수 (지원서 follower_count 에 저장, 비공개면 null)", example = "12345")
        Long followerCount
) {

    public static YouTubeVerifyResponse of(String channelId, String channelTitle, Long followerCount) {
        return new YouTubeVerifyResponse(true, channelId, channelTitle, followerCount);
    }
}
