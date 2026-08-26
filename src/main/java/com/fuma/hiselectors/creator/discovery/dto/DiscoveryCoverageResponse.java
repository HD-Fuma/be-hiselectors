package com.fuma.hiselectors.creator.discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "카테고리별 YouTube 크리에이터 발굴 포화도")
public record DiscoveryCoverageResponse(
        Long categoryId,
        String categoryCode,
        String categoryName,
        int executedKeywordCount,
        int minimumKeywordCount,
        int observedCreators,
        BigDecimal estimatedCreators,
        BigDecimal coveragePercent,
        int singletonCreators,
        int doubletonCreators,
        CoverageStatus status,
        String recommendation,
        List<KeywordCoverage> keywords
) {

    public enum CoverageStatus {
        INSUFFICIENT_DATA,
        EXPLORING,
        MATURING,
        SATURATING
    }

    public record KeywordCoverage(
            Long keywordId,
            String keyword,
            LocalDateTime lastRunAt,
            int discoveredCreators,
            int exclusiveCreators,
            int overlapCreators,
            BigDecimal overlapPercent
    ) {
    }
}
