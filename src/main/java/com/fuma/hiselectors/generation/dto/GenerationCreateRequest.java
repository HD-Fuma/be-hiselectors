package com.fuma.hiselectors.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "기수 생성 요청")
public record GenerationCreateRequest(

        @NotBlank(message = "기수명은 필수입니다.")
        @Size(max = 30, message = "기수명은 30자를 넘을 수 없습니다.")
        String generationName,

        @NotNull(message = "모집 시작일은 필수입니다.")
        LocalDateTime startDate,

        @NotNull(message = "모집 종료일은 필수입니다.")
        LocalDateTime endDate
) {
}
