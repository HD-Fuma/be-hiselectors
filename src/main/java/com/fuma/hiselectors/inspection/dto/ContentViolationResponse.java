package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.time.LocalDateTime;
import java.util.List;

public record ContentViolationResponse(
        Long violationItemId,
        Long violationEvidenceHistoryId,
        Long contentReportId,
        Long inspectionPolicyId,
        ViolationTypeCode violationType,
        String violationTypeDescription,
        ViolationStatus currentStatus,
        ViolationEvidence evidence,
        List<EvidenceLocationResponse> resolvedLocations,
        LocalDateTime detectedAt
) {
    public ContentViolationResponse {
        resolvedLocations = resolvedLocations == null
                ? List.of() : List.copyOf(resolvedLocations);
    }
}
