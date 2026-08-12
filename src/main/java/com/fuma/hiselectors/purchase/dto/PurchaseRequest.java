package com.fuma.hiselectors.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseRequest(
        @NotBlank String orderNo,
        @NotNull @Positive Long buyerUserId,
        String selectorCode,
        @NotBlank String productCode,
        @NotNull @Min(1) Integer quantity
) {
}
