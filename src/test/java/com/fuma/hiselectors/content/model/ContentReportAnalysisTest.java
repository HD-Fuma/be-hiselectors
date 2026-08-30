package com.fuma.hiselectors.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentReportAnalysisTest {

    @Test
    void convertsLegacyColumnsWithoutLosingOverview() {
        ContentReportData legacy = new ContentReportData(
                "summary", "purpose", "flow", "assessment");

        ContentReportAnalysis analysis = ContentReportAnalysis.fromLegacy(legacy);

        assertThat(analysis.overview()).isEqualTo(new ContentReportAnalysis.Overview(
                "summary", "purpose", "flow", "assessment"));
        assertThat(analysis.insight()).isEqualTo(ContentReportAnalysis.Insight.empty());
    }

    @Test
    void normalizesNullableInsightCollections() {
        ContentReportAnalysis.Insight insight = new ContentReportAnalysis.Insight(
                null, null, null, List.of("caution"), null, false, null);

        assertThat(insight.contentStyle()).isEmpty();
        assertThat(insight.strengths()).isEmpty();
        assertThat(insight.cautions()).containsExactly("caution");
        assertThat(insight.risks()).isEmpty();
        assertThat(insight.collabBrands()).isEmpty();
    }

    @Test
    void blankReportHasEmptySummaryAndStyle() {
        assertThat(ContentReportAnalysis.empty().hasNoContent()).isTrue();
        assertThat(new ContentReportAnalysis(
                new ContentReportAnalysis.Overview("요약", "", "", ""),
                ContentReportAnalysis.Insight.empty()).hasNoContent()).isFalse();
    }

    @Test
    void jackson2IgnoresPersistedBlankProperty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {"overview":{"summary":"요약","purpose":"","flow":"","overallAssessment":""},\
                "insight":{"contentStyle":"","tone":"","strengths":[],"cautions":[],\
                "risks":[],"hateConfirmed":false,"collabBrands":[]},"blank":false}
                """;

        ContentReportAnalysis analysis = mapper.readValue(json, ContentReportAnalysis.class);

        assertThat(analysis.overview().summary()).isEqualTo("요약");
        assertThat(analysis.hasNoContent()).isFalse();
        assertThat(mapper.writeValueAsString(analysis)).doesNotContain("\"blank\"");
    }

    @Test
    void clipsOverviewFieldsToSharedLimits() {
        String purpose = "가".repeat(ContentReportTextLimits.PURPOSE + 20);
        ContentReportAnalysis.Overview overview = new ContentReportAnalysis.Overview(
                "요약", purpose, "전개", "평가");

        assertThat(overview.purpose()).hasSize(ContentReportTextLimits.PURPOSE);
    }
}
