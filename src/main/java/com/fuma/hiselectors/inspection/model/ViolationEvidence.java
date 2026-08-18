package com.fuma.hiselectors.inspection.model;

import java.util.List;

public record ViolationEvidence(
        String reason,
        Double confidence,
        List<EvidenceLocation> locations
) {
    public ViolationEvidence {
        locations = locations == null ? List.of() : List.copyOf(locations);
    }
}
