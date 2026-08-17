package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** 정산월은 구매가 발생한 활동월(purchased_at 기준)이다. */
public record SettlementEstimateResponse(
        Long settlementId,
        Long selectorsId,
        String selectorsCode,
        String selectorsNickname,
        YearMonth settlementMonth,
        Long confirmedPurchaseCount,
        Long totalSales,
        BigDecimal commissionRate,
        Long estimatedCommission,
        SettlementStatus status,
        LocalDateTime calculatedAt,
        LocalDateTime updatedAt
) {

    public static SettlementEstimateResponse of(
            SettlementHistory history, Selectors selectors) {
        return new SettlementEstimateResponse(
                history.getId(),
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                YearMonth.from(history.getSettlementMonth()),
                history.getConfirmedPurchaseCount(),
                history.getTotalSales(),
                history.getCommissionRate(),
                history.getCommission(),
                history.getStatus(),
                history.getCalculatedAt(),
                history.getUpdatedAt()
        );
    }
}
