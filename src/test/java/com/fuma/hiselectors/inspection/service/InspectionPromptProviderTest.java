package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InspectionPromptProviderTest {

    private final InspectionPromptProvider provider = new InspectionPromptProvider();

    @Test
    void aiPrompt는_8개_위반_유형_정의와_판정_규칙을_포함한다() {
        String prompt = provider.aiPrompt();

        assertThat(prompt).contains(
                "ABUSIVE_LANGUAGE",
                "HATE_DISCRIMINATION",
                "VIOLENCE_THREAT",
                "SEXUAL_CONTENT",
                "POLITICAL_CONTENT",
                "SOCIAL_CONTROVERSY",
                "FALSE_EXAGGERATED_CLAIM",
                "BRAND_REPUTATION_DAMAGE");
        assertThat(prompt).contains("violations는 빈 배열");
        assertThat(prompt).contains("가장 구체적인 유형");
        assertThat(prompt).contains("violations만 반환하세요");
        assertThat(prompt).doesNotContain("report.overview");
        assertThat(InspectionPromptProvider.AI_PROMPT_VERSION)
                .isEqualTo("content-inspection-v8");
        assertThat(prompt.formatted("검수입력")).contains("검수입력");
    }

    @Test
    void contentReportPrompt는_위반_없이_상세_분석만_요청한다() {
        String prompt = provider.contentReportPrompt();

        assertThat(prompt).contains("콘텐츠 상세 분석가");
        assertThat(prompt).contains("report.overview");
        assertThat(prompt).doesNotContain("violations");
        assertThat(prompt.formatted("분석입력")).contains("분석입력");
        assertThat(InspectionPromptProvider.CONTENT_REPORT_PROMPT_VERSION)
                .isEqualTo("content-report-v1");
    }

    @Test
    void youtubeExtractionPrompt는_검수용_세그먼트_지시를_쓴다() {
        String prompt = provider.youtubeExtractionPrompt();

        assertThat(prompt).contains("콘텐츠 검수에 사용할 근거");
        assertThat(prompt).contains("stt.segments");
        assertThat(prompt).contains("ocr.segments");
        assertThat(prompt).contains("한 segment로 합칩니다");
        assertThat(prompt).contains("단어·음절 단위로 쪼개지 마세요");
        assertThat(prompt).contains("3. report");
        assertThat(prompt).contains("durationMs=%s");
        assertThat(prompt).doesNotContain("===음성===");
        assertThat(prompt).doesNotContain("===자막===");
        assertThat(InspectionPromptProvider.YOUTUBE_EXTRACTION_PROMPT_VERSION)
                .isEqualTo("youtube-extraction-v6");
        assertThat(provider.youtubeExtractionPrompt(618_000L)).contains("durationMs=618000");
    }

    @Test
    void youtubeSttPrompt는_리포트용_압축_추출_지시를_따로_쓴다() {
        String prompt = provider.youtubeSttPrompt();

        assertThat(prompt).contains("크리에이터 리포트");
        assertThat(prompt).contains("===음성===");
        assertThat(prompt).contains("===자막===");
        assertThat(prompt).doesNotContain("stt.segments");
        assertThat(prompt).doesNotContain("ocr.segments");
        assertThat(prompt).doesNotContain("segmentId");
        assertThat(prompt).isNotEqualTo(provider.youtubeExtractionPrompt());
    }
}
