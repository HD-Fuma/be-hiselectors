package com.fuma.hiselectors.performance.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record PerformanceSummaryResponse(
        YearMonth activityMonth,
        BigDecimal settlementRate,
        PerformanceMetricsResponse metrics,
        PerformanceMetricsResponse previousMonthMetrics,
        List<PerformanceTrendResponse> trends,
        List<ProductPerformanceResponse> topProducts
) {
}
