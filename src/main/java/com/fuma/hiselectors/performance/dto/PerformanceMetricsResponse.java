package com.fuma.hiselectors.performance.dto;

import java.math.BigDecimal;

public record PerformanceMetricsResponse(
        long estimatedSettlementAmount,
        long conversionAmount,
        long conversionCount,
        long clickCount,
        BigDecimal conversionRate
) {
}
