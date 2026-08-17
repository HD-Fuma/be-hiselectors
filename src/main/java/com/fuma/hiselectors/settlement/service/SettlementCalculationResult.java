package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.model.SettlementHistory;

public record SettlementCalculationResult(
        SettlementHistory settlementHistory,
        SettlementCalculationOutcome outcome
) {
}
