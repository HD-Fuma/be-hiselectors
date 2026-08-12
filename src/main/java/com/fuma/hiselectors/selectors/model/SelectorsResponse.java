package com.fuma.hiselectors.selectors.model;

import java.time.LocalDateTime;

public record SelectorsResponse(
        Long id,
        Long applicationId,
        Long userId,
        String selectorsRoleId,
        String selectorsCode,
        String selectorsNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SelectorsResponse from(Selectors selectors) {
        return new SelectorsResponse(
                selectors.getId(),
                selectors.getApplicationId(),
                selectors.getUserId(),
                selectors.getSelectorsRoleId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                selectors.getCreatedAt(),
                selectors.getUpdatedAt()
        );
    }
}
