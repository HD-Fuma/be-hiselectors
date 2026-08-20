package com.fuma.hiselectors.productgroup.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductGroupItemAddRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> productIds
) {
}
