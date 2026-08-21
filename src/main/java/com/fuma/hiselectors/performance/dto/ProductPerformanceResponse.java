package com.fuma.hiselectors.performance.dto;

import java.math.BigDecimal;

public record ProductPerformanceResponse(
        Long productId,
        String productCode,
        String productName,
        String brandName,
        String thumbnailUrl,
        long clickCount,
        long conversionCount,
        long conversionAmount,
        BigDecimal conversionRate,
        long estimatedSettlementAmount
) {
}
