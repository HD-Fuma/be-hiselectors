package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.ApplicationContentAnalysis;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CreatorEvaluationTest {

    @Test
    void 엔티티_왕복시_transcript와_신호가_보존된다() {
        InstagramAnalysisResult original = new InstagramAnalysisResult(
                "video", "음성 전사", "화면 자막",
                new InstagramAnalysisResult.Analysis(
                        List.of("세정제", "청소"),
                        new InstagramAnalysisResult.Category("LIVING_LIFE", 0.4, false),
                        new InstagramAnalysisResult.Hate(List.of(), Map.of(), false)));

        ApplicationContentAnalysis entity = ApplicationContentAnalysis.from(1L, "DbXos7-kgtj", original);
        InstagramAnalysisResult back = entity.toResult();

        assertThat(entity.getContentKey()).isEqualTo("DbXos7-kgtj");
        assertThat(back.stt()).isEqualTo("음성 전사");
        assertThat(back.ocr()).isEqualTo("화면 자막");
        assertThat(back.analysis().keywords()).containsExactly("세정제", "청소");
        assertThat(back.analysis().category().label()).isEqualTo("LIVING_LIFE");
        assertThat(back.analysis().hate().suspected()).isFalse();
    }

    @Test
    void Gemini_취합_JSON이_ApplicantInsight로_매핑된다() {
        String json = """
                {
                  "summary": "생활용품 청소 꿀팁과 맛집 리뷰를 친근하게 소개하는 크리에이터입니다.",
                  "contentStyle": "리뷰언박싱",
                  "tone": "친근수다",
                  "strengths": ["실사용 리뷰", "정보 전달력"],
                  "cautions": ["과장 표현 주의"],
                  "risks": [],
                  "hateConfirmed": false,
                  "collabBrands": ["홈스타"]
                }""";

        ApplicantInsight i = new ObjectMapper().readValue(json, ApplicantInsight.class);

        assertThat(i.summary()).contains("청소");
        assertThat(i.contentStyle()).isEqualTo("리뷰언박싱");
        assertThat(i.tone()).isEqualTo("친근수다");
        assertThat(i.strengths()).containsExactly("실사용 리뷰", "정보 전달력");
        assertThat(i.hateConfirmed()).isFalse();
        assertThat(i.collabBrands()).containsExactly("홈스타");
    }
}
