package com.fuma.hiselectors.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "관리자 메인 대시보드 매출·예상 정산액 추이")
public record DashboardSalesSettlementTrendResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<Point> points
) {

    public record Point(
            LocalDate date,
            BigDecimal salesAmount,
            @Schema(description = "확정 매출과 현재 수수료율로 계산한 예상 정산액")
            BigDecimal settlementAmount
    ) {
    }
}
