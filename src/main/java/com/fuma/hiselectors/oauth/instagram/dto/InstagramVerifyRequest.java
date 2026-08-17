package com.fuma.hiselectors.oauth.instagram.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "인스타그램 계정 인증 검증 요청 (프론트가 인스타그램 콜백에서 받은 값)")
public record InstagramVerifyRequest(
        @Schema(description = "인스타그램이 콜백으로 전달한 인가 코드")
        @NotBlank(message = "code 는 필수입니다.")
        String code,

        @Schema(description = "인증 시작 시 발급한 state (인스타그램이 그대로 돌려줌)")
        @NotBlank(message = "state 는 필수입니다.")
        String state
) {
}
