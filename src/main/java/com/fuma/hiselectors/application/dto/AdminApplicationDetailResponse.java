package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminApplicationDetailResponse(
        Long id,
        Long userId,
        String hiId,
        String applicantName,
        String email,
        String phone,
        Long generationId,
        String generationName,
        SnsPlatform snsCode,
        String snsAccountId,
        String profileUrl,
        Long followerCount,
        ApplicationStatus status,
        MediaCollectionStatus mediaCollectionStatus,
        ContentAnalysisStatus analysisStatus,   // AI 리포트 생성 진행상태(DONE 이면 /ai-report 조회 가능, IN_PROGRESS/PENDING 이면 생성 중)
        LocalDateTime appliedAt,
        LocalDateTime mediaCollectedAt,
        LocalDateTime updatedAt,
        QuantitativeMetrics metrics,
        List<ApplicationMediaResponse> contents
) {

    public record QuantitativeMetrics(
            int analysisWindowDays,
            Long totalContentCount,
            Long recent90DayContentCount,
            LocalDateTime lastPublishedAt,
            UploadCadence uploadCadence,
            MetricAverage averageViewCount,
            MetricAverage averageLikeCount,
            MetricAverage averageCommentCount,
            MetricAverage engagementRate,
            List<ContentFormatCount> contentFormats,
            // 아래 3개는 미디어 수집이 끝난 전체 지원자 중 상위 N%(1=최상위). 비교 대상이 없으면 null.
            Integer viewCountPercentile,
            Integer likeCountPercentile,
            Integer commentCountPercentile
    ) {
    }

    public record UploadCadence(
            long sampleCount,
            BigDecimal dailyAverage,
            BigDecimal weeklyAverage,
            Long maximumGapDays
    ) {
    }

    public record MetricAverage(BigDecimal value, long sampleCount) {
    }

    public record ContentFormatCount(String contentType, long count) {
    }
}
