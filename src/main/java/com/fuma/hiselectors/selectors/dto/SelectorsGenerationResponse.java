package com.fuma.hiselectors.selectors.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "셀렉터스가 참여한 기수")
public record SelectorsGenerationResponse(
        @Schema(description = "기수 ID") Long generationId,
        @Schema(description = "기수명", example = "3기") String generationName,
        @Schema(description = "모집 시작일") LocalDateTime startDate,
        @Schema(description = "모집 종료일") LocalDateTime endDate,
        @Schema(description = "활동 시작일") LocalDateTime activityStartDate,
        @Schema(description = "활동 종료일") LocalDateTime activityEndDate,
        @Schema(description = "기수 상태", example = "ACTIVE") String status,
        @Schema(description = "참여 등록 시각") LocalDateTime joinedAt,
        @Schema(description = "기수 총 매출") long totalSales,
        @Schema(description = "기수 구매확정 건수") long confirmedPurchaseCount,
        @Schema(description = "기수 지급 완료 수수료") long paidCommissionAmount
) {

    public SelectorsGenerationResponse(
            Long generationId,
            String generationName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            LocalDateTime activityStartDate,
            LocalDateTime activityEndDate,
            String status,
            LocalDateTime joinedAt) {
        this(generationId, generationName, startDate, endDate, activityStartDate,
                activityEndDate, status, joinedAt, 0L, 0L, 0L);
    }
}
