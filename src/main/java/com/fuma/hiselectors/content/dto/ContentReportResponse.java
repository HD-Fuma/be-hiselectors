package com.fuma.hiselectors.content.dto;

public record ContentReportResponse(
        Long contentReportId,
        String summary,
        String purpose,
        String flow,
        String overallAssessment
) {
}
