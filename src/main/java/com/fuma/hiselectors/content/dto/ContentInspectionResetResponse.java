package com.fuma.hiselectors.content.dto;

public record ContentInspectionResetResponse(
        int resetVersionCount,
        int resetViolationCount
) {
}
