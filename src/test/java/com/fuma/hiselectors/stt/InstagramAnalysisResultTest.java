package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 파이썬 워커 /reel 실제 응답이 InstagramAnalysisResult 로 그대로 매핑되는지(통합 계약) 검증. */
class InstagramAnalysisResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // stt-worker pipeline.run() 실제 응답 형태(축약).
    private static final String PYTHON_RESPONSE = """
            {
              "source": "video",
              "stt": "홈스타 핑크 파워 스프레이 세정제",
              "ocr": "욕실찌든때클리너\\n신고번호 FB24-01-0019",
              "analysis": {
                "keywords": ["세정제", "홈스타핑크파워"],
                "category": {"label": "BEAUTY", "score": 0.45, "uncertain": false},
                "hate": {"labels": [], "scores": {}, "suspected": false}
              }
            }""";

    @Test
    void 파이썬_reel_응답이_DTO로_매핑된다() {
        InstagramAnalysisResult r = mapper.readValue(PYTHON_RESPONSE, InstagramAnalysisResult.class);

        assertThat(r.source()).isEqualTo("video");
        assertThat(r.stt()).contains("핑크 파워");
        assertThat(r.ocr()).contains("FB24-01-0019");
        assertThat(r.analysis().keywords()).containsExactly("세정제", "홈스타핑크파워");
        assertThat(r.analysis().category().label()).isEqualTo("BEAUTY");
        assertThat(r.analysis().category().score()).isEqualTo(0.45);
        assertThat(r.analysis().category().uncertain()).isFalse();
        assertThat(r.analysis().hate().suspected()).isFalse();
        assertThat(r.analysis().hate().scores()).isEmpty();
    }
}
