package com.fuma.hiselectors.selectors.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "셀렉터스가 참여한 기수")
public record SelectorsGenerationResponse(
        @Schema(description = "기수 ID") Long generationId,
        @Schema(description = "기수명", example = "3기") String generationName,
        @Schema(description = "모집 시작일") LocalDateTime startDate,
        @Schema(description = "모집 종료일") LocalDateTime endDate,
        @Schema(description = "기수 상태", example = "ACTIVE") String status,
        @Schema(description = "참여 등록 시각") LocalDateTime joinedAt
) {
}
