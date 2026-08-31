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
        @Schema(description = "대표 카테고리가 요청 카테고리와 일치하는지") boolean representativeMatch,
        String matchReason
) {
}
