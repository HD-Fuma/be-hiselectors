package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;

public record SelectorsPenaltyResponse(
        Long selectorsId,
        String selectorsCode,
        String selectorsNickname,
        long totalPenaltyCount,
        long activePenaltyCount,
        boolean blacklistTarget,
        List<PenaltyHistoryResponse> histories
) {
    public static SelectorsPenaltyResponse of(
            Selectors selectors, List<PenaltyHistory> histories, long blacklistThreshold) {
        long activePenaltyCount = histories.stream()
                .filter(history -> history.getStatus() == PenaltyStatus.ACTIVE)
                .count();
        return new SelectorsPenaltyResponse(
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                histories.size(),
                activePenaltyCount,
                selectors.isBlacklisted(),
                histories.stream().map(PenaltyHistoryResponse::from).toList());
    }
}
