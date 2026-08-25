package com.fuma.hiselectors.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "발굴 키워드 등록 요청")
public record KeywordCreateRequest(

        @Schema(description = "YouTube 검색에 그대로 쓰일 키워드", example = "겟레디윗미")
        @NotBlank(message = "키워드는 필수입니다.")
        @Size(min = 2, max = 30, message = "키워드는 2자 이상 30자 이하여야 합니다.")
        String keyword,

        @Schema(description = "발굴 우선순위 (클수록 먼저 실행)", example = "0")
        Integer priority
) {
}
