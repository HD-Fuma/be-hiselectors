package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.time.LocalDateTime;

public record ContentViolationResponse(
        Long violationItemId,
        Long violationEvidenceHistoryId,
        Long inspectionPolicyId,
        ViolationTypeCode violationType,
        String violationTypeDescription,
        ViolationStatus currentStatus,
        ViolationEvidence evidence,
        LocalDateTime detectedAt
) {
}
