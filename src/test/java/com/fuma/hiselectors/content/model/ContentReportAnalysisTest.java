package com.fuma.hiselectors.content.model;

import static org.assertj.core.api.Assertions.assertThat;

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
}
