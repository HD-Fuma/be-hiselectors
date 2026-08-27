package com.fuma.hiselectors.content.service;

import java.util.List;

public record ContentViolationConfirmedEvent(
        String adminLoginId,
        Long contentId,
        Long selectorsId,
        List<Long> violationItemIds
) {
    public ContentViolationConfirmedEvent {
        violationItemIds = List.copyOf(violationItemIds);
    }
}
