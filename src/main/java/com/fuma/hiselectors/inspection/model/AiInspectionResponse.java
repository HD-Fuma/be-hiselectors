package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.content.model.ContentReportData;
import java.util.List;

/**
 * AI가 반환한 콘텐츠 리포트와 위반 후보를 매핑한 응답이다.
 * 저장 시에는 report와 violations를 각각 별도의 흐름으로 전달한다.
 */
public record AiInspectionResponse(
        ContentReportData report,
        List<DetectedViolation> violations
) {
    public AiInspectionResponse {
        report = report == null ? ContentReportData.empty() : report;
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
