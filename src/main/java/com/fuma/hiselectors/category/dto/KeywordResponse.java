package com.fuma.hiselectors.category.dto;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "발굴 키워드")
public record KeywordResponse(

        @Schema(description = "키워드 ID") Long id,
        @Schema(description = "검색 키워드", example = "겟레디윗미") String keyword,
        @Schema(description = "발굴 대상 여부") boolean enabled,
        @Schema(description = "발굴 우선순위") int priority,
        @Schema(description = "마지막 발굴 실행 시각 (미실행이면 null)") LocalDateTime lastRunAt
) {

    public static KeywordResponse from(DiscoveryKeyword keyword) {
        return new KeywordResponse(
                keyword.getId(),
                keyword.getKeyword(),
                keyword.isEnabled(),
                keyword.getPriority(),
                keyword.getLastRunAt()
        );
    }
}
