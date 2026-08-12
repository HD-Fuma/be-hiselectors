package com.fuma.hiselectors.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "발굴 카테고리 수정 요청 (null 인 필드는 변경하지 않음. 코드는 변경 불가)")
public record CategoryUpdateRequest(

        @Schema(description = "화면에 보여줄 이름", example = "뷰티")
        @Size(max = 50, message = "카테고리명은 50자를 넘을 수 없습니다.")
        @Pattern(regexp = ".*\\S.*", message = "카테고리명은 공백일 수 없습니다.")
        String name,

        @Schema(description = "화면 노출 순서") Integer displayOrder,

        @Schema(description = "false 면 발굴 대상에서 제외") Boolean enabled
) {
}
