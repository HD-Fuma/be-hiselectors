package com.fuma.hiselectors.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "관리자 셀렉터스 성과 목록 항목")
public record SelectorPerformanceResponse(
        @Schema(description = "셀렉터스 ID") Long selectorId,
        @Schema(description = "셀렉터스 코드", example = "SEL0001") String selectorCode,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "활동 상태 코드", example = "ACTIVE") String roleId,
        @Schema(description = "가장 최근 참여 기수 ID") Long generationId,
        @Schema(description = "가장 최근 참여 기수", example = "4기") String generationName,
        @Schema(description = "대표 카테고리 코드", example = "BEAUTY") String category,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가장 최근 우수 활동자 선정 기수", example = "3기")
        String excellentGenerationName,
        @Schema(description = "가장 최근 우수 활동자 선정 기수의 확정 매출")
        BigDecimal excellentGenerationSales,
        @Schema(description = "조회 기간 내 확정 매출") BigDecimal totalSales,
        @Schema(description = "조회 기간 내 확정 주문 수") long confirmedOrderCount,
        @Schema(description = "조회 기간 내 상품 클릭 수") long clickCount,
        @Schema(description = "조회 기간 내 수집 콘텐츠 수") long contentCount,
        @Schema(description = "조회 기간 내 발생 수수료. 지급 완료액이 아니다")
        BigDecimal accruedCommissionAmount,
        @Schema(description = "우수 셀렉터스 여부") boolean isExcellent,
        @Schema(description = "우수 활동자 선정 사유") String excellentActivityType
) {
}
