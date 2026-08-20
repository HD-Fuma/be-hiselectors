package com.fuma.hiselectors.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthenticatedPurchaseRequest(
        String selectorsCode,
        @NotBlank String productCode,
        @NotNull @Min(1) Integer quantity
) {
}
