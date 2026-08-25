package com.fuma.hiselectors.content.service;

public record ContentViolationConfirmedEvent(
        String adminLoginId,
        Long contentId,
        Long selectorsId
) {
}
