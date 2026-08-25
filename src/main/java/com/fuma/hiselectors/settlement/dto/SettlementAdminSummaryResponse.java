package com.fuma.hiselectors.settlement.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record SettlementAdminSummaryResponse(
        YearMonth activityMonth,
        long settlementCount,
        long confirmedPurchaseCount,
        long confirmedSalesAmount,
        long settlementAmount,
        BigDecimal commissionToSalesRate
) {
}
