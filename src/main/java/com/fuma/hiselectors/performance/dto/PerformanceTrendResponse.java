package com.fuma.hiselectors.performance.dto;

import java.time.LocalDate;

public record PerformanceTrendResponse(
        LocalDate date,
        long clickCount,
        long conversionCount,
        long conversionAmount
) {
}
