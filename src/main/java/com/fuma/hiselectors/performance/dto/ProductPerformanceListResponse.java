package com.fuma.hiselectors.performance.dto;

import java.time.YearMonth;
import java.util.List;

public record ProductPerformanceListResponse(
        YearMonth activityMonth,
        long conversionCount,
        int totalProductCount,
        List<ProductPerformanceResponse> products
) {
}
