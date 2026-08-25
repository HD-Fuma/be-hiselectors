package com.fuma.hiselectors.content.model;

/**
 * 콘텐츠 자체에 대한 분석 리포트다. 위반 내역은 별도의 검사 결과 흐름에서 관리한다.
 */
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
