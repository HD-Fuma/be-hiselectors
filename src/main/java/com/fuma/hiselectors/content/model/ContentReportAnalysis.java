package com.fuma.hiselectors.content.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 콘텐츠 검수 리포트의 버전화된 JSON 계약이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnore
    public boolean hasNoContent() {
        return overview.summary().isBlank() && insight.contentStyle().isBlank();
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
            summary = ContentReportTextLimits.clip(summary, ContentReportTextLimits.SUMMARY);
            purpose = ContentReportTextLimits.clip(purpose, ContentReportTextLimits.PURPOSE);
            flow = ContentReportTextLimits.clip(flow, ContentReportTextLimits.FLOW);
            overallAssessment = ContentReportTextLimits.clip(
                    overallAssessment, ContentReportTextLimits.OVERALL_ASSESSMENT);
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
            contentStyle = ContentReportTextLimits.clip(
                    contentStyle, ContentReportTextLimits.CONTENT_STYLE);
            tone = ContentReportTextLimits.clip(tone, ContentReportTextLimits.TONE);
            strengths = ContentReportTextLimits.clipItems(
                    strengths, ContentReportTextLimits.INSIGHT_ITEM);
            cautions = ContentReportTextLimits.clipItems(
                    cautions, ContentReportTextLimits.INSIGHT_ITEM);
            risks = ContentReportTextLimits.clipItems(
                    risks, ContentReportTextLimits.INSIGHT_ITEM);
            collabBrands = ContentReportTextLimits.clipItems(
                    collabBrands, ContentReportTextLimits.INSIGHT_ITEM);
        }

        public static Insight empty() {
            return new Insight("", "", List.of(), List.of(), List.of(), false, List.of());
        }
    }

}
