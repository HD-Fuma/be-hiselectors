package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementType;

public record SettlementAccountResponse(
        String bankName,
        String accountNumber,
        String accountHolder,
        SettlementType settlementType,
        String businessNumber
) {
    private static final String MASKED_INDIVIDUAL_NUMBER = "******-*******";

    public static SettlementAccountResponse of(
            SettlementAccount account, String accountNumber, String businessNumber) {
        SettlementType settlementType = SettlementType.fromStorage(account.getSettlementType())
                .orElse(null);
        return new SettlementAccountResponse(
                account.getBankName(),
                accountNumber,
                account.getAccountHolder(),
                settlementType,
                responseBusinessNumber(businessNumber, settlementType));
    }

    private static String responseBusinessNumber(
            String businessNumber, SettlementType settlementType) {
        if (settlementType == null || businessNumber == null || businessNumber.isBlank()) {
            return null;
        }
        return settlementType == SettlementType.INDIVIDUAL
                ? MASKED_INDIVIDUAL_NUMBER
                : businessNumber;
    }
}
