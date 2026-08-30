package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import java.util.LinkedHashMap;
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
        analysis = analysis == null ? ContentReportAnalysis.empty() : analysis;
        executionMetadata = copyWithoutNulls(executionMetadata);
    }

    private static Map<String, Object> copyWithoutNulls(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                copied.put(key, value);
            }
        });
        return Map.copyOf(copied);
    }
}
