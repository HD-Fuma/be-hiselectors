package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record SettlementEstimateResponse(
        Long settlementId,
        Long selectorsId,
        String selectorsCode,
        String selectorsNickname,
        YearMonth activityMonth,
        YearMonth settlementMonth,
        YearMonth paymentMonth,
        Long confirmedPurchaseCount,
        Long confirmedSalesAmount,
        BigDecimal settlementRate,
        Long settlementAmount,
        SettlementProvisionalEstimate provisionalEstimate,
        SettlementStatus status,
        LocalDateTime calculatedAt,
        LocalDateTime updatedAt
) {

    public static SettlementEstimateResponse of(SettlementHistory history, Selectors selectors) {
        return of(history, selectors, null);
    }

    public static SettlementEstimateResponse of(
            SettlementHistory history,
            Selectors selectors,
            SettlementProvisionalEstimate provisionalEstimate) {
        return new SettlementEstimateResponse(
                history.getId(),
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                YearMonth.from(history.getActivityMonth()),
                YearMonth.from(history.getActivityMonth()).plusMonths(1),
                paymentMonth(history),
                history.getConfirmedPurchaseCount(),
                history.getTotalSales(),
                history.getSettlementRate(),
                history.getSettlementAmount(),
                provisionalEstimate,
                history.getStatus(),
                history.getCalculatedAt(),
                history.getUpdatedAt()
        );
    }

    private static YearMonth paymentMonth(SettlementHistory history) {
        if (history.getScheduledPaymentYearMonth() != null) {
            int value = history.getScheduledPaymentYearMonth();
            return YearMonth.of(value / 100, value % 100);
        }
        if (history.getStatus() == SettlementStatus.PAYMENT_CARRYOVER) {
            return null;
        }
        return YearMonth.from(history.getActivityMonth()).plusMonths(2);
    }
}
