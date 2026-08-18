package com.fuma.hiselectors.inspection.model;

import java.util.Objects;

public record DetectedViolation(
        ViolationTypeCode type,
        ViolationEvidence evidence
) {
    public DetectedViolation {
        Objects.requireNonNull(type);
        Objects.requireNonNull(evidence);
    }
}
