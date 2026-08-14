package com.fuma.hiselectors.oauth.youtube.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "유튜브 채널 인증 검증 요청 (프론트가 구글 콜백에서 받은 값)")
public record YouTubeVerifyRequest(
        @Schema(description = "구글이 콜백으로 전달한 인가 코드")
        @NotBlank(message = "code 는 필수입니다.")
        String code,

        @Schema(description = "인증 시작 시 발급한 state (구글이 그대로 돌려줌)")
        @NotBlank(message = "state 는 필수입니다.")
        String state
) {
}
