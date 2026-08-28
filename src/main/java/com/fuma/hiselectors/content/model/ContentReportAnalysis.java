package com.fuma.hiselectors.content.model;

import java.util.List;

/** 콘텐츠 검수 리포트의 버전화된 JSON 계약이다. */
public record ContentReportAnalysis(
        Overview overview,
        Insight insight
) {

    public ContentReportAnalysis {
        overview = overview == null ? Overview.empty() : overview;
        insight = insight == null ? Insight.empty() : insight;
    }

    public static ContentReportAnalysis empty() {
        return new ContentReportAnalysis(Overview.empty(), Insight.empty());
    }

    public static ContentReportAnalysis fromLegacy(ContentReportData data) {
        if (data == null) {
            return empty();
        }
        return new ContentReportAnalysis(
                new Overview(data.summary(), data.purpose(), data.flow(),
                        data.overallAssessment()),
                Insight.empty());
    }

    public record Overview(
            String summary,
            String purpose,
            String flow,
            String overallAssessment
    ) {

        public Overview {
            summary = valueOrEmpty(summary);
            purpose = valueOrEmpty(purpose);
            flow = valueOrEmpty(flow);
            overallAssessment = valueOrEmpty(overallAssessment);
        }

        public static Overview empty() {
            return new Overview("", "", "", "");
        }
    }

    public record Insight(
            String contentStyle,
            String tone,
            List<String> strengths,
            List<String> cautions,
            List<String> risks,
            boolean hateConfirmed,
            List<String> collabBrands
    ) {

        public Insight {
            contentStyle = valueOrEmpty(contentStyle);
            tone = valueOrEmpty(tone);
            strengths = immutable(strengths);
            cautions = immutable(cautions);
            risks = immutable(risks);
            collabBrands = immutable(collabBrands);
        }

        public static Insight empty() {
            return new Insight("", "", List.of(), List.of(), List.of(), false, List.of());
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
