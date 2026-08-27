package com.fuma.hiselectors.application.service;

public record ApplicationSubmittedEvent(
        Long userId,
        Long applicationId,
        String name
) {
}
