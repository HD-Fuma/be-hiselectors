package com.fuma.hiselectors.settlement.event;

public record SettlementCarryoverConfirmedEvent(
        Long settlementId,
        long accumulatedAmount,
        long minimumPaymentAmount
) {
}
