package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(@NotNull ApplicationStatus status) {
}
