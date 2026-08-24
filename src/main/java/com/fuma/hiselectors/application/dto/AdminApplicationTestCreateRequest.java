package com.fuma.hiselectors.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminApplicationTestCreateRequest(
        @NotBlank(message = "SNS 프로필 URL은 필수입니다.")
        @Size(max = 500, message = "SNS 프로필 URL은 500자 이하여야 합니다.")
        String profileUrl
) {
}
