package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.inspection.dto.ContentViolationResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ContentVersionDetailResponse(
        Long contentVersionId,
        Long versionNo,
        ContentVersionStatus inspectionStatus,
        LocalDateTime createdAt,
        LocalDateTime inspectedAt,
        List<String> texts,
        ContentReportResponse contentReport,
        List<ContentViolationResponse> violations
) {
    public ContentVersionDetailResponse {
        texts = List.copyOf(texts);
        violations = List.copyOf(violations);
    }
}
