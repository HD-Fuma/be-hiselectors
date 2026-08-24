package com.fuma.hiselectors.inspection.model;

import java.util.List;

public record ViolationEvidence(
        String reason,
        Double confidence,
        List<EvidenceLocation> locations,
        EvidenceSource source
) {
    public ViolationEvidence {
        locations = locations == null ? List.of() : List.copyOf(locations);
        if (source == null) {
            throw new IllegalArgumentException("evidence source는 필수입니다.");
        }
    }
}
