package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.inspection.model.ViolationStatus;

public record ViolationActionResponse(
        Long violationId,
        ViolationStatus status,
        boolean penaltyCreated,
        Long notificationId
) {
}
