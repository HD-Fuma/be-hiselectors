package com.fuma.hiselectors.selectors.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SelectorRequest(
        Long applicationId,

        Long userId,

        @NotBlank(message = "셀렉터스 역할 ID는 필수입니다.")
        @Size(max = 20)
        String selectorsRoleId,

        @Size(max = 20)
        String selectorsCode,

        @Size(max = 20)
        String selectorsNickname
) {
}
