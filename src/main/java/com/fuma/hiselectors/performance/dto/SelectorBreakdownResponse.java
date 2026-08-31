package com.fuma.hiselectors.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "셀렉터스 개인 상세 성과: 진행중인 상품별·캠페인별 확정 매출 분포")
public record SelectorBreakdownResponse(
        Long selectorId,
        String selectorsCode,
        String nickname,
        @Schema(description = "셀렉터스 대표 카테고리 코드") String category,
        List<ProductPerformance> products,
        List<CampaignPerformance> campaigns
) {

    public record ProductPerformance(
            Long productId,
            String productName,
            String brandName,
            String thumbnailUrl,
            String category,
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity
    ) {
    }

    public record CampaignPerformance(
            Long campaignId,
            String title,
            BigDecimal confirmedSales,
            long confirmedOrderCount,
            long soldQuantity
    ) {
    }
}
