package com.fuma.hiselectors.settlement.dto;

import java.time.YearMonth;

/** 전전월 정산 이력의 상태 변경 결과. */
public record SettlementPaymentResponse(
        YearMonth targetSettlementMonth,
        int processedCount,
        int settledCount,
        int heldCount,
        int skippedCount,
        int failedCount
) {
}
