package com.fuma.hiselectors.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "셀렉터스 성과 대시보드 요약")
public record SelectorPerformanceSummaryResponse(
        Universe universe,
        Kpis kpis,
        Distribution distribution,
        List<TopSelector> top5,
        List<CategoryPerformance> categories,
        Watchlist watchlist
) {

    public record Universe(
            @Schema(description = "집계 대상 인원") long selectorCount,
            List<Long> generationIds,
            LocalDate previousStartDate,
            LocalDate previousEndDate
    ) {
    }

    public record Kpis(
            BigDecimal totalSales,
            long confirmedOrderCount,
            long clickCount,
            @Schema(description = "구매전환율(%). 클릭이 없으면 0") BigDecimal conversionRate,
            BigDecimal accruedCommissionAmount,
            BigDecimal averageSales,
            BigDecimal medianSales,
            BigDecimal previousTotalSales,
            Long previousConfirmedOrderCount,
            BigDecimal previousAccruedCommissionAmount,
            BigDecimal previousAverageSales,
            @Schema(description = "전 기간 대비 매출 증감률(%). 이전 매출이 0이면 null")
            BigDecimal totalSalesChangeRate,
            BigDecimal confirmedOrderChangeRate,
            BigDecimal accruedCommissionChangeRate,
            BigDecimal averageSalesChangeRate
    ) {
    }

    public record Distribution(
            long sellingSelectorCount,
            long zeroSalesSelectorCount,
            @Schema(description = "상위 집중도(%). 총 매출이 0이면 0") BigDecimal topShareRate,
            List<SalesBucket> buckets
    ) {
    }

    public record SalesBucket(
            @Schema(description = "ZERO | UP_TO_100000 | UP_TO_500000 | UP_TO_1000000 | OVER_1000000")
            String key,
            long selectorCount
    ) {
    }

    public record TopSelector(
            Long selectorId,
            String nickname,
            String profileImageUrl,
            String generationName,
            BigDecimal totalSales,
            int rank,
            @Schema(description = "이전 기간 매출 순위. 이전 매출이 0이거나 비교 불가면 null")
            Integer previousRank
    ) {
    }

    public record CategoryPerformance(
            @Schema(description = "셀렉터스 대표 카테고리 코드. 없으면 null") String category,
            long selectorCount,
            BigDecimal averageSales,
            BigDecimal medianSales,
            @Schema(description = "인원 5명 미만이면 참고 값") boolean reference
    ) {
    }

    public record Watchlist(
            long noClicks,
            long noUploads,
            long clicksWithoutPurchase,
            long salesDrop,
            long salesSurge,
            long newTop10
    ) {
    }
}
