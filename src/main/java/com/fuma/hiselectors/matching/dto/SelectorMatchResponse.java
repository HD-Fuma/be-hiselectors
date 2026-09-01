package com.fuma.hiselectors.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "신규 상품·캠페인 추천 셀렉터스")
public record SelectorMatchResponse(
        Long selectorId,
        String selectorsCode,
        String nickname,
        @Schema(description = "셀렉터스 대표 카테고리 코드") String category,
        String profileImageUrl,
        @Schema(description = "해당 카테고리에서의 과거 확정 매출") BigDecimal categorySales,
        long categoryOrderCount,
        @Schema(description = "전환율(%). 카테고리 상품 클릭 대비 확정 주문. 클릭이 없으면 null")
        BigDecimal conversionRate,
        @Schema(description = "추천 점수 0~100. 최근성 가중 매출 + 전환율의 후보 내 상대 점수")
        int matchScore,
        @Schema(description = "대표 카테고리가 요청 카테고리와 일치하는지") boolean representativeMatch,
        @Schema(description = "실적 없이 대표 카테고리 일치만으로 보완 추천된 셀렉터스인지") boolean fallback,
        String matchReason
) {
}
