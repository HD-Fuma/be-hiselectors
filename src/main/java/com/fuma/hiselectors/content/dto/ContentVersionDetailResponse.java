package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.inspection.dto.ContentViolationResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ContentVersionDetailResponse(
        Long contentVersionId,
        Long versionNo,
        ContentVersionCreationReason creationReason,
        ContentVersionStatus inspectionStatus,
        ContentInspectionDecision inspectionDecision,
        LocalDateTime createdAt,
        LocalDateTime inspectedAt,
        List<ContentVersionMediaResponse> media,
        ContentReportResponse contentReport,
        List<ContentViolationResponse> violations
) {
    public ContentVersionDetailResponse {
        media = List.copyOf(media);
        violations = List.copyOf(violations);
    }
}
