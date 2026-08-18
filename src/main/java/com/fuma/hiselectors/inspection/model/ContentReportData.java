package com.fuma.hiselectors.inspection.model;

public record ContentReportData(
        String summary,
        String purpose,
        String flow,
        String overallAssessment
) {
    public static ContentReportData empty() {
        return new ContentReportData("", "", "", "");
    }
}
