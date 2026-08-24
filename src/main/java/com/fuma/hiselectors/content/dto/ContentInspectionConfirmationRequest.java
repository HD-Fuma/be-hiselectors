package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ContentInspectionConfirmationRequest(
        @NotNull ContentInspectionDecision decision,
        @NotNull List<@Valid ViolationDecision> violations
) {
    public record ViolationDecision(
            @NotNull Long violationItemId,
            @NotNull ViolationStatus status
    ) {
    }
}
