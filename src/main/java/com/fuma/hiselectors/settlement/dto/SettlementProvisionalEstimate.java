package com.fuma.hiselectors.settlement.dto;

import java.time.LocalDateTime;

/** 취소·반품에 따라 달라질 수 있는 당월 실시간 잠정 수수료. */
public record SettlementProvisionalEstimate(
        Long purchaseCount,
        Long salesAmount,
        Long settlementAmount,
        LocalDateTime asOf
) {
}
