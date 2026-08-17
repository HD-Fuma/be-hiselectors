package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 카테고리·플랫폼 안에서 계산된 크리에이터 영향력 순위. */
@Schema(description = "영향력 상위 크리에이터")
public record InfluenceRankedCreator(
        @Schema(description = "동일 카테고리·플랫폼 내 순위", example = "1") int rank,
        @Schema(description = "크리에이터 풀 ID", example = "113") Long creatorId,
        @Schema(description = "SNS 코드", example = "YOUTUBE") String snsCode,
        @Schema(description = "플랫폼 계정 ID") String accountId,
        @Schema(description = "크리에이터명") String creatorName,
        @Schema(description = "팔로워/구독자 수") Long followerCount,
        @Schema(description = "ER 지수") BigDecimal engagementRate,
        @Schema(description = "최근 활동일") LocalDateTime lastContentAt,
        @Schema(description = "대표 카테고리 코드", example = "BEAUTY") String categoryCode,
        @Schema(description = "크리에이터 풀 최초 등록일") LocalDateTime discoveredAt,
        @Schema(description = "팔로워 상대 점수 (0~100)") BigDecimal followerScore,
        @Schema(description = "ER 상대 점수 (0~100)") BigDecimal engagementScore,
        @Schema(description = "최근성 상대 점수 (최근 콘텐츠일 기준, 0~100)") BigDecimal recencyScore,
        @Schema(description = "종합 영향력 점수 (0~100)") BigDecimal influenceScore
) {
}
