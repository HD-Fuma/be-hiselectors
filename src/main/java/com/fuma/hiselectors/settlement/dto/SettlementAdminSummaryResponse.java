package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.settlement.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record SettlementAdminSummaryResponse(
        YearMonth activityMonth,
        long settlementCount,
        long confirmedPurchaseCount,
        long confirmedSalesAmount,
        long settlementAmount,
        BigDecimal commissionToSalesRate,
        List<MonthlyTrend> monthlyTrend,
        List<StatusDistribution> statusDistribution
) {

    public SettlementAdminSummaryResponse {
        monthlyTrend = List.copyOf(monthlyTrend);
        statusDistribution = List.copyOf(statusDistribution);
    }

    public record MonthlyTrend(
            YearMonth activityMonth,
            long settlementCount,
            long confirmedPurchaseCount,
            long confirmedSalesAmount,
            long settlementAmount,
            BigDecimal commissionToSalesRate
    ) {
    }

    public record StatusDistribution(
            SettlementStatus status,
            long settlementCount,
            long settlementAmount
    ) {
    }
}
