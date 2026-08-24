package com.fuma.hiselectors.stt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

    @Test
    void 리포트_파이프라인은_비용별_기본_모델을_사용한다() {
        GeminiProperties properties = new GeminiProperties(
                "key", null, null, null, null, null);

        assertEquals("gemini-3.5-flash-lite", properties.youtubeModelOrDefault());
        assertEquals("gemini-3.5-flash-lite", properties.reportModelOrDefault());
    }
}
