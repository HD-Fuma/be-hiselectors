package com.fuma.hiselectors.purchase.dto;

import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseResponse(
        Long purchaseHistoryId,
        String orderNo,
        PurchaseStatus status,
        Integer quantity,
        BigDecimal regularUnitPrice,
        BigDecimal saleUnitPrice,
        BigDecimal discountAmount,
        BigDecimal paidAmount,
        LocalDateTime purchasedAt,
        PurchaseProcessingResult processingResult
) {

    public static PurchaseResponse of(
            PurchaseHistory purchaseHistory, PurchaseProcessingResult processingResult) {
        return new PurchaseResponse(
                purchaseHistory.getId(),
                purchaseHistory.getOrderNo(),
                purchaseHistory.getStatus(),
                purchaseHistory.getQuantity(),
                purchaseHistory.getRegularUnitPrice(),
                purchaseHistory.getSaleUnitPrice(),
                purchaseHistory.getDiscountAmount(),
                purchaseHistory.getPaidAmount(),
                purchaseHistory.getPurchasedAt(),
                processingResult
        );
    }
}
