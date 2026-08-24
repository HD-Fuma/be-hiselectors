package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import java.time.LocalDateTime;

public record ContentVersionSummaryResponse(
        Long contentVersionId,
        Long versionNo,
        ContentVersionCreationReason creationReason,
        ContentVersionStatus inspectionStatus,
        ContentInspectionDecision inspectionDecision,
        LocalDateTime createdAt,
        LocalDateTime inspectedAt
) {
}
