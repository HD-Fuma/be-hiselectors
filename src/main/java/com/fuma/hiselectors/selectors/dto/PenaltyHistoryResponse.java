package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import java.time.LocalDateTime;

public record PenaltyHistoryResponse(
        Long id,
        Long generationId,
        Long violationTypeId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        PenaltyStatus status
) {
    public static PenaltyHistoryResponse from(PenaltyHistory history) {
        return new PenaltyHistoryResponse(
                history.getId(),
                history.getGenerationId(),
                history.getViolationTypeId(),
                history.getStartedAt(),
                history.getEndedAt(),
                history.getStatus());
    }
}
