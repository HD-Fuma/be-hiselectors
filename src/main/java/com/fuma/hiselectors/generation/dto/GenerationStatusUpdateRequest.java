package com.fuma.hiselectors.generation.dto;

import com.fuma.hiselectors.generation.model.GenerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "기수 상태 변경 요청")
public record GenerationStatusUpdateRequest(
        @NotNull(message = "기수 상태는 필수입니다.")
        GenerationStatus status
) {
}
