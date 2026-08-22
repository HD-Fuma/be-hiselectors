package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationStatus;
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
            List<ContentFormatCount> contentFormats
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
