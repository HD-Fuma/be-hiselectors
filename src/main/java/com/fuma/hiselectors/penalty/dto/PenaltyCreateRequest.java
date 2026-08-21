package com.fuma.hiselectors.penalty.dto;

import jakarta.validation.constraints.NotNull;

public record PenaltyCreateRequest(@NotNull Long violationTypeId) {
}
