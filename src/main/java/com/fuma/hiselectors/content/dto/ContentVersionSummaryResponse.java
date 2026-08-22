package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentVersionStatus;
import java.time.LocalDateTime;

public record ContentVersionSummaryResponse(
        Long contentVersionId,
        Long versionNo,
        ContentVersionStatus inspectionStatus,
        LocalDateTime createdAt,
        LocalDateTime inspectedAt
) {
}
