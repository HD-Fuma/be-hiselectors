package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import java.util.Map;

public record ContentReportResponse(
        Long contentReportId,
        String summary,
        String purpose,
        String flow,
        String overallAssessment,
        Long inspectionPolicyId,
        String reportSchemaVersion,
        ContentReportAnalysis analysis,
        Map<String, Object> executionMetadata
) {
    public ContentReportResponse {
        executionMetadata = executionMetadata == null
                ? Map.of() : Map.copyOf(executionMetadata);
    }
}
