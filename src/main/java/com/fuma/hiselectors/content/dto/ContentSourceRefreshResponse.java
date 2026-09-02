package com.fuma.hiselectors.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "기존 콘텐츠 원본 메타·성과 갱신 결과")
public record ContentSourceRefreshResponse(
        @Schema(description = "이번에 집어 든 대상 수") int targetCount,
        @Schema(description = "프로필 이미지를 갱신한 수") int profileImageUpdatedCount,
        @Schema(description = "제목·본문을 갱신한 수") int textsUpdatedCount,
        @Schema(description = "조회수·좋아요·댓글을 저장한 수") int engagementUpdatedCount,
        @Schema(description = "실패한 수") int failedCount,
        @Schema(description = "대상별 결과") List<Item> results
) {

    public ContentSourceRefreshResponse {
        results = List.copyOf(results);
    }

    @Schema(description = "콘텐츠 1건 갱신 결과")
    public record Item(
            @Schema(description = "콘텐츠 ID") Long contentId,
            @Schema(description = "셀렉터스 ID") Long selectorsId,
            @Schema(description = "보강 후 프로필 이미지 URL") String profileImageUrl,
            @Schema(description = "이번 요청에서 프로필 이미지를 넣었는지") boolean profileImageUpdated,
            @Schema(description = "보강 후 제목·본문") List<String> texts,
            @Schema(description = "이번 요청에서 제목·본문을 넣었는지") boolean textsUpdated,
            @Schema(description = "조회수") Long viewCount,
            @Schema(description = "좋아요 수") Long likeCount,
            @Schema(description = "댓글 수") Long commentCount,
            @Schema(description = "이번 요청에서 성과를 저장했는지") boolean engagementUpdated,
            @Schema(description = "실패 이유. 성공이면 null") String failureReason
    ) {

        public Item {
            texts = texts == null ? List.of() : List.copyOf(texts);
        }
    }
}
