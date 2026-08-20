package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        Long userId,
        Long generationId,
        SnsPlatform snsCode,
        String snsAccountId,
        Long followerCount,
        Long contentCount,
        LocalDateTime lastContentAt,
        BigDecimal engagementRate,
        boolean alarmYn,
        LocalDateTime policyAgreedAt,
        ApplicationStatus status,
        LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getGenerationId(),
                application.getSnsCode(),
                application.getSnsAccountId(),
                application.getFollowerCount(),
                application.getContentCount(),
                application.getLastContentAt(),
                application.getEngagementRate(),
                application.isAlarmYn(),
                application.getPolicyAgreedAt(),
                application.getStatus(),
                application.getCreatedAt()
        );
    }
}
