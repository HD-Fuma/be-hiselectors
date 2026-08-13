package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 하루 동안 새로 발굴된 크리에이터 중 리포트 생성 기준을 통과한 후보. */
@Schema(description = "카테고리별 일일 리포트 생성 후보")
public record DailyReportCandidatesResponse(
        @Schema(description = "선정 기준일") LocalDate selectionDate,
        @Schema(description = "카테고리 코드", example = "BEAUTY") String categoryCode,
        @Schema(description = "플랫폼별 상위 비율", example = "10") int topPercent,
        @Schema(description = "점수 비교 풀의 최근 활동 기간(일)", example = "90")
        int activeWithinDays,
        @Schema(description = "카테고리당 일일 최대 선정 인원", example = "5") int dailyLimit,
        @Schema(description = "최근 활동 조건을 통과한 전체 비교 후보 수") int rankingPoolSize,
        @Schema(description = "비교 후보 중 오늘 처음 등록된 인원 수") int discoveredTodayCount,
        @Schema(description = "최종 선정 인원 수") int selectedCount,
        List<InfluenceRankedCreator> creators
) {
}
