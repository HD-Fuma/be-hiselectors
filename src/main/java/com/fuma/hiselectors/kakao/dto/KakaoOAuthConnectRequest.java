package com.fuma.hiselectors.kakao.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoOAuthConnectRequest(
        @NotBlank String code,
        @NotBlank String state
) {
}
