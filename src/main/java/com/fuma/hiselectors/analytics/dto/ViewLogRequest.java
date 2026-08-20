package com.fuma.hiselectors.analytics.dto;

import com.fuma.hiselectors.analytics.model.ViewPageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ViewLogRequest(
        @NotBlank String selectorsCode,
        @NotNull ViewPageType pageType,
        Long referenceId
) {
}
