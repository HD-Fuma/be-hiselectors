package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminApplicationSummaryResponse(
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
        String snsDisplayName,
        String profileUrl,
        String profileImageUrl,
        Long followerCount,
        Long totalContentCount,
        Long recent90DayContentCount,
        LocalDateTime lastPublishedAt,
        BigDecimal engagementRate,
        ApplicationStatus status,
        MediaCollectionStatus mediaCollectionStatus,
        LocalDateTime appliedAt,
        LocalDateTime mediaCollectedAt,
        LocalDateTime updatedAt
) {
}
