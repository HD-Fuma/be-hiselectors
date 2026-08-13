package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 카테고리·플랫폼별 영향력 상위 N% 조회 결과. */
@Schema(description = "영향력 상위 N% 조회 결과")
public record TopPercentInfluenceResponse(
        @Schema(description = "카테고리 코드", example = "BEAUTY") String categoryCode,
        @Schema(description = "SNS 코드", example = "YOUTUBE") String snsCode,
        @Schema(description = "조회한 상위 비율", example = "10") int topPercent,
        @Schema(description = "후보에 포함한 최근 활동 기간(일)", example = "90")
        int activeWithinDays,
        @Schema(description = "전체 후보 수", example = "19") int totalCandidates,
        @Schema(description = "선정된 인원 수", example = "2") int selectedCount,
        List<InfluenceRankedCreator> creators
) {
}
