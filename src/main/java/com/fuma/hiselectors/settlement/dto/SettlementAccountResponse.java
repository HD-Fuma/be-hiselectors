package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.settlement.model.SettlementAccount;

public record SettlementAccountResponse(
        String bankName,
        String accountNumber,
        String accountHolder
) {
    public static SettlementAccountResponse of(SettlementAccount account) {
        return new SettlementAccountResponse(
                account.getBankName(), account.getAccountNumber(), account.getAccountHolder());
    }
}
