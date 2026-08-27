package com.fuma.hiselectors.penalty.service;

public record PenaltyReleasedEvent(
        Long senderAdminId,
        Long penaltyHistoryId,
        Long selectorsId
) {
}
