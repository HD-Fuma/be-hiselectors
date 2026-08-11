package com.fuma.hiselectors.selectors.model;

import java.time.LocalDateTime;

public record SelectorResponse(
        Long id,
        Long applicationId,
        Long userId,
        String selectorsRoleId,
        String selectorsCode,
        String selectorsNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SelectorResponse from(Selector selector) {
        return new SelectorResponse(
                selector.getId(),
                selector.getApplicationId(),
                selector.getUserId(),
                selector.getSelectorsRoleId(),
                selector.getSelectorsCode(),
                selector.getSelectorsNickname(),
                selector.getCreatedAt(),
                selector.getUpdatedAt()
        );
    }
}
