package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.Application;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        Long userId,
        boolean alarmYn,
        LocalDateTime policyAgreedAt,
        LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.isAlarmYn(),
                application.getPolicyAgreedAt(),
                application.getCreatedAt()
        );
    }
}
