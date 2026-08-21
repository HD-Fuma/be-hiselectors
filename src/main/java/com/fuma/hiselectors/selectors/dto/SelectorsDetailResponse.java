package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 셀렉터스 기본 정보와 운영 상세. */
@Schema(description = "셀렉터스 상세")
public record SelectorsDetailResponse(
        @Schema(description = "셀렉터스 ID") Long id,
        @Schema(description = "셀렉터스 코드") String selectorsCode,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "역할 코드", example = "ACTIVE") String roleId,
        @Schema(description = "역할명", example = "활성") String roleName,
        @Schema(description = "연결된 지원서 ID") Long applicationId,
        @Schema(description = "연결된 사용자 ID") Long userId,
        @Schema(description = "등록 시각") LocalDateTime createdAt,
        @Schema(description = "수정 시각") LocalDateTime updatedAt,
        @Schema(description = "참여 기수 이력 (최신순)")
        List<SelectorsGenerationResponse> generations,
        @Schema(description = "SNS 계정") SelectorsSnsAccountResponse snsAccount,
        @Schema(description = "누적 패널티 횟수") long totalPenaltyCount,
        @Schema(description = "활성 패널티 횟수") long activePenaltyCount,
        @Schema(description = "블랙리스트 대상 여부") boolean blacklistTarget,
        @Schema(description = "최근 콘텐츠 5개") List<ContentResponse> contents,
        @Schema(description = "콘텐츠 성과 합계") PerformanceResponse performance
) {

    public static SelectorsDetailResponse of(
            Selectors selectors,
            String roleName,
            List<SelectorsGenerationResponse> generations,
            SelectorsSnsAccountResponse snsAccount,
            List<PenaltyHistory> penaltyHistories,
            List<Content> contents,
            Map<Long, ContentEngagement> latestEngagements,
            long blacklistThreshold) {
        long activePenaltyCount = penaltyHistories.stream()
                .filter(history -> history.getStatus() == PenaltyStatus.ACTIVE)
                .count();
        return new SelectorsDetailResponse(
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                selectors.getSelectorsRoleId(),
                roleName,
                selectors.getApplicationId(),
                selectors.getUserId(),
                selectors.getCreatedAt(),
                selectors.getUpdatedAt(),
                generations,
                snsAccount,
                penaltyHistories.size(),
                activePenaltyCount,
                selectors.isBlacklisted(),
                contents.stream()
                        .limit(5)
                        .map(content -> ContentResponse.from(
                                content, latestEngagements.get(content.getId())))
                        .toList(),
                PerformanceResponse.from(
                        contents,
                        latestEngagements,
                        snsAccount != null && snsAccount.lastCollectedAt() != null)
        );
    }

    public record ContentResponse(
            Long id,
            String snsCode,
            String contentUrl,
            String contentType,
            LocalDateTime createdAt,
            Long viewCount,
            Long likeCount,
            Long commentCount
    ) {
        private static ContentResponse from(
                Content content, ContentEngagement engagement) {
            return new ContentResponse(
                    content.getId(),
                    content.getSnsCode().name(),
                    content.getContentUrl(),
                    content.getContentType().name(),
                    content.getCreatedAt(),
                    engagement == null ? null : engagement.getViewCount(),
                    engagement == null ? null : engagement.getLikeCount(),
                    engagement == null ? null : engagement.getCommentCount()
            );
        }
    }

    public record PerformanceResponse(
            Long contentCount,
            Long totalViewCount,
            Long totalLikeCount,
            Long totalCommentCount
    ) {
        private static PerformanceResponse from(
                List<Content> contents,
                Map<Long, ContentEngagement> latestEngagements,
                boolean contentCollectionCompleted) {
            if (!contentCollectionCompleted) {
                return new PerformanceResponse(null, null, null, null);
            }
            if (contents.isEmpty()) {
                return new PerformanceResponse(0L, 0L, 0L, 0L);
            }
            return new PerformanceResponse(
                    (long) contents.size(),
                    sumWhenComplete(contents, latestEngagements, ContentEngagement::getViewCount),
                    sumWhenComplete(contents, latestEngagements, ContentEngagement::getLikeCount),
                    sumWhenComplete(contents, latestEngagements, ContentEngagement::getCommentCount));
        }

        private static Long sumWhenComplete(
                List<Content> contents,
                Map<Long, ContentEngagement> latestEngagements,
                Function<ContentEngagement, Long> metric) {
            long total = 0;
            for (Content content : contents) {
                ContentEngagement engagement = latestEngagements.get(content.getId());
                if (engagement == null) return null;
                Long value = metric.apply(engagement);
                if (value == null) return null;
                total += value;
            }
            return total;
        }
    }
}
