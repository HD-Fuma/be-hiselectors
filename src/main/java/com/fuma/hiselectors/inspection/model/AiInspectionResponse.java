package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.content.model.ContentReportData;
import java.util.List;
import java.util.Map;

/**
 * AI가 반환한 콘텐츠 리포트와 위반 후보를 매핑한 응답이다.
 * 저장 시에는 report와 violations를 각각 별도의 흐름으로 전달한다.
 */
public record AiInspectionResponse(
        ContentReportAnalysis report,
        List<DetectedViolation> violations,
        Map<String, Object> executionMetadata
) {
    public AiInspectionResponse {
        report = report == null ? ContentReportAnalysis.empty() : report;
        violations = violations == null ? List.of() : List.copyOf(violations);
        executionMetadata = executionMetadata == null
                ? Map.of() : Map.copyOf(executionMetadata);
    }

    public AiInspectionResponse(ContentReportAnalysis report, List<DetectedViolation> violations) {
        this(report, violations, Map.of());
    }

    public AiInspectionResponse(ContentReportData report, List<DetectedViolation> violations) {
        this(ContentReportAnalysis.fromLegacy(report), violations, Map.of());
    }
}
