package com.fuma.hiselectors.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "셀렉터스 성과 추이")
public record SelectorPerformanceTrendResponse(
        @Schema(description = "집계 단위", example = "DAY") Bucket bucket,
        @Schema(description = "추이 시작일") LocalDate startDate,
        @Schema(description = "추이 종료일") LocalDate endDate,
        List<Point> points
) {

    public enum Bucket {
        DAY,
        MONTH
    }

    public record Point(
            @Schema(description = "버킷 시작일. 월 단위면 해당 월 1일") LocalDate date,
            @Schema(description = "확정 매출") BigDecimal totalSales,
            @Schema(description = "확정 주문 수") long confirmedOrderCount
    ) {
    }
}
