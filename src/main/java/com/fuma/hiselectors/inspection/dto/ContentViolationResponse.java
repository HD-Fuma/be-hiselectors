package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;

public record ContentViolationResponse(
        Long violationItemId,
        ViolationTypeCode violationType,
        String violationTypeDescription,
        ViolationStatus status,
        ViolationEvidence evidence
) {
}
