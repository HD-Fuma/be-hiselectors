package com.fuma.hiselectors.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "발굴 키워드 수정 요청 (null 인 필드는 변경하지 않음)")
public record KeywordUpdateRequest(

        @Schema(description = "false 면 발굴 대상에서 제외") Boolean enabled,

        @Schema(description = "발굴 우선순위 (클수록 먼저 실행)") Integer priority
) {
}
