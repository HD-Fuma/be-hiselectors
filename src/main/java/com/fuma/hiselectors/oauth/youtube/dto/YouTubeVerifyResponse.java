package com.fuma.hiselectors.oauth.youtube.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "유튜브 채널 인증 결과. 구글 계정이 소유한 채널을 모두 반환하며, "
        + "사용자가 그중 하나를 골라 그 채널의 verificationToken 으로 지원서를 제출한다.")
public record YouTubeVerifyResponse(
        @Schema(description = "인증 성공 여부", example = "true")
        boolean verified,
        @Schema(description = "소유가 확인된 채널 목록 (보통 1개, 브랜드 계정 등은 다수)")
        List<Channel> channels
) {

    @Schema(description = "인증된 유튜브 채널 1개")
    public record Channel(
            @Schema(description = "채널 ID (지원서 sns_account_id 에 저장)", example = "UCxxxxxxxxxxxxxxxx")
            String channelId,
            @Schema(description = "채널명", example = "내 채널")
            String channelTitle,
            @Schema(description = "구독자 수 (지원서 follower_count 에 저장, 비공개면 null)", example = "12345")
            Long followerCount,
            @Schema(description = "전체 공개 영상 수", example = "120")
            Long contentCount,
            @Schema(description = "이 채널로 지원서를 제출할 때 쓰는 서명 검증 토큰")
            String verificationToken
    ) {
    }

    public static YouTubeVerifyResponse of(List<Channel> channels) {
        return new YouTubeVerifyResponse(true, channels);
    }
}
