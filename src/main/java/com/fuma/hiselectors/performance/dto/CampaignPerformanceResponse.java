package com.fuma.hiselectors.performance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CampaignPerformanceResponse(
        Long campaignId,
        LocalDate startDate,
        LocalDate endDate,
        Summary summary,
        List<DailyPerformance> daily,
        List<ProductPerformance> products,
        List<SelectorPerformance> selectors
) {

    public record Summary(
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity,
            long contributingSelectorCount,
            long canceledOrReturnedOrderCount,
            BigDecimal canceledOrReturnedRate
    ) {
    }

    public record DailyPerformance(
            LocalDate date,
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity
    ) {
    }

    public record ProductPerformance(
            Long productId,
            String productCode,
            String productName,
            String brandName,
            String thumbnailUrl,
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity,
            long contributingSelectorCount
    ) {
    }

    public record SelectorPerformance(
            Long selectorId,
            String selectorCode,
            String nickname,
            String profileImageUrl,
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity,
            long productCount
    ) {
    }
}
