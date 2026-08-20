package com.fuma.hiselectors.generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "기수 수정 요청 (null인 필드는 변경하지 않음)")
public record GenerationUpdateRequest(

        @Size(max = 30, message = "기수명은 30자를 넘을 수 없습니다.")
        @Pattern(regexp = ".*\\S.*", message = "기수명은 공백일 수 없습니다.")
        String generationName,

        LocalDateTime startDate,

        LocalDateTime endDate
) {
}
