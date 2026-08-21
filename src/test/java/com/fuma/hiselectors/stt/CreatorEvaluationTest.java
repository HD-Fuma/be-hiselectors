package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 캐시 누적/파기 로직과 Gemini 평가 JSON 매핑 검증(네트워크 없음). */
class CreatorEvaluationTest {

    private InstagramAnalysisResult content(String stt) {
        return new InstagramAnalysisResult("video", stt, "", null);
    }

    @Test
    void 지원자별로_콘텐츠가_누적되고_clear로_파기된다() {
        TranscriptCache cache = new TranscriptCache();

        cache.add(1L, content("첫 콘텐츠"));
        cache.add(1L, content("둘째 콘텐츠"));
        cache.add(2L, content("다른 지원자"));

        assertThat(cache.get(1L)).hasSize(2);
        assertThat(cache.get(2L)).hasSize(1);
        assertThat(cache.get(99L)).isEmpty();

        cache.clear(1L);
        assertThat(cache.get(1L)).isEmpty();
        assertThat(cache.get(2L)).hasSize(1);
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
