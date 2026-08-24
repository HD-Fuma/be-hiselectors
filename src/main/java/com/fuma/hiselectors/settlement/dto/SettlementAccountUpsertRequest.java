package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.settlement.model.SettlementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettlementAccountUpsertRequest(
        @NotBlank @Size(max = 20) String bankName,
        @NotBlank @Size(max = 50) String accountNumber,
        @NotBlank @Size(max = 50) String accountHolder,
        SettlementType settlementType,
        @Size(max = 50) String businessNumber
) {
}
