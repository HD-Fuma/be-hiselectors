package com.fuma.hiselectors.selectors.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "셀렉터스 SNS 프로필·카테고리 보강 결과")
public record SelectorSnsEnrichmentResponse(
        @Schema(description = "셀렉터스 ID") Long selectorsId,
        @Schema(description = "보강 후 프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "이번 요청에서 프로필 이미지를 새로 넣었는지") boolean profileImageUpdated,
        @Schema(description = "보강 후 대표 카테고리 코드", example = "FOOD") String category,
        @Schema(description = "이번 요청에서 카테고리를 새로 넣었는지") boolean categoryUpdated,
        @Schema(description = "프로필을 건너뛴 이유. 채웠으면 null") String profileSkipReason,
        @Schema(description = "카테고리를 건너뛴 이유. 채웠으면 null") String categorySkipReason
) {

    @Schema(description = "셀렉터스 SNS 프로필·카테고리 일괄 보강 결과")
    public record Batch(
            @Schema(description = "이번에 집어 든 대상 수") int targetCount,
            @Schema(description = "프로필 이미지를 새로 넣은 수") int profileImageUpdatedCount,
            @Schema(description = "카테고리를 새로 넣은 수") int categoryUpdatedCount,
            @Schema(description = "예외로 실패한 수") int failedCount,
            @Schema(description = "대상별 결과") List<SelectorSnsEnrichmentResponse> results
    ) {
    }
}
