package com.fuma.hiselectors.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "발굴 카테고리 생성 요청")
public record CategoryCreateRequest(

        @Schema(description = "카테고리 코드 (UPPER_SNAKE_CASE). creator_pool.category 에 저장되는 값",
                example = "BEAUTY")
        @NotBlank(message = "카테고리 코드는 필수입니다.")
        @Size(max = 20, message = "카테고리 코드는 20자를 넘을 수 없습니다.")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "카테고리 코드는 대문자·숫자·밑줄만 쓸 수 있습니다. (예: BEAUTY, HOME_LIVING)")
        String code,

        @Schema(description = "화면에 보여줄 이름", example = "뷰티")
        @NotBlank(message = "카테고리명은 필수입니다.")
        @Size(max = 50, message = "카테고리명은 50자를 넘을 수 없습니다.")
        String name,

        @Schema(description = "화면 뱃지 색 (#rrggbb). 선택", example = "#d6497f")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "색상은 #rrggbb 형식이어야 합니다.")
        String color,

        @Schema(description = "화면 노출 순서 (작을수록 앞)", example = "0")
        Integer displayOrder
) {
}
