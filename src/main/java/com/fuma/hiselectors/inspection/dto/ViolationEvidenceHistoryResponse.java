package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import java.time.LocalDateTime;

public record ViolationEvidenceHistoryResponse(
        Long id,
        Long contentVersionId,
        Long inspectionPolicyId,
        ViolationEvidence evidence,
        LocalDateTime detectedAt
) {
}
