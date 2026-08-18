package com.fuma.hiselectors.inspection.model;

import java.util.List;

public record AiInspectionResult(
        ContentReportData report,
        List<DetectedViolation> violations
) {
    public AiInspectionResult {
        report = report == null ? ContentReportData.empty() : report;
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
