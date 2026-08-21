package com.fuma.hiselectors.inspection.dto;

import java.util.List;

public record ReinspectStaleResponse(
        int targetCount,
        int successCount,
        int failureCount,
        List<Long> failedVersionIds
) {
}
