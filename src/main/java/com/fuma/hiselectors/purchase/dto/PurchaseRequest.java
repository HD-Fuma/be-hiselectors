package com.fuma.hiselectors.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseRequest(
        @NotNull @Positive Long buyerUserId,
        String selectorsCode,
        @jakarta.validation.constraints.NotBlank String productCode,
        @NotNull @Min(1) Integer quantity
) {
}
