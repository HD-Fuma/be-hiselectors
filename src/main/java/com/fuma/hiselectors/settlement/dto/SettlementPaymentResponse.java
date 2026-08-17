package com.fuma.hiselectors.settlement.dto;

import java.time.YearMonth;

public record SettlementPaymentResponse(
        YearMonth paymentMonth,
        YearMonth latestEligibleActivityMonth,
        int processedCount,
        int settledCount,
        int heldCount,
        int skippedCount,
        int failedCount
) {
}
