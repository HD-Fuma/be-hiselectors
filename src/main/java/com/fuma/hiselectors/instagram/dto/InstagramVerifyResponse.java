package com.fuma.hiselectors.instagram.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인스타그램 계정 인증 결과 (지원서 저장에 사용)")
public record InstagramVerifyResponse(
        @Schema(description = "인증 성공 여부", example = "true")
        boolean verified,
        @Schema(description = "인스타그램 계정 ID (지원서 sns_account_id 에 저장)", example = "17841400000000000")
        String accountId,
        @Schema(description = "사용자명(핸들)", example = "my_handle")
        String username,
        @Schema(description = "팔로워 수 (지원서 follower_count 에 저장, 비즈니스/크리에이터 계정이 아니면 null)",
                example = "12345")
        Long followerCount
) {

    public static InstagramVerifyResponse of(String accountId, String username, Long followerCount) {
        return new InstagramVerifyResponse(true, accountId, username, followerCount);
    }
}
