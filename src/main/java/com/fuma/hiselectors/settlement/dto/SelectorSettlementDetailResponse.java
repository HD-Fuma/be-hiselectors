package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.springframework.data.domain.Page;

public record SelectorSettlementDetailResponse(
        SelectorProfile profile,
        SettlementSummary settlementSummary,
        Page<SettlementEstimateResponse> histories
) {

    public static SelectorSettlementDetailResponse of(
            Selectors selectors,
            SelectorsSnsAccount snsAccount,
            SettlementSummary settlementSummary,
            Page<SettlementEstimateResponse> histories) {
        return new SelectorSettlementDetailResponse(
                SelectorProfile.of(selectors, snsAccount), settlementSummary, histories);
    }

    public record SelectorProfile(
            Long selectorsId,
            String selectorsCode,
            String selectorsNickname,
            SnsPlatform snsCode,
            String accountId,
            Long followerCount,
            String profileImageUrl,
            LocalDateTime lastCollectedAt
    ) {

        private static SelectorProfile of(Selectors selectors, SelectorsSnsAccount snsAccount) {
            if (snsAccount == null) {
                return new SelectorProfile(
                        selectors.getId(), selectors.getSelectorsCode(),
                        selectors.getSelectorsNickname(), null, null, null, null, null);
            }
            return new SelectorProfile(
                    selectors.getId(), selectors.getSelectorsCode(), selectors.getSelectorsNickname(),
                    snsAccount.getSnsCode(), snsAccount.getAccountId(), snsAccount.getFollowerCount(),
                    snsAccount.getProfileImageUrl(), snsAccount.getLastCollectedAt());
        }
    }

    public record SettlementSummary(
            Long cumulativePurchaseConversionCount,
            Long cumulativePaidCommission,
            Long currentMonthPurchaseConversionCount,
            YearMonth currentMonth,
            Long nextMonthScheduledCommission,
            YearMonth nextPaymentMonth,
            SettlementStatus nextPaymentSettlementStatus
    ) {
    }
}
