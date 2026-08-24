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

    public static SettlementAccountResponse of(SettlementAccount account) {
        SettlementType settlementType = SettlementType.fromStorage(account.getSettlementType())
                .orElse(null);
        return new SettlementAccountResponse(
                account.getBankName(),
                account.getAccountNumber(),
                account.getAccountHolder(),
                settlementType,
                responseBusinessNumber(account, settlementType));
    }

    private static String responseBusinessNumber(
            SettlementAccount account, SettlementType settlementType) {
        if (settlementType == null || account.getBusinessNumber() == null
                || account.getBusinessNumber().isBlank()) {
            return null;
        }
        return settlementType == SettlementType.INDIVIDUAL
                ? MASKED_INDIVIDUAL_NUMBER
                : account.getBusinessNumber();
    }
}
