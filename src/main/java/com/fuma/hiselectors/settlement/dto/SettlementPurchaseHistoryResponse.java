package com.fuma.hiselectors.settlement.dto;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementPurchaseHistoryResponse(
        Long purchaseHistoryId,
        Long selectorsId,
        String selectorsCode,
        String selectorsNickname,
        Long userId,
        String userHiId,
        String orderNo,
        String productCode,
        Integer quantity,
        BigDecimal paidAmount,
        LocalDateTime purchasedAt,
        LocalDateTime confirmedAt,
        PurchaseStatus status) {
}
