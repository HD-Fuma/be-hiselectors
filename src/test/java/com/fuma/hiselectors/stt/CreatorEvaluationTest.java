package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CreatorEvaluationTest {

    private CreatorEvaluationService service() {
        GeminiProperties props = new GeminiProperties("dummy", null, null, null);
        return new CreatorEvaluationService(new GeminiEvalClient(props));
    }

    @Test
    void 콘텐츠가_없으면_Gemini_호출_전에_거부한다() {
        assertThatThrownBy(() -> service().evaluate(List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 전사_자막이_전부_비면_거부한다() {
        List<InstagramAnalysisResult> blank =
                List.of(new InstagramAnalysisResult("thumbnail", "", "  ", null));

        assertThatThrownBy(() -> service().evaluate(blank))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void Gemini_평가_JSON이_DTO로_매핑된다() {
        String json = """
                {
                  "category": "LIVING_LIFE",
                  "keywords": ["세정제", "홈스타", "청소"],
                  "summary": "생활용품 청소 리뷰 크리에이터",
                  "tone": "친근함"
                }""";

        ApplicantEvaluation e = new ObjectMapper().readValue(json, ApplicantEvaluation.class);

        assertThat(e.category()).isEqualTo("LIVING_LIFE");
        assertThat(e.keywords()).containsExactly("세정제", "홈스타", "청소");
        assertThat(e.summary()).contains("청소");
        assertThat(e.tone()).isEqualTo("친근함");
    }
}
